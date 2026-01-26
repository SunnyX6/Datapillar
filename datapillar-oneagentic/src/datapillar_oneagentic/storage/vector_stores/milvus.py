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
        self._metric_type = "COSINE"
        self._score_kind = "similarity" if self._metric_type in {"COSINE", "IP"} else "distance"

    @property
    def capabilities(self) -> VectorStoreCapabilities:
        return VectorStoreCapabilities(supports_dense=True, supports_sparse=True, supports_filter=True)

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
            return

        if self._dim is None:
            raise ValueError("Embedding dimension is not configured for Milvus collection")

        from pymilvus import DataType, MilvusClient

        milvus_schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        for field in schema.fields:
            if field.name == schema.primary_key:
                milvus_schema.add_field(field.name, DataType.VARCHAR, is_primary=True, max_length=128)
                continue
            if field.field_type == VectorFieldType.STRING:
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
                milvus_schema.add_field(field.name, DataType.VARCHAR, max_length=65535)
            else:
                raise ValueError(f"Unsupported field type: {field.field_type}")

        index_params = MilvusClient.prepare_index_params()
        for field in schema.fields:
            if field.field_type == VectorFieldType.VECTOR:
                index_params.add_index(
                    field_name=field.name,
                    index_type="FLAT",
                    metric_type=self._metric_type,
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
            limit=k,
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
