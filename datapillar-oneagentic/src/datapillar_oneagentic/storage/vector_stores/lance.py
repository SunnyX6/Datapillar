"""
Lance VectorStore 实现
"""

from __future__ import annotations

import inspect
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


class LanceVectorStore(VectorStore):
    """LanceDB VectorStore"""

    def __init__(self, *, path: str, namespace: str) -> None:
        super().__init__(namespace=namespace)
        import os

        self._base_path = path
        self._path = os.path.join(path, namespace)
        self._db = None
        self._tables: dict[str, Any] = {}

    @property
    def capabilities(self) -> VectorStoreCapabilities:
        return VectorStoreCapabilities(supports_dense=True, supports_sparse=True, supports_filter=True)

    async def initialize(self) -> None:
        import lancedb

        self._db = await lancedb.connect_async(self._path)
        logger.info(f"初始化 LanceVectorStore: {self._path}")

    async def close(self) -> None:
        self._db = None
        self._tables.clear()
        logger.info("LanceVectorStore 已关闭")

    async def ensure_collection(self, schema: VectorCollectionSchema) -> None:
        if self._db is None:
            await self.initialize()

        name = self._namespaced(schema.name)
        if schema.name in self._tables:
            return

        table_names = await self._db.list_tables()
        if name in table_names:
            self._tables[schema.name] = await self._db.open_table(name)
            return

        import pyarrow as pa

        fields = []
        for field in schema.fields:
            if field.field_type == VectorFieldType.STRING:
                fields.append(pa.field(field.name, pa.string()))
            elif field.field_type == VectorFieldType.INT:
                fields.append(pa.field(field.name, pa.int64()))
            elif field.field_type == VectorFieldType.FLOAT:
                fields.append(pa.field(field.name, pa.float32()))
            elif field.field_type == VectorFieldType.JSON:
                fields.append(pa.field(field.name, pa.string()))
            elif field.field_type == VectorFieldType.VECTOR:
                if field.dimension is None:
                    raise ValueError(f"向量字段缺少 dimension: {field.name}")
                fields.append(pa.field(field.name, pa.list_(pa.float32(), list_size=field.dimension)))
            elif field.field_type == VectorFieldType.SPARSE_VECTOR:
                fields.append(pa.field(field.name, pa.string()))
            else:
                raise ValueError(f"不支持的字段类型: {field.field_type}")

        pa_schema = pa.schema(fields)
        try:
            self._tables[schema.name] = await self._db.create_table(
                name,
                data=[],
                schema=pa_schema,
                exist_ok=True,
            )
            logger.info(f"创建或复用 Lance 表: {name}")
        except TypeError as exc:
            if "exist_ok" not in str(exc):
                raise
            try:
                self._tables[schema.name] = await self._db.create_table(
                    name,
                    data=[],
                    schema=pa_schema,
                )
                logger.info(f"创建 Lance 表: {name}")
            except Exception as inner_exc:
                if _is_table_exists_error(inner_exc):
                    self._tables[schema.name] = await self._db.open_table(name)
                    logger.info(f"复用 Lance 表: {name}")
                else:
                    raise
        except Exception as exc:
            if _is_table_exists_error(exc):
                self._tables[schema.name] = await self._db.open_table(name)
                logger.info(f"复用 Lance 表: {name}")
            else:
                raise

    async def add(self, collection: str, records: list[dict[str, Any]]) -> None:
        if not records:
            return
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        table = self._tables[collection]
        await table.add(records)

    async def get(self, collection: str, ids: list[str]) -> list[dict[str, Any]]:
        if not ids:
            return []
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        table = self._tables[collection]

        from lancedb.util import value_to_sql

        values = ", ".join(value_to_sql(i) for i in ids)
        expr = f"{schema.primary_key} IN ({values})"
        return await table.query().where(expr).to_list()

    async def delete(self, collection: str, ids: list[str]) -> int:
        if not ids:
            return 0
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        table = self._tables[collection]

        from lancedb.util import value_to_sql

        values = ", ".join(value_to_sql(i) for i in ids)
        expr = f"{schema.primary_key} IN ({values})"
        await table.delete(expr)
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
        table = self._tables[collection]

        query = table.search(query_vector, query_type="vector")
        if inspect.isawaitable(query):
            query = await query
        if filters:
            query = query.where(_build_lance_filter(filters))
        rows = await query.limit(k).to_list()
        results: list[VectorSearchResult] = []
        for row in rows:
            distance = row.get("_distance")
            if distance is None:
                raise ValueError("LanceDB 搜索结果缺少 _distance")
            results.append(
                VectorSearchResult(
                    record=row,
                    score=float(distance),
                    score_kind="distance",
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
        table = self._tables[collection]

        query = table.query()
        if inspect.isawaitable(query):
            query = await query
        if filters:
            query = query.where(_build_lance_filter(filters))
        if limit is not None:
            query = query.limit(limit)
        return await query.to_list()

    async def count(self, collection: str) -> int:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        table = self._tables[collection]
        return await table.count_rows()


def _build_lance_filter(filters: dict[str, Any]) -> str:
    from lancedb.util import value_to_sql

    parts = []
    for key, value in filters.items():
        if isinstance(value, list):
            values = ", ".join(value_to_sql(v) for v in value)
            parts.append(f"{key} IN ({values})")
        else:
            parts.append(f"{key} = {value_to_sql(value)}")
    return " AND ".join(parts)


def _is_table_exists_error(exc: Exception) -> bool:
    message = str(exc).lower()
    return "already exists" in message
