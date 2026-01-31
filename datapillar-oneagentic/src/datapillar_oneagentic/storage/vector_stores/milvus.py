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


class MilvusVectorStore(VectorStore):
    """Milvus VectorStore"""

    def __init__(
        self,
        *,
        uri: str,
        token: str | None,
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
        self._dim = dim
        self._client = None
        backend_params = params or {}
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
            supports_hybrid=True,
        )

    async def initialize(self) -> None:
        try:
            from pymilvus import AsyncMilvusClient
        except ImportError as err:
            raise ImportError("Milvus dependency required: pip install pymilvus>=2.5.3") from err

        if self._is_remote:
            logger.info(f"MilvusVectorStore initialized (remote): {self._uri}, namespace={self._namespace}")
            self._client = AsyncMilvusClient(uri=self._uri, token=self._token)
        else:
            import os
            os.makedirs(os.path.dirname(self._uri), exist_ok=True)
            logger.info(f"MilvusVectorStore initialized (local): {self._uri}")
            self._client = AsyncMilvusClient(uri=self._uri)

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
            if _should_enable_bm25(schema):
                self._bm25_collections.add(schema.name)
            return

        if self._dim is None:
            raise ValueError("Embedding dimension is not configured for Milvus collection")

        from pymilvus import DataType, Function, FunctionType, MilvusClient

        milvus_schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        enable_bm25 = _should_enable_bm25(schema)
        for field in schema.fields:
            if field.name == schema.primary_key:
                milvus_schema.add_field(field.name, DataType.VARCHAR, is_primary=True, max_length=128)
                continue
            if field.field_type == VectorFieldType.STRING:
                if enable_bm25 and field.name == "content":
                    milvus_schema.add_field(
                        field.name,
                        DataType.VARCHAR,
                        max_length=65535,
                        enable_analyzer=True,
                        enable_match=True,
                    )
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
                input_field_names=["content"],
                output_field_names="sparse_vector",
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
            if enable_bm25 and field.field_type == VectorFieldType.SPARSE_VECTOR:
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
                if "sparse_vector" in record:
                    record = dict(record)
                    record.pop("sparse_vector", None)
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
            output_fields=["*"],
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
            output_fields=["*"],
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
            anns_field="sparse_vector",
            param=_build_search_params(self._sparse_search_params, "BM25"),
            limit=k,
        )
        ranker = RRFRanker(k=rrf_k)
        result = await self._client.hybrid_search(
            collection_name=name,
            reqs=[sparse_req, dense_req],
            ranker=ranker,
            limit=k,
            output_fields=["*"],
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
            output_fields=["*"],
        )
        return list(result or [])

    async def count(self, collection: str) -> int:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        name = self._namespaced(collection)
        stats = await self._client.get_collection_stats(name)
        return stats.get("row_count", 0)


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


def _should_enable_bm25(schema: VectorCollectionSchema) -> bool:
    field_names = {field.name for field in schema.fields}
    return "content" in field_names and "sparse_vector" in field_names


def _resolve_param_dict(
    explicit: dict[str, Any] | None,
    fallback: dict[str, Any] | None,
) -> dict[str, Any]:
    if explicit:
        return dict(explicit)
    if fallback:
        return dict(fallback)
    return {}


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
