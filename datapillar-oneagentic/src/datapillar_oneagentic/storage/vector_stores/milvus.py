# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Milvus VectorStore implementation."""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.storage.vector_stores.base import (
    VectorCollectionSchema,
    VectorFieldType,
    VectorSearchResult,
    VectorStore,
    VectorStoreCapabilities,
)

logger = logging.getLogger(__name__)

_BM25_CONFIG_KEY = "bm25"
_DEFAULT_BM25_TEXT_FIELD = "content"
_DEFAULT_BM25_SPARSE_FIELD = "sparse_vector"


class MilvusVectorStore(VectorStore):
    """Milvus VectorStore"""

    def __init__(
        self,
        *,
        uri: str,
        token: str | None,
        user: str | None,
        password: str | None,
        db_name: str | None,
        namespace: str,
        dim: int | None,
        index_params: dict[str, Any] | None = None,
        sparse_index_params: dict[str, Any] | None = None,
        search_params: dict[str, Any] | None = None,
        sparse_search_params: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(namespace=namespace)
        import os

        self._is_remote = uri.startswith("http")
        if not self._is_remote:
            base_path = os.path.dirname(uri) or "."
            filename = os.path.basename(uri)
            self._uri = os.path.join(base_path, namespace, filename)
        else:
            self._uri = uri
        self._token = token
        self._user = user
        self._password = password
        self._db_name = db_name
        self._dim = dim
        self._client = None
        backend_params = params or {}
        self._bm25_config = _resolve_bm25_config(backend_params)
        self._bm25_enabled = bool(self._bm25_config.get("enabled", True))
        self._bm25_text_field = _resolve_bm25_text_field(self._bm25_config)
        self._bm25_sparse_field = _resolve_bm25_sparse_field(self._bm25_config)
        self._dense_index_params = _resolve_param_dict(index_params, backend_params.get("index_params"))
        self._sparse_index_params = _resolve_param_dict(
            sparse_index_params, backend_params.get("sparse_index_params")
        )
        self._dense_search_params = _resolve_param_dict(search_params, backend_params.get("search_params"))
        self._sparse_search_params = _resolve_param_dict(
            sparse_search_params, backend_params.get("sparse_search_params")
        )
        metric_type = (
            self._dense_index_params.get("metric_type")
            or backend_params.get("metric_type")
            or "COSINE"
        )
        self._metric_type = str(metric_type)
        metric_kind = self._metric_type.upper()
        self._score_kind = "similarity" if metric_kind in {"COSINE", "IP"} else "distance"
        self._bm25_collections: set[str] = set()

    @property
    def capabilities(self) -> VectorStoreCapabilities:
        return VectorStoreCapabilities(
            supports_dense=True,
            supports_sparse=True,
            supports_filter=True,
            supports_hybrid=self._bm25_enabled,
            supports_full_text=self._bm25_enabled,
        )

    async def initialize(self) -> None:
        try:
            from pymilvus import AsyncMilvusClient
        except ImportError as err:
            raise ImportError("Milvus dependency required: pip install pymilvus>=2.5.3") from err

        if self._is_remote:
            logger.info(f"MilvusVectorStore initialized (remote): {self._uri}, namespace={self._namespace}")
            self._client = AsyncMilvusClient(
                uri=self._uri,
                token=self._token,
                user=self._user,
                password=self._password,
                db_name=self._db_name,
            )
        else:
            import os
            os.makedirs(os.path.dirname(self._uri), exist_ok=True)
            logger.info(f"MilvusVectorStore initialized (local): {self._uri}")
            self._client = AsyncMilvusClient(
                uri=self._uri,
                user=self._user,
                password=self._password,
                db_name=self._db_name,
            )

    async def close(self) -> None:
        if self._client:
            await self._client.close()
        self._client = None
        logger.info("MilvusVectorStore closed")

    async def ensure_collection(self, schema: VectorCollectionSchema) -> None:
        if self._client is None:
            await self.initialize()

        name = self._namespaced(schema.name)
        has_collection = await self._client.has_collection(name)
        if has_collection:
            if _should_enable_bm25(
                schema,
                enabled=self._bm25_enabled,
                text_field=self._bm25_text_field,
                sparse_field=self._bm25_sparse_field,
            ):
                self._bm25_collections.add(schema.name)
            return

        if self._dim is None:
            raise ValueError("Embedding dimension is not configured for Milvus collection")

        from pymilvus import DataType, Function, FunctionType, MilvusClient

        milvus_schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        enable_bm25 = _should_enable_bm25(
            schema,
            enabled=self._bm25_enabled,
            text_field=self._bm25_text_field,
            sparse_field=self._bm25_sparse_field,
        )
        bm25_field_kwargs = _build_bm25_field_kwargs(self._bm25_config) if enable_bm25 else {}
        for field in schema.fields:
            if field.name == schema.primary_key:
                milvus_schema.add_field(field.name, DataType.VARCHAR, is_primary=True, max_length=128)
                continue
            if field.field_type == VectorFieldType.STRING:
                if enable_bm25 and field.name == self._bm25_text_field:
                    field_kwargs = {"max_length": 65535, **bm25_field_kwargs}
                    milvus_schema.add_field(field.name, DataType.VARCHAR, **field_kwargs)
                else:
                    milvus_schema.add_field(field.name, DataType.VARCHAR, max_length=65535)
            elif field.field_type == VectorFieldType.INT:
                milvus_schema.add_field(field.name, DataType.INT64)
            elif field.field_type == VectorFieldType.FLOAT:
                milvus_schema.add_field(field.name, DataType.FLOAT)
            elif field.field_type == VectorFieldType.JSON:
                milvus_schema.add_field(field.name, DataType.VARCHAR, max_length=65535)
            elif field.field_type == VectorFieldType.VECTOR:
                dim = field.dimension or self._dim
                milvus_schema.add_field(field.name, DataType.FLOAT_VECTOR, dim=dim)
            elif field.field_type == VectorFieldType.SPARSE_VECTOR:
                milvus_schema.add_field(field.name, DataType.SPARSE_FLOAT_VECTOR)
            else:
                raise ValueError(f"Unsupported field type: {field.field_type}")

        if enable_bm25:
            bm25_function = Function(
                name="bm25_fn",
                input_field_names=[self._bm25_text_field],
                output_field_names=self._bm25_sparse_field,
                function_type=FunctionType.BM25,
            )
            milvus_schema.add_function(bm25_function)
            self._bm25_collections.add(schema.name)

        index_params = MilvusClient.prepare_index_params()
        for field in schema.fields:
            if field.field_type == VectorFieldType.VECTOR:
                dense_spec = _resolve_index_spec(
                    self._dense_index_params,
                    default_type="FLAT",
                    default_metric=self._metric_type,
                    default_params={},
                )
                index_params.add_index(
                    field_name=field.name,
                    index_type=dense_spec["index_type"],
                    metric_type=dense_spec["metric_type"],
                    params=dense_spec["params"],
                )
            if (
                enable_bm25
                and field.field_type == VectorFieldType.SPARSE_VECTOR
                and field.name == self._bm25_sparse_field
            ):
                sparse_spec = _resolve_index_spec(
                    self._sparse_index_params,
                    default_type="SPARSE_INVERTED_INDEX",
                    default_metric="BM25",
                    default_params={"bm25_k1": 1.2, "bm25_b": 0.75},
                )
                index_params.add_index(
                    field_name=field.name,
                    index_type=sparse_spec["index_type"],
                    metric_type=sparse_spec["metric_type"],
                    params=sparse_spec["params"],
                )

        try:
            await self._client.create_collection(
                collection_name=name,
                schema=milvus_schema,
                index_params=index_params,
            )
            logger.info(f"Create Milvus collection: {name}")
        except Exception as exc:
            if _is_collection_exists(exc):
                logger.info(f"Reuse Milvus collection: {name}")
                return
            raise

    async def add(self, collection: str, records: list[dict[str, Any]]) -> None:
        if not records:
            return
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        if schema.name in self._bm25_collections:
            cleaned = []
            for record in records:
                if self._bm25_sparse_field in record:
                    record = dict(record)
                    record.pop(self._bm25_sparse_field, None)
                cleaned.append(record)
            await self._client.insert(collection_name=name, data=cleaned)
            return
        await self._client.insert(collection_name=name, data=records)

    async def get(self, collection: str, ids: list[str]) -> list[dict[str, Any]]:
        if not ids:
            return []
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        result = await self._client.get(
            collection_name=name,
            ids=ids,
            output_fields=self._output_fields(schema),
        )
        return list(result or [])

    async def delete(self, collection: str, ids: list[str]) -> int:
        if not ids:
            return 0
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        values = ", ".join(json.dumps(i, ensure_ascii=False) for i in ids)
        expr = f'{schema.primary_key} in [{values}]'
        await self._client.delete(collection_name=name, filter=expr)
        return len(ids)

    async def search(
        self,
        collection: str,
        query_vector: list[float],
        k: int = 5,
        filters: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)

        filter_expr = _build_milvus_filter(filters) if filters else None
        result = await self._client.search(
            collection_name=name,
            data=[query_vector],
            anns_field="vector",
            limit=k,
            search_params=_build_search_params(self._dense_search_params, self._metric_type),
            filter=filter_expr,
            output_fields=self._output_fields(schema),
        )

        results: list[VectorSearchResult] = []
        if result and result[0]:
            for hit in result[0]:
                entity = hit.get("entity", {})
                entity[schema.primary_key] = hit.get("id", entity.get(schema.primary_key))
                score = hit.get("score")
                if score is None:
                    score = hit.get("distance")
                if score is None:
                    raise ValueError("Milvus search result missing score")
                results.append(
                    VectorSearchResult(
                        record=entity,
                        score=float(score),
                        score_kind=self._score_kind,
                    )
                )
        return results

    async def hybrid_search(
        self,
        collection: str,
        query_vector: list[float],
        query_text: str,
        k: int = 5,
        filters: dict[str, Any] | None = None,
        rrf_k: int = 60,
    ) -> list[VectorSearchResult]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        if schema.name not in self._bm25_collections:
            raise ValueError("Milvus hybrid search requires BM25-enabled collection schema")

        from pymilvus import AnnSearchRequest, RRFRanker

        dense_req = AnnSearchRequest(
            data=[query_vector],
            anns_field="vector",
            param=_build_search_params(self._dense_search_params, self._metric_type),
            limit=k,
        )
        sparse_req = AnnSearchRequest(
            data=[query_text],
            anns_field=self._bm25_sparse_field,
            param=_build_search_params(self._sparse_search_params, "BM25"),
            limit=k,
        )
        ranker = RRFRanker(k=rrf_k)
        result = await self._client.hybrid_search(
            collection_name=name,
            reqs=[sparse_req, dense_req],
            ranker=ranker,
            limit=k,
            output_fields=self._output_fields(schema),
        )

        results: list[VectorSearchResult] = []
        if result and result[0]:
            for hit in result[0]:
                entity = hit.get("entity", {})
                entity[schema.primary_key] = hit.get("id", entity.get(schema.primary_key))
                score = hit.get("score")
                if score is None:
                    score = hit.get("distance")
                if score is None:
                    raise ValueError("Milvus hybrid search result missing score")
                results.append(
                    VectorSearchResult(
                        record=entity,
                        score=float(score),
                        score_kind="similarity",
                    )
                )
        return results

    async def full_text_search(
        self,
        collection: str,
        query_text: str,
        k: int = 5,
        filters: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        if schema.name not in self._bm25_collections:
            raise ValueError("Milvus full-text search requires BM25-enabled collection schema")

        filter_expr = _build_milvus_filter(filters) if filters else None
        result = await self._client.search(
            collection_name=name,
            data=[query_text],
            anns_field=self._bm25_sparse_field,
            limit=k,
            search_params=_build_search_params(self._sparse_search_params, "BM25"),
            filter=filter_expr,
            output_fields=self._output_fields(schema),
        )

        results: list[VectorSearchResult] = []
        if result and result[0]:
            for hit in result[0]:
                entity = hit.get("entity", {})
                entity[schema.primary_key] = hit.get("id", entity.get(schema.primary_key))
                score = hit.get("score")
                if score is None:
                    score = hit.get("distance")
                if score is None:
                    raise ValueError("Milvus full-text search result missing score")
                results.append(
                    VectorSearchResult(
                        record=entity,
                        score=float(score),
                        score_kind="similarity",
                    )
                )
        return results

    async def query(
        self,
        collection: str,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)

        filter_expr = _build_milvus_filter(filters) if filters else None
        result = await self._client.query(
            collection_name=name,
            filter=filter_expr,
            limit=limit,
            output_fields=self._output_fields(schema),
        )
        return list(result or [])

    async def count(self, collection: str) -> int:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        stats = await self._client.get_collection_stats(name)
        return stats.get("row_count", 0)

    def _output_fields(self, schema: VectorCollectionSchema) -> list[str]:
        fields = [field.name for field in schema.fields]
        if schema.name in self._bm25_collections:
            fields = [field for field in fields if field != self._bm25_sparse_field]
        if schema.primary_key not in fields:
            fields.insert(0, schema.primary_key)
        return fields


def _build_milvus_filter(filters: dict[str, Any]) -> str:
    parts = []
    for key, value in filters.items():
        if isinstance(value, list):
            values = ", ".join(json.dumps(v, ensure_ascii=False) for v in value)
            parts.append(f"{key} in [{values}]")
        else:
            parts.append(f"{key} == {json.dumps(value, ensure_ascii=False)}")
    return " and ".join(parts)


def _is_collection_exists(exc: Exception) -> bool:
    message = str(exc).lower()
    return "already exists" in message or "already exist" in message or "collection exists" in message


def _should_enable_bm25(
    schema: VectorCollectionSchema,
    *,
    enabled: bool,
    text_field: str,
    sparse_field: str,
) -> bool:
    if not enabled:
        return False
    field_names = {field.name for field in schema.fields}
    return text_field in field_names and sparse_field in field_names


def _resolve_param_dict(
    explicit: dict[str, Any] | None,
    fallback: dict[str, Any] | None,
) -> dict[str, Any]:
    if explicit:
        return dict(explicit)
    if fallback:
        return dict(fallback)
    return {}


def _resolve_bm25_config(params: dict[str, Any]) -> dict[str, Any]:
    bm25 = params.get(_BM25_CONFIG_KEY)
    if bm25 is None:
        return {"enabled": True}
    if isinstance(bm25, bool):
        return {"enabled": bm25}
    if isinstance(bm25, dict):
        payload = dict(bm25)
        payload.setdefault("enabled", True)
        return payload
    raise TypeError("VectorStoreConfig.params.bm25 must be a bool or dict")


def _resolve_bm25_text_field(config: dict[str, Any]) -> str:
    value = config.get("text_field") or _DEFAULT_BM25_TEXT_FIELD
    if value != _DEFAULT_BM25_TEXT_FIELD:
        raise ValueError("BM25 text_field must be 'content' in current schema")
    return str(value)


def _resolve_bm25_sparse_field(config: dict[str, Any]) -> str:
    value = config.get("sparse_vector_field") or _DEFAULT_BM25_SPARSE_FIELD
    if value != _DEFAULT_BM25_SPARSE_FIELD:
        raise ValueError("BM25 sparse_vector_field must be 'sparse_vector' in current schema")
    return str(value)


def _build_bm25_field_kwargs(config: dict[str, Any]) -> dict[str, Any]:
    analyzer_params = config.get("analyzer_params")
    multi_analyzer_params = config.get("multi_analyzer_params")
    if analyzer_params is not None and multi_analyzer_params is not None:
        raise ValueError("BM25 analyzer_params and multi_analyzer_params cannot be set together")
    kwargs: dict[str, Any] = {"enable_analyzer": True}
    kwargs["enable_match"] = bool(config.get("enable_match", False))
    if multi_analyzer_params is not None:
        kwargs["multi_analyzer_params"] = multi_analyzer_params
    elif analyzer_params is not None:
        kwargs["analyzer_params"] = analyzer_params
    return kwargs


def _resolve_index_spec(
    config: dict[str, Any],
    *,
    default_type: str,
    default_metric: str,
    default_params: dict[str, Any],
) -> dict[str, Any]:
    params = dict(default_params)
    overrides = config or {}
    override_params = overrides.get("params")
    if override_params is not None:
        if not isinstance(override_params, dict):
            raise ValueError("Index params must include a dict field named 'params'.")
        params.update(override_params)
    return {
        "index_type": overrides.get("index_type") or default_type,
        "metric_type": overrides.get("metric_type") or default_metric,
        "params": params,
    }


def _build_search_params(config: dict[str, Any], metric_type: str) -> dict[str, Any]:
    payload = dict(config or {})
    params = payload.get("params")
    if params is None:
        params = {}
    if not isinstance(params, dict):
        raise ValueError("Search params must include a dict field named 'params'.")
    return {
        "metric_type": payload.get("metric_type") or metric_type,
        "params": params,
    }
