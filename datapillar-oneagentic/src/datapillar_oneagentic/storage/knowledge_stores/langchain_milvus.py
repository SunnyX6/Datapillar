# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-29
"""LangChain Milvus KnowledgeStore implementation."""

from __future__ import annotations

import json
import logging
from typing import Any

from langchain_core.documents import Document
from langchain_milvus import BaseMilvusBuiltInFunction, BM25BuiltInFunction, Milvus

from datapillar_oneagentic.knowledge.models import (
    KnowledgeChunk,
    KnowledgeDocument,
    KnowledgeSearchHit,
    KnowledgeSource,
    SourceSpan,
)
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage.config import VectorStoreConfig
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore

logger = logging.getLogger(__name__)

_METADATA_FIELD = "metadata"
_RECORD_TYPE_KEY = "record_type"
_RECORD_TYPE_CHUNK = "chunk"
_RECORD_TYPE_DOC = "doc"
_RECORD_TYPE_SOURCE = "source"
_BM25_CONFIG_KEY = "bm25"
_DEFAULT_DENSE_VECTOR_FIELD = "vector"
_DEFAULT_SPARSE_VECTOR_FIELD = "sparse"
_DEFAULT_TEXT_FIELD = "text"


class LangChainMilvusKnowledgeStore(KnowledgeStore):
    """Knowledge store backed by langchain-milvus (single collection)."""

    def __init__(
        self,
        *,
        namespace: str,
        vector_store_config: VectorStoreConfig,
        embedding_config: EmbeddingConfig,
    ) -> None:
        self._namespace = namespace
        self._config = vector_store_config
        self._embedding_provider = EmbeddingProvider(embedding_config)
        self._bm25_enabled = False
        self._sparse_vector_field: str | None = None
        self._dense_vector_field: str | None = None
        self._dense_metric_type: str | None = None
        self._sparse_metric_type: str | None = None
        self._store = self._build_store()

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    def supports_hybrid(self) -> bool:
        try:
            vector_fields = getattr(self._store, "vector_fields", None)
            if isinstance(vector_fields, list):
                return len(vector_fields) > 1
        except Exception:
            return False
        return False

    @property
    def supports_full_text(self) -> bool:
        return self._bm25_enabled

    @property
    def supports_external_embeddings(self) -> bool:
        # langchain-milvus embeds internally on write.
        return False

    async def initialize(self) -> None:
        # Collection is created lazily on first insert.
        return None

    async def close(self) -> None:
        # No explicit close needed for sync client.
        return None

    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        if not sources:
            return
        ids = [source.source_id for source in sources if source.source_id]
        if ids:
            self._delete_by_metadata_ids(_RECORD_TYPE_SOURCE, "source_id", ids)
        docs = [
            Document(
                page_content=source.name or source.source_uri or source.source_id or "",
                metadata=_source_to_metadata(source),
            )
            for source in sources
        ]
        self._store.add_documents(documents=docs, ids=ids)

    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        if not docs:
            return
        ids = [doc.doc_id for doc in docs if doc.doc_id]
        if ids:
            self._delete_by_metadata_ids(_RECORD_TYPE_DOC, "doc_id", ids)
        documents = [
            Document(
                page_content=doc.title or doc.doc_id,
                metadata=_doc_to_metadata(doc),
            )
            for doc in docs
        ]
        self._store.add_documents(documents=documents, ids=ids)

    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        if not chunks:
            return
        ids = [chunk.chunk_id for chunk in chunks if chunk.chunk_id]
        if ids:
            self._delete_by_metadata_ids(_RECORD_TYPE_CHUNK, "chunk_id", ids)
        documents = [
            Document(
                page_content=chunk.content,
                metadata=_chunk_to_metadata(chunk),
            )
            for chunk in chunks
        ]
        self._store.add_documents(documents=documents, ids=ids)

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        expr = _build_expr(filters, record_type=_RECORD_TYPE_CHUNK)
        if self._bm25_enabled:
            return self._search_by_vector(
                field_name=self._dense_vector_field,
                query_vector=query_vector,
                k=k,
                expr=expr,
            )
        results = self._store.similarity_search_with_score_by_vector(
            embedding=query_vector,
            k=max(1, k),
            expr=expr,
        )
        return _convert_search_results(self._store, results)

    async def hybrid_search_chunks(
        self,
        *,
        query_vector: list[float],
        query_text: str,
        k: int,
        filters: dict[str, Any] | None = None,
        rrf_k: int = 60,
    ) -> list[KnowledgeSearchHit]:
        expr = _build_expr(filters, record_type=_RECORD_TYPE_CHUNK)
        results = self._store.similarity_search_with_score(
            query=query_text,
            k=max(1, k),
            expr=expr,
        )
        if self._bm25_enabled:
            return _convert_search_results_raw(results, score_kind="similarity")
        return _convert_search_results(self._store, results)

    async def full_text_search_chunks(
        self,
        *,
        query_text: str,
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        if not self._bm25_enabled:
            raise ValueError("Full-text retrieval is not supported by the current backend.")
        expr = _build_expr(filters, record_type=_RECORD_TYPE_CHUNK)
        return self._search_by_text(
            field_name=self._sparse_vector_field,
            query_text=query_text,
            k=k,
            expr=expr,
        )

    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        rows = self._query_by_metadata(_RECORD_TYPE_DOC, "doc_id", [doc_id], limit=1)
        if not rows:
            return None
        doc = _parse_document(self._store, rows[0])
        return _metadata_to_doc(doc.metadata)

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        if not chunk_ids:
            return []
        rows = self._query_by_metadata(_RECORD_TYPE_CHUNK, "chunk_id", chunk_ids)
        docs = [_parse_document(self._store, row) for row in rows]
        return [_document_to_chunk(doc) for doc in docs]

    async def delete_chunks(self, chunk_ids: list[str]) -> int:
        if not chunk_ids:
            return 0
        return self._delete_by_metadata_ids(_RECORD_TYPE_CHUNK, "chunk_id", chunk_ids)

    async def query_chunks(
        self,
        *,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[KnowledgeChunk]:
        expr = _build_expr(filters, record_type=_RECORD_TYPE_CHUNK)
        rows = self._query(expr=expr, limit=limit)
        docs = [_parse_document(self._store, row) for row in rows]
        return [_document_to_chunk(doc) for doc in docs]

    async def delete_doc(self, doc_id: str) -> int:
        return self._delete_by_metadata_ids(_RECORD_TYPE_DOC, "doc_id", [doc_id])

    async def delete_doc_chunks(self, doc_id: str) -> int:
        rows = self._query_by_metadata(_RECORD_TYPE_CHUNK, "doc_id", [doc_id])
        if not rows:
            return 0
        docs = [_parse_document(self._store, row) for row in rows]
        chunk_ids = [doc.metadata.get("chunk_id") for doc in docs if doc.metadata.get("chunk_id")]
        return self._delete_by_metadata_ids(_RECORD_TYPE_CHUNK, "chunk_id", chunk_ids)

    def _build_store(self) -> Milvus:
        params = _extract_store_params(self._config)
        bm25_config = _resolve_bm25_config(self._config)
        bm25_enabled = bool(bm25_config.get("enabled", False))
        self._bm25_enabled = bm25_enabled
        dense_index_params = params.pop("index_params", None)
        dense_search_params = params.pop("search_params", None)
        sparse_index_params = params.pop("sparse_index_params", None)
        sparse_search_params = params.pop("sparse_search_params", None)
        params.setdefault("collection_name", f"{self._namespace}_knowledge")
        params.setdefault("auto_id", False)
        params.setdefault("metadata_field", _METADATA_FIELD)
        if dense_index_params is None:
            dense_index_params = {
                "metric_type": "COSINE",
                "index_type": "FLAT",
                "params": {},
            }
        if "connection_args" not in params:
            connection_args: dict[str, Any] = {}
            if self._config.uri:
                connection_args["uri"] = self._config.uri
            if self._config.token:
                connection_args["token"] = self._config.token
            if connection_args:
                params["connection_args"] = connection_args

        if "embedding_function" not in params:
            params["embedding_function"] = self._embedding_provider.get_embeddings()

        if bm25_enabled:
            dense_field = _resolve_dense_vector_field(params, bm25_config)
            sparse_field = _resolve_sparse_vector_field(bm25_config)
            text_field = _resolve_text_field(params, bm25_config)
            self._dense_vector_field = dense_field
            self._sparse_vector_field = sparse_field
            self._dense_metric_type = _resolve_metric_type(dense_index_params, "COSINE")
            self._sparse_metric_type = _resolve_metric_type(sparse_index_params, "BM25")
            params["text_field"] = text_field
            params["vector_field"] = [dense_field, sparse_field]
            params["builtin_function"] = _build_bm25_function(
                bm25_config=bm25_config,
                input_field=text_field,
                output_field=sparse_field,
            )
            if isinstance(dense_index_params, list):
                params["index_params"] = dense_index_params
            else:
                params["index_params"] = [
                    dense_index_params,
                    sparse_index_params or _default_sparse_index_params(),
                ]
            if isinstance(dense_search_params, list):
                params["search_params"] = dense_search_params
            elif dense_search_params is not None or sparse_search_params is not None:
                params["search_params"] = [
                    dense_search_params or _default_search_params(self._dense_metric_type or "COSINE"),
                    sparse_search_params or _default_search_params(self._sparse_metric_type or "BM25"),
                ]
        else:
            self._dense_vector_field = _resolve_dense_vector_field(params, bm25_config)
            self._sparse_vector_field = None
            self._dense_metric_type = _resolve_metric_type(dense_index_params, "COSINE")
            self._sparse_metric_type = None
            params["index_params"] = dense_index_params
            if dense_search_params is not None:
                params["search_params"] = dense_search_params

        return Milvus(**params)

    def _search_by_vector(
        self,
        *,
        field_name: str | None,
        query_vector: list[float],
        k: int,
        expr: str,
    ) -> list[KnowledgeSearchHit]:
        if not field_name:
            raise ValueError("Dense vector field is not configured for Milvus search.")
        results, score_kind = _milvus_search_by_field(
            store=self._store,
            field_name=field_name,
            data=query_vector,
            k=k,
            expr=expr,
            default_metric=self._dense_metric_type or "COSINE",
        )
        return _convert_search_results_raw(results, score_kind=score_kind)

    def _search_by_text(
        self,
        *,
        field_name: str | None,
        query_text: str,
        k: int,
        expr: str,
    ) -> list[KnowledgeSearchHit]:
        if not field_name:
            raise ValueError("Sparse vector field is not configured for Milvus search.")
        results, score_kind = _milvus_search_by_field(
            store=self._store,
            field_name=field_name,
            data=query_text,
            k=k,
            expr=expr,
            default_metric=self._sparse_metric_type or "BM25",
        )
        return _convert_search_results_raw(results, score_kind=score_kind)

    def _query_by_metadata(
        self,
        record_type: str,
        field: str,
        values: list[str],
        *,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        expr = _build_expr({field: values}, record_type=record_type)
        return self._query(expr=expr, limit=limit)

    def _query(self, *, expr: str, limit: int | None = None) -> list[dict[str, Any]]:
        if not self._store.client.has_collection(self._store.collection_name):
            return []
        return list(
            self._store.client.query(
                self._store.collection_name,
                filter=expr,
                limit=limit,
                output_fields=["*"],
            )
            or []
        )

    def _delete_by_metadata_ids(self, record_type: str, field: str, values: list[str]) -> int:
        if not values:
            return 0
        expr = _build_expr({field: values}, record_type=record_type)
        return self._delete_by_expr(expr, expected=len(values))

    def _delete_by_expr(self, expr: str, *, expected: int = 0) -> int:
        if not self._store.client.has_collection(self._store.collection_name):
            return 0
        ok = self._store.delete(expr=expr)
        return expected if ok else 0


def _extract_store_params(config: VectorStoreConfig) -> dict[str, Any]:
    params = dict(config.params or {})
    extra = getattr(config, "model_extra", None) or {}
    for key, value in extra.items():
        if key not in params:
            params[key] = value
    for key in ("index_params", "search_params", "sparse_index_params", "sparse_search_params"):
        value = getattr(config, key, None)
        if value and key not in params:
            params[key] = value
    params.pop("driver", None)
    params.pop(_BM25_CONFIG_KEY, None)
    return params


def _resolve_bm25_config(config: VectorStoreConfig) -> dict[str, Any]:
    params = dict(config.params or {})
    extra = getattr(config, "model_extra", None) or {}
    bm25 = params.get(_BM25_CONFIG_KEY)
    if bm25 is None and _BM25_CONFIG_KEY in extra:
        bm25 = extra.get(_BM25_CONFIG_KEY)
    if bm25 is None:
        return {"enabled": False}
    if isinstance(bm25, bool):
        return {"enabled": bm25}
    if isinstance(bm25, dict):
        payload = dict(bm25)
        payload.setdefault("enabled", True)
        return payload
    raise TypeError("bm25 config must be a bool or dict")


def _resolve_dense_vector_field(params: dict[str, Any], bm25_config: dict[str, Any]) -> str:
    value = bm25_config.get("dense_vector_field") or params.get("vector_field")
    if isinstance(value, list):
        if value:
            return str(value[0])
        return _DEFAULT_DENSE_VECTOR_FIELD
    return str(value or _DEFAULT_DENSE_VECTOR_FIELD)


def _resolve_sparse_vector_field(bm25_config: dict[str, Any]) -> str:
    return str(bm25_config.get("sparse_vector_field") or _DEFAULT_SPARSE_VECTOR_FIELD)


def _resolve_text_field(params: dict[str, Any], bm25_config: dict[str, Any]) -> str:
    return str(bm25_config.get("text_field") or params.get("text_field") or _DEFAULT_TEXT_FIELD)


def _resolve_metric_type(params: dict[str, Any] | list[dict[str, Any]] | None, fallback: str) -> str:
    if isinstance(params, list):
        if params:
            metric = params[0].get("metric_type")
            if metric:
                return str(metric)
        return fallback
    if isinstance(params, dict):
        metric = params.get("metric_type")
        if metric:
            return str(metric)
    return fallback


def _build_bm25_function(
    *,
    bm25_config: dict[str, Any],
    input_field: str,
    output_field: str,
) -> BaseMilvusBuiltInFunction:
    analyzer_params = bm25_config.get("analyzer_params")
    multi_analyzer_params = bm25_config.get("multi_analyzer_params")
    enable_match = bool(bm25_config.get("enable_match", False))
    return BM25BuiltInFunction(
        input_field_names=input_field,
        output_field_names=output_field,
        analyzer_params=analyzer_params,
        multi_analyzer_params=multi_analyzer_params,
        enable_match=enable_match,
    )


def _default_sparse_index_params() -> dict[str, Any]:
    return {
        "metric_type": "BM25",
        "index_type": "SPARSE_INVERTED_INDEX",
        "params": {"bm25_k1": 1.2, "bm25_b": 0.75},
    }


def _default_search_params(metric_type: str) -> dict[str, Any]:
    return {"metric_type": metric_type, "params": {}}


def _milvus_search_by_field(
    *,
    store: Milvus,
    field_name: str,
    data: list[float] | str,
    k: int,
    expr: str,
    default_metric: str,
) -> tuple[list[tuple[Document, float]], str]:
    if not store.client.has_collection(store.collection_name):
        return [], "similarity"
    _ensure_store_fields(store)
    search_params = _resolve_search_params(store, field_name, default_metric)
    output_fields = ["*"] if store.enable_dynamic_field else store._remove_forbidden_fields(store.fields[:])
    result = store.client.search(
        collection_name=store.collection_name,
        data=[data],
        anns_field=field_name,
        search_params=search_params,
        limit=max(1, k),
        filter=expr,
        output_fields=output_fields,
    )
    score_kind = _score_kind_for_metric(search_params.get("metric_type") or default_metric)
    return store._parse_documents_from_search_results(result), score_kind


def _ensure_store_fields(store: Milvus) -> None:
    if store.fields:
        return
    if store.client.has_collection(store.collection_name):
        store._extract_fields()


def _resolve_search_params(store: Milvus, field_name: str, default_metric: str) -> dict[str, Any]:
    params = store.search_params
    if isinstance(params, list):
        vector_fields = store.vector_fields
        if field_name in vector_fields and len(params) == len(vector_fields):
            payload = params[vector_fields.index(field_name)]
            return _normalize_search_params(payload, default_metric)
    if isinstance(params, dict):
        return _normalize_search_params(params, default_metric)
    return _normalize_search_params({}, default_metric)


def _normalize_search_params(payload: dict[str, Any] | None, default_metric: str) -> dict[str, Any]:
    data = dict(payload or {})
    metric = data.get("metric_type") or default_metric
    params = data.get("params")
    if params is None:
        params = {}
    if not isinstance(params, dict):
        raise ValueError("Search params must include a dict field named 'params'.")
    return {"metric_type": metric, "params": params}


def _score_kind_for_metric(metric_type: str) -> str:
    metric = str(metric_type or "").upper()
    if metric in {"L2"}:
        return "distance"
    return "similarity"


def _build_expr(filters: dict[str, Any] | None, *, record_type: str) -> str:
    parts = [f'{_METADATA_FIELD}["{_RECORD_TYPE_KEY}"] == {json.dumps(record_type)}']
    if filters:
        for key, value in filters.items():
            if isinstance(value, list):
                values = ", ".join(json.dumps(v, ensure_ascii=False) for v in value)
                parts.append(f'{_METADATA_FIELD}["{key}"] in [{values}]')
            else:
                parts.append(f'{_METADATA_FIELD}["{key}"] == {json.dumps(value, ensure_ascii=False)}')
    return " and ".join(parts)


def _parse_document(store: Milvus, row: dict[str, Any]) -> Document:
    return store._parse_document(dict(row))


def _convert_search_results(store: Milvus, results: list[tuple[Document, float]]) -> list[KnowledgeSearchHit]:
    hits: list[KnowledgeSearchHit] = []
    if not results:
        return hits
    relevance_fn = store._select_relevance_score_fn()
    for doc, score in results:
        chunk = _document_to_chunk(doc)
        hits.append(
            KnowledgeSearchHit(
                chunk=chunk,
                score=float(relevance_fn(score)),
                score_kind="similarity",
            )
        )
    return hits


def _convert_search_results_raw(
    results: list[tuple[Document, float]],
    *,
    score_kind: str,
) -> list[KnowledgeSearchHit]:
    hits: list[KnowledgeSearchHit] = []
    if not results:
        return hits
    for doc, score in results:
        chunk = _document_to_chunk(doc)
        hits.append(
            KnowledgeSearchHit(
                chunk=chunk,
                score=float(score),
                score_kind=score_kind,
            )
        )
    return hits


def _chunk_to_metadata(chunk: KnowledgeChunk) -> dict[str, Any]:
    return {
        _RECORD_TYPE_KEY: _RECORD_TYPE_CHUNK,
        "chunk_id": chunk.chunk_id,
        "doc_id": chunk.doc_id,
        "source_id": chunk.source_id,
        "doc_title": chunk.doc_title,
        "parent_id": chunk.parent_id,
        "chunk_type": chunk.chunk_type,
        "content_hash": chunk.content_hash,
        "chunk_index": chunk.chunk_index,
        "section_path": chunk.section_path,
        "version": chunk.version,
        "status": chunk.status,
        "metadata": chunk.metadata,
        "source_spans": [span.__dict__ for span in chunk.source_spans],
        "created_at": chunk.created_at,
        "updated_at": chunk.updated_at,
    }


def _doc_to_metadata(doc: KnowledgeDocument) -> dict[str, Any]:
    return {
        _RECORD_TYPE_KEY: _RECORD_TYPE_DOC,
        "doc_id": doc.doc_id,
        "source_id": doc.source_id,
        "title": doc.title,
        "source_uri": doc.source_uri,
        "version": doc.version,
        "language": doc.language,
        "status": doc.status,
        "content_hash": doc.content_hash,
        "content_ref": doc.content_ref,
        "created_at": doc.created_at,
        "updated_at": doc.updated_at,
        "tags": doc.tags,
        "metadata": doc.metadata,
    }


def _source_to_metadata(source: KnowledgeSource) -> dict[str, Any]:
    return {
        _RECORD_TYPE_KEY: _RECORD_TYPE_SOURCE,
        "source_id": source.source_id,
        "name": source.name,
        "source_type": source.source_type,
        "source_uri": source.source_uri,
        "tags": source.tags,
        "metadata": source.metadata,
    }


def _metadata_to_doc(metadata: dict[str, Any]) -> KnowledgeDocument:
    return KnowledgeDocument(
        doc_id=str(metadata.get("doc_id") or ""),
        source_id=str(metadata.get("source_id") or ""),
        title=str(metadata.get("title") or metadata.get("doc_id") or ""),
        content="",
        source_uri=metadata.get("source_uri"),
        version=str(metadata.get("version") or "1.0.0"),
        language=str(metadata.get("language") or "zh"),
        status=str(metadata.get("status") or "published"),
        content_hash=metadata.get("content_hash"),
        content_ref=metadata.get("content_ref"),
        created_at=metadata.get("created_at"),
        updated_at=metadata.get("updated_at"),
        vector=[],
        tags=metadata.get("tags") or [],
        metadata=metadata.get("metadata") or {},
    )


def _document_to_chunk(doc: Document) -> KnowledgeChunk:
    metadata = doc.metadata or {}
    source_spans = [
        SourceSpan(**span) if isinstance(span, dict) else span
        for span in (metadata.get("source_spans") or [])
    ]
    return KnowledgeChunk(
        chunk_id=str(metadata.get("chunk_id") or ""),
        doc_id=str(metadata.get("doc_id") or ""),
        source_id=str(metadata.get("source_id") or ""),
        content=doc.page_content,
        vector=[],
        doc_title=str(metadata.get("doc_title") or ""),
        parent_id=metadata.get("parent_id"),
        chunk_type=str(metadata.get("chunk_type") or "parent"),
        content_hash=metadata.get("content_hash"),
        sparse_vector=None,
        token_count=len(doc.page_content or ""),
        chunk_index=int(metadata.get("chunk_index") or 0),
        section_path=str(metadata.get("section_path") or ""),
        version=str(metadata.get("version") or "1.0.0"),
        status=str(metadata.get("status") or "published"),
        source_spans=source_spans,
        metadata=metadata.get("metadata") or {},
        created_at=metadata.get("created_at"),
        updated_at=metadata.get("updated_at"),
    )
