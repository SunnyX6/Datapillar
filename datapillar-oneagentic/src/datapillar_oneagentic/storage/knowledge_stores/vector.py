# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""VectorKnowledgeStore implementation."""

from __future__ import annotations

import hashlib
import json
import logging
from typing import Any

from datapillar_oneagentic.knowledge.models import (
    KnowledgeChunk,
    KnowledgeDocument,
    KnowledgeSearchHit,
    KnowledgeSource,
    SourceSpan,
)
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore
from datapillar_oneagentic.storage.vector_stores import (
    VectorCollectionSchema,
    VectorField,
    VectorFieldType,
    VectorStore,
)
from datapillar_oneagentic.utils.time import now_ms

logger = logging.getLogger(__name__)

_SOURCES = "knowledge_sources"
_DOCS = "knowledge_docs"
_CHUNKS = "knowledge_chunks"
_KEY_SEPARATOR = "::"


class VectorKnowledgeStore(KnowledgeStore):
    """Knowledge storage backed by VectorStore."""

    def __init__(self, *, vector_store: VectorStore, dimension: int, namespace: str) -> None:
        self._vector_store = vector_store
        self._dimension = dimension
        self._namespace = namespace
        self._register_schemas()

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    def supports_hybrid(self) -> bool:
        return self._vector_store.capabilities.supports_hybrid

    @property
    def supports_full_text(self) -> bool:
        return False

    def _register_schemas(self) -> None:
        self._vector_store.register_schema(
            VectorCollectionSchema(
                name=_SOURCES,
                primary_key="source_key",
                fields=[
                    VectorField("source_key", VectorFieldType.STRING),
                    VectorField("namespace", VectorFieldType.STRING),
                    VectorField("source_id", VectorFieldType.STRING),
                    VectorField("name", VectorFieldType.STRING),
                    VectorField("source_type", VectorFieldType.STRING),
                    VectorField("source_uri", VectorFieldType.STRING),
                    VectorField("tags", VectorFieldType.JSON),
                    VectorField("metadata", VectorFieldType.JSON),
                    VectorField("created_at", VectorFieldType.INT),
                    VectorField("updated_at", VectorFieldType.INT),
                    VectorField("vector", VectorFieldType.VECTOR, dimension=self._dimension),
                ],
            )
        )
        self._vector_store.register_schema(
            VectorCollectionSchema(
                name=_DOCS,
                primary_key="doc_key",
                fields=[
                    VectorField("doc_key", VectorFieldType.STRING),
                    VectorField("namespace", VectorFieldType.STRING),
                    VectorField("doc_id", VectorFieldType.STRING),
                    VectorField("source_id", VectorFieldType.STRING),
                    VectorField("source_uri", VectorFieldType.STRING),
                    VectorField("title", VectorFieldType.STRING),
                    VectorField("version", VectorFieldType.STRING),
                    VectorField("content_hash", VectorFieldType.STRING),
                    VectorField("status", VectorFieldType.STRING),
                    VectorField("language", VectorFieldType.STRING),
                    VectorField("tags", VectorFieldType.JSON),
                    VectorField("metadata", VectorFieldType.JSON),
                    VectorField("content_ref", VectorFieldType.STRING),
                    VectorField("created_at", VectorFieldType.INT),
                    VectorField("updated_at", VectorFieldType.INT),
                    VectorField("vector", VectorFieldType.VECTOR, dimension=self._dimension),
                ],
            )
        )
        self._vector_store.register_schema(
            VectorCollectionSchema(
                name=_CHUNKS,
                primary_key="chunk_key",
                fields=[
                    VectorField("chunk_key", VectorFieldType.STRING),
                    VectorField("namespace", VectorFieldType.STRING),
                    VectorField("chunk_id", VectorFieldType.STRING),
                    VectorField("doc_id", VectorFieldType.STRING),
                    VectorField("source_id", VectorFieldType.STRING),
                    VectorField("doc_title", VectorFieldType.STRING),
                    VectorField("parent_id", VectorFieldType.STRING),
                    VectorField("chunk_type", VectorFieldType.STRING),
                    VectorField("content", VectorFieldType.STRING),
                    VectorField("content_hash", VectorFieldType.STRING),
                    VectorField("token_count", VectorFieldType.INT),
                    VectorField("chunk_index", VectorFieldType.INT),
                    VectorField("section_path", VectorFieldType.STRING),
                    VectorField("version", VectorFieldType.STRING),
                    VectorField("status", VectorFieldType.STRING),
                    VectorField("metadata", VectorFieldType.JSON),
                    VectorField("sparse_vector", VectorFieldType.SPARSE_VECTOR),
                    VectorField("created_at", VectorFieldType.INT),
                    VectorField("updated_at", VectorFieldType.INT),
                    VectorField("vector", VectorFieldType.VECTOR, dimension=self._dimension),
                ],
            )
        )

    async def initialize(self) -> None:
        await self._vector_store.ensure_collection(self._vector_store.get_schema(_SOURCES))
        await self._vector_store.ensure_collection(self._vector_store.get_schema(_DOCS))
        await self._vector_store.ensure_collection(self._vector_store.get_schema(_CHUNKS))

    async def close(self) -> None:
        # vector_store lifecycle is managed by the caller.
        return None

    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        if not sources:
            return
        source_keys = [self._build_key(source.source_id) for source in sources]
        await self._vector_store.delete(_SOURCES, source_keys)
        now = now_ms()
        records = []
        for source in sources:
            record = {
                "source_key": self._build_key(source.source_id),
                "namespace": self._namespace,
                "source_id": source.source_id,
                "name": source.name,
                "source_type": source.source_type,
                "source_uri": source.source_uri or "",
                "tags": json.dumps(source.tags, ensure_ascii=False),
                "metadata": json.dumps(source.metadata, ensure_ascii=False),
                "created_at": now,
                "updated_at": now,
                "vector": _zero_vector(self._dimension),
            }
            records.append(record)
        await self._vector_store.add(_SOURCES, records)

    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        if not docs:
            return
        records = []
        for doc in docs:
            created_at = doc.created_at or now_ms()
            updated_at = doc.updated_at or created_at
            content_hash = doc.content_hash or _hash_content(doc.content)
            vector = doc.vector or _zero_vector(self._dimension)
            record = {
                "doc_key": self._build_key(doc.doc_id),
                "namespace": self._namespace,
                "doc_id": doc.doc_id,
                "source_id": doc.source_id,
                "source_uri": doc.source_uri or "",
                "title": doc.title,
                "version": doc.version,
                "content_hash": content_hash,
                "status": doc.status,
                "language": doc.language,
                "tags": json.dumps(doc.tags, ensure_ascii=False),
                "metadata": json.dumps(doc.metadata, ensure_ascii=False),
                "content_ref": doc.content_ref or "",
                "created_at": created_at,
                "updated_at": updated_at,
                "vector": vector,
            }
            records.append(record)
        await self._vector_store.add(_DOCS, records)

    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        if not chunks:
            return
        records = []
        for chunk in chunks:
            created_at = chunk.created_at or now_ms()
            updated_at = chunk.updated_at or created_at
            metadata = dict(chunk.metadata)
            if chunk.source_spans:
                metadata["source_spans"] = [
                    {
                        "page": span.page,
                        "start_offset": span.start_offset,
                        "end_offset": span.end_offset,
                        "block_id": span.block_id,
                    }
                    for span in chunk.source_spans
                ]
            content_hash = chunk.content_hash or _hash_content(chunk.content)
            record = {
                "chunk_key": self._build_key(chunk.chunk_id),
                "namespace": self._namespace,
                "chunk_id": chunk.chunk_id,
                "doc_id": chunk.doc_id,
                "source_id": chunk.source_id,
                "doc_title": chunk.doc_title,
                "parent_id": chunk.parent_id or "",
                "chunk_type": chunk.chunk_type,
                "content": chunk.content,
                "content_hash": content_hash,
                "token_count": chunk.token_count,
                "chunk_index": chunk.chunk_index,
                "section_path": chunk.section_path,
                "version": chunk.version,
                "status": chunk.status,
                "metadata": json.dumps(metadata, ensure_ascii=False),
                "sparse_vector": json.dumps(chunk.sparse_vector or {}, ensure_ascii=False),
                "created_at": created_at,
                "updated_at": updated_at,
                "vector": chunk.vector,
            }
            records.append(record)
        await self._vector_store.add(_CHUNKS, records)

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        merged_filters = dict(filters or {})
        merged_filters["namespace"] = self._namespace
        results = await self._vector_store.search(
            _CHUNKS,
            query_vector=query_vector,
            k=k,
            filters=merged_filters,
        )
        hits: list[KnowledgeSearchHit] = []
        for item in results:
            chunk = _row_to_chunk(item.record)
            hits.append(
                KnowledgeSearchHit(
                    chunk=chunk,
                    score=item.score,
                    score_kind=item.score_kind,
                )
            )
        return hits

    async def hybrid_search_chunks(
        self,
        *,
        query_vector: list[float],
        query_text: str,
        k: int,
        filters: dict[str, Any] | None = None,
        rrf_k: int = 60,
    ) -> list[KnowledgeSearchHit]:
        merged_filters = dict(filters or {})
        merged_filters["namespace"] = self._namespace
        results = await self._vector_store.hybrid_search(
            _CHUNKS,
            query_vector=query_vector,
            query_text=query_text,
            k=k,
            filters=merged_filters,
            rrf_k=rrf_k,
        )
        hits: list[KnowledgeSearchHit] = []
        for item in results:
            chunk = _row_to_chunk(item.record)
            hits.append(
                KnowledgeSearchHit(
                    chunk=chunk,
                    score=item.score,
                    score_kind=item.score_kind,
                )
            )
        return hits

    async def full_text_search_chunks(
        self,
        *,
        query_text: str,
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        raise ValueError("Full-text retrieval is not supported by the current backend.")

    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        rows = await self._vector_store.get(_DOCS, [self._build_key(doc_id)])
        if not rows:
            return None
        return _row_to_doc(rows[0])

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        if not chunk_ids:
            return []
        keys = [self._build_key(chunk_id) for chunk_id in chunk_ids]
        rows = await self._vector_store.get(_CHUNKS, keys)
        return [_row_to_chunk(row) for row in rows]

    async def delete_chunks(self, chunk_ids: list[str]) -> int:
        if not chunk_ids:
            return 0
        keys = [self._build_key(chunk_id) for chunk_id in chunk_ids]
        return await self._vector_store.delete(_CHUNKS, keys)

    async def query_chunks(
        self,
        *,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[KnowledgeChunk]:
        merged_filters = dict(filters or {})
        merged_filters["namespace"] = self._namespace
        rows = await self._vector_store.query(_CHUNKS, filters=merged_filters, limit=limit)
        return [_row_to_chunk(row) for row in rows]

    async def delete_doc(self, doc_id: str) -> int:
        key = self._build_key(doc_id)
        return await self._vector_store.delete(_DOCS, [key])

    async def delete_doc_chunks(self, doc_id: str) -> int:
        rows = await self._vector_store.query(
            _CHUNKS,
            filters={"namespace": self._namespace, "doc_id": doc_id},
        )
        chunk_keys = []
        for row in rows:
            chunk_key = row.get("chunk_key") or row.get("id")
            if chunk_key:
                chunk_keys.append(str(chunk_key))
        if not chunk_keys:
            return 0
        return await self._vector_store.delete(_CHUNKS, chunk_keys)

    def _build_key(self, raw_id: str) -> str:
        return f"{self._namespace}{_KEY_SEPARATOR}{raw_id}"


def _row_to_doc(row: dict[str, Any]) -> KnowledgeDocument:
    return KnowledgeDocument(
        doc_id=row.get("doc_id", ""),
        source_id=row.get("source_id", ""),
        title=row.get("title", ""),
        content="",
        source_uri=row.get("source_uri") or None,
        version=row.get("version", "1.0.0"),
        language=row.get("language", "zh"),
        status=row.get("status", "published"),
        content_hash=row.get("content_hash"),
        content_ref=row.get("content_ref") or None,
        created_at=row.get("created_at"),
        updated_at=row.get("updated_at"),
        vector=row.get("vector", []),
        tags=_loads_json(row.get("tags")),
        metadata=_loads_json(row.get("metadata")),
    )


def _row_to_chunk(row: dict[str, Any]) -> KnowledgeChunk:
    sparse_vector = row.get("sparse_vector")
    if isinstance(sparse_vector, str):
        sparse_vector = _loads_json(sparse_vector)
    metadata = _loads_json(row.get("metadata"))
    spans = []
    raw_spans = metadata.pop("source_spans", [])
    for item in raw_spans if isinstance(raw_spans, list) else []:
        if not isinstance(item, dict):
            continue
        spans.append(
            SourceSpan(
                page=item.get("page"),
                start_offset=item.get("start_offset"),
                end_offset=item.get("end_offset"),
                block_id=item.get("block_id"),
            )
        )
    return KnowledgeChunk(
        chunk_id=row.get("chunk_id", ""),
        doc_id=row.get("doc_id", ""),
        source_id=row.get("source_id", ""),
        doc_title=row.get("doc_title", ""),
        parent_id=row.get("parent_id") or None,
        chunk_type=row.get("chunk_type", "parent"),
        content=row.get("content", ""),
        content_hash=row.get("content_hash"),
        vector=row.get("vector", []),
        sparse_vector=sparse_vector or None,
        token_count=row.get("token_count", 0),
        chunk_index=row.get("chunk_index", 0),
        section_path=row.get("section_path", ""),
        version=row.get("version", "1.0.0"),
        status=row.get("status", "published"),
        source_spans=spans,
        metadata=metadata,
        created_at=row.get("created_at"),
        updated_at=row.get("updated_at"),
    )


def _hash_content(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _loads_json(value: Any) -> dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return {}
    return {}


def _zero_vector(dim: int) -> list[float]:
    return [0.0 for _ in range(dim)]
