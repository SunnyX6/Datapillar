"""
LanceVectorStore - LanceDB 向量存储

默认的向量数据库实现，特点：
- 嵌入式，无需额外服务
- 异步支持
- 高性能列式存储
- 支持向量 + 全文搜索

使用示例：
```python
from datapillar_oneagentic.storage.learning_stores import (
    LearningStore,
    LanceVectorStore,
)

vector_store = LanceVectorStore(path="./data/experience")
learning_store = LearningStore(vector_store=vector_store)
await learning_store.initialize()
```
"""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.storage.learning_stores.base import (
    VectorRecord,
    VectorSearchResult,
    VectorStore,
)

logger = logging.getLogger(__name__)

TABLE_NAME = "vectors"


class LanceVectorStore(VectorStore):
    """
    LanceDB 向量存储

    表结构：
    - id: str (主键)
    - text: str (用于全文搜索)
    - vector: list[float] (embedding)
    - metadata: str (JSON)
    """

    def __init__(self, *, path: str = "./data/vectors"):
        """
        初始化 LanceDB 存储

        Args:
            path: 数据库路径
        """
        self._path = path
        self._db = None
        self._table = None

    async def initialize(self) -> None:
        """初始化数据库和表"""
        import lancedb

        logger.info(f"初始化 LanceVectorStore: {self._path}")

        self._db = await lancedb.connect_async(self._path)

        table_names = await self._db.table_names()
        if TABLE_NAME in table_names:
            self._table = await self._db.open_table(TABLE_NAME)
            logger.info(f"打开已存在的表: {TABLE_NAME}")
        else:
            logger.info(f"表 {TABLE_NAME} 不存在，将在首次添加数据时创建")

        logger.info("LanceVectorStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        self._db = None
        self._table = None
        logger.info("LanceVectorStore 已关闭")

    def _record_to_row(self, record: VectorRecord) -> dict[str, Any]:
        """将 VectorRecord 转换为表行"""
        return {
            "id": record.id,
            "text": record.text,
            "vector": record.vector,
            "metadata": json.dumps(record.metadata, ensure_ascii=False),
        }

    def _row_to_record(self, row: dict[str, Any]) -> VectorRecord:
        """将表行转换为 VectorRecord"""
        return VectorRecord(
            id=row["id"],
            text=row.get("text", ""),
            vector=row.get("vector", []),
            metadata=json.loads(row.get("metadata", "{}")),
        )

    async def _ensure_table(self, first_row: dict[str, Any]) -> None:
        """确保表存在"""
        if self._table is None:
            import pyarrow as pa

            schema = pa.schema([
                pa.field("id", pa.string()),
                pa.field("text", pa.string()),
                pa.field("vector", pa.list_(pa.float32())),
                pa.field("metadata", pa.string()),
            ])

            self._table = await self._db.create_table(
                TABLE_NAME,
                data=[first_row],
                schema=schema,
            )
            logger.info(f"创建表: {TABLE_NAME}")

    # ==================== 写操作 ====================

    async def add(self, record: VectorRecord) -> str:
        """添加记录"""
        row = self._record_to_row(record)

        if self._table is None:
            await self._ensure_table(row)
        else:
            await self._table.add([row])

        logger.debug(f"添加记录: {record.id}")
        return record.id

    async def add_batch(self, records: list[VectorRecord]) -> list[str]:
        """批量添加记录"""
        if not records:
            return []

        rows = [self._record_to_row(r) for r in records]

        if self._table is None:
            await self._ensure_table(rows[0])
            if len(rows) > 1:
                await self._table.add(rows[1:])
        else:
            await self._table.add(rows)

        logger.info(f"批量添加 {len(records)} 条记录")
        return [r.id for r in records]

    async def update(self, record: VectorRecord) -> bool:
        """更新记录"""
        if self._table is None:
            return False

        await self.delete(record.id)
        await self.add(record)

        logger.debug(f"更新记录: {record.id}")
        return True

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        if self._table is None:
            return False

        await self._table.delete(f"id = '{record_id}'")
        logger.debug(f"删除记录: {record_id}")
        return True

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> VectorRecord | None:
        """获取记录"""
        if self._table is None:
            return None

        results = await self._table.query().where(
            f"id = '{record_id}'"
        ).limit(1).to_pandas()

        if results.empty:
            return None

        row = results.iloc[0].to_dict()
        return self._row_to_record(row)

    def _build_filter_sql(self, filter: dict[str, Any] | None) -> str | None:
        """构建过滤 SQL"""
        if not filter:
            return None

        conditions = []

        for key, value in filter.items():
            if isinstance(value, dict):
                for op, val in value.items():
                    if op == "$eq":
                        conditions.append(f"json_extract(metadata, '$.{key}') = '{val}'")
                    elif op == "$gte":
                        conditions.append(f"json_extract(metadata, '$.{key}') >= {val}")
                    elif op == "$lte":
                        conditions.append(f"json_extract(metadata, '$.{key}') <= {val}")
                    elif op == "$gt":
                        conditions.append(f"json_extract(metadata, '$.{key}') > {val}")
                    elif op == "$lt":
                        conditions.append(f"json_extract(metadata, '$.{key}') < {val}")
                    elif op == "$contains":
                        conditions.append(f"json_extract(metadata, '$.{key}') LIKE '%{val}%'")
            else:
                if isinstance(value, str):
                    conditions.append(f"json_extract(metadata, '$.{key}') = '{value}'")
                else:
                    conditions.append(f"json_extract(metadata, '$.{key}') = {value}")

        if not conditions:
            return None

        return " AND ".join(conditions)

    async def search_by_vector(
        self,
        vector: list[float],
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """向量相似度搜索"""
        if self._table is None:
            return []

        search_query = self._table.search(vector, query_type="vector")

        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            search_query = search_query.where(filter_sql)

        results_df = await search_query.limit(k).to_pandas()

        results = []
        for _, row in results_df.iterrows():
            row_dict = row.to_dict()
            distance = row_dict.get("_distance", 0)
            score = 1.0 / (1.0 + distance)
            metadata = json.loads(row_dict.get("metadata", "{}"))

            results.append(VectorSearchResult(
                id=row_dict["id"],
                score=score,
                distance=distance,
                metadata=metadata,
                text=row_dict.get("text", ""),
            ))

        return results

    async def search_by_text(
        self,
        query: str,
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """全文搜索"""
        if self._table is None:
            return []

        search_query = self._table.search(query, query_type="fts")

        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            search_query = search_query.where(filter_sql)

        results_df = await search_query.limit(k).to_pandas()

        results = []
        for _, row in results_df.iterrows():
            row_dict = row.to_dict()
            distance = row_dict.get("_distance", 0)
            score = 1.0 / (1.0 + distance)
            metadata = json.loads(row_dict.get("metadata", "{}"))

            results.append(VectorSearchResult(
                id=row_dict["id"],
                score=score,
                distance=distance,
                metadata=metadata,
                text=row_dict.get("text", ""),
            ))

        return results

    # ==================== 统计操作 ====================

    async def count(self, filter: dict[str, Any] | None = None) -> int:
        """统计记录数量"""
        if self._table is None:
            return 0

        query = self._table.query()

        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            query = query.where(filter_sql)

        results = await query.to_pandas()
        return len(results)

    async def distinct(self, field: str) -> list[Any]:
        """获取字段的去重值列表"""
        if self._table is None:
            return []

        results = await self._table.query().select(["metadata"]).to_pandas()

        values = set()
        for metadata_json in results["metadata"]:
            metadata = json.loads(metadata_json)
            if field in metadata:
                value = metadata[field]
                if isinstance(value, list):
                    values.update(value)
                else:
                    values.add(value)

        return list(values)
