"""
LanceExperienceStore - LanceDB 实现

默认的向量数据库实现，特点：
- 嵌入式，无需额外服务
- 异步支持
- 高性能列式存储
- 支持向量相似度搜索

表结构直接对应 ExperienceRecord 的字段，所有字段都是独立列，支持直接过滤。
"""

from __future__ import annotations

import json
import logging
from typing import Any, TYPE_CHECKING

from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord

logger = logging.getLogger(__name__)

TABLE_NAME = "experiences"


class LanceExperienceStore(ExperienceStore):
    """
    LanceDB 经验存储实现

    表结构直接对应 ExperienceRecord：
    - id: str (主键)
    - namespace: str
    - session_id: str
    - goal: str
    - outcome: str
    - result_summary: str
    - tools_used: str (JSON)
    - agents_involved: str (JSON)
    - duration_ms: int
    - feedback: str (JSON)
    - created_at: int
    - vector: list[float] (embedding)
    """

    def __init__(
        self,
        *,
        path: str = "./data/experience",
        namespace: str = "default",
    ):
        """
        初始化 LanceDB 存储

        Args:
            path: 数据库基础路径
            namespace: 命名空间（用于隔离经验数据）
        """
        import os

        self._base_path = path
        self._namespace = namespace
        self._path = os.path.join(path, namespace)
        self._db = None
        self._table = None

    async def initialize(self) -> None:
        """初始化数据库和表"""
        import lancedb

        logger.info(f"初始化 LanceExperienceStore: {self._path}")

        self._db = await lancedb.connect_async(self._path)

        table_names = await self._db.list_tables()
        if TABLE_NAME in table_names:
            self._table = await self._db.open_table(TABLE_NAME)
            logger.info(f"打开已存在的表: {TABLE_NAME}")
        else:
            logger.info(f"表 {TABLE_NAME} 不存在，将在首次添加数据时创建")

        logger.info("LanceExperienceStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        self._db = None
        self._table = None
        logger.info("LanceExperienceStore 已关闭")

    def _record_to_row(self, record: "ExperienceRecord") -> dict[str, Any]:
        """将 ExperienceRecord 转换为表行"""
        return {
            "id": record.id,
            "namespace": record.namespace,
            "session_id": record.session_id,
            "goal": record.goal,
            "outcome": record.outcome,
            "result_summary": record.result_summary,
            "tools_used": json.dumps(record.tools_used, ensure_ascii=False),
            "agents_involved": json.dumps(record.agents_involved, ensure_ascii=False),
            "duration_ms": record.duration_ms,
            "feedback": json.dumps(record.feedback, ensure_ascii=False),
            "created_at": record.created_at,
            "vector": record.vector,
        }

    def _row_to_record(self, row: dict[str, Any]) -> "ExperienceRecord":
        """将表行转换为 ExperienceRecord"""
        from datapillar_oneagentic.experience.learner import ExperienceRecord

        return ExperienceRecord(
            id=row["id"],
            namespace=row.get("namespace", ""),
            session_id=row.get("session_id", ""),
            goal=row.get("goal", ""),
            outcome=row.get("outcome", "pending"),
            result_summary=row.get("result_summary", ""),
            tools_used=json.loads(row.get("tools_used", "[]")),
            agents_involved=json.loads(row.get("agents_involved", "[]")),
            duration_ms=row.get("duration_ms", 0),
            feedback=json.loads(row.get("feedback", "{}")),
            created_at=row.get("created_at", 0),
            vector=row.get("vector", []),
        )

    async def _ensure_table(self, first_row: dict[str, Any]) -> None:
        """确保表存在"""
        # 确保数据库已初始化
        if self._db is None:
            await self.initialize()

        if self._table is None:
            import pyarrow as pa

            from datapillar_oneagentic.config import datapillar
            dimension = datapillar.embedding.dimension

            schema = pa.schema([
                pa.field("id", pa.string()),
                pa.field("namespace", pa.string()),
                pa.field("session_id", pa.string()),
                pa.field("goal", pa.string()),
                pa.field("outcome", pa.string()),
                pa.field("result_summary", pa.string()),
                pa.field("tools_used", pa.string()),
                pa.field("agents_involved", pa.string()),
                pa.field("duration_ms", pa.int64()),
                pa.field("feedback", pa.string()),
                pa.field("created_at", pa.int64()),
                pa.field("vector", pa.list_(pa.float32(), list_size=dimension)),
            ])

            self._table = await self._db.create_table(
                TABLE_NAME,
                data=[first_row],
                schema=schema,
            )
            logger.info(f"创建表: {TABLE_NAME}, 向量维度: {dimension}")

    # ==================== 写操作 ====================

    async def add(self, record: "ExperienceRecord") -> str:
        """添加记录"""
        row = self._record_to_row(record)

        if self._table is None:
            await self._ensure_table(row)
        else:
            await self._table.add([row])

        return record.id

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        if self._table is None:
            return False

        from lancedb.util import value_to_sql

        safe_id = value_to_sql(record_id)
        await self._table.delete(f"id = {safe_id}")
        return True

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> "ExperienceRecord | None":
        """获取记录"""
        if self._table is None:
            return None

        from lancedb.util import value_to_sql

        safe_id = value_to_sql(record_id)
        results = await self._table.query().where(
            f"id = {safe_id}"
        ).limit(1).to_list()

        if not results:
            return None

        return self._row_to_record(results[0])

    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list["ExperienceRecord"]:
        """
        向量相似度搜索

        Args:
            query_vector: 查询向量
            k: 返回数量
            outcome: 过滤条件（success / failure / None=全部）

        Returns:
            ExperienceRecord 列表（按相似度排序）
        """
        if self._table is None:
            return []

        search_query = await self._table.search(query_vector, query_type="vector")

        # 直接用 outcome 列过滤
        if outcome:
            from lancedb.util import value_to_sql
            safe_outcome = value_to_sql(outcome)
            search_query = search_query.where(f"outcome = {safe_outcome}")

        # 使用 to_list() 代替 to_pandas()，不需要 pandas 依赖
        results = await search_query.limit(k).to_list()

        records = []
        for row in results:
            records.append(self._row_to_record(row))

        return records

    # ==================== 统计操作 ====================

    async def count(self) -> int:
        """统计记录数量"""
        if self._table is None:
            return 0

        return await self._table.count_rows()
