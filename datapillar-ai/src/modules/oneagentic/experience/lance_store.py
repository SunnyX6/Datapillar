"""
LanceDB 经验存储实现

默认的向量数据库实现，特点：
- 嵌入式，无需额外服务
- 异步支持
- 高性能列式存储
- 支持向量 + 全文搜索

使用示例：
```python
from src.modules.oneagentic.experience import LanceExperienceStore

store = LanceExperienceStore(
    path="./data/experience",
    embedding_model="text-embedding-3-small",
)
await store.initialize()

# 存储经验
await store.add(episode)

# 检索
results = await store.search("创建用户宽表", k=5)
```
"""

from __future__ import annotations

import json
import logging
from typing import Any

from src.modules.oneagentic.experience.episode import Episode
from src.modules.oneagentic.experience.store import (
    ExperienceStore,
    SearchFilter,
    SearchResult,
)

logger = logging.getLogger(__name__)

# LanceDB 表名
TABLE_NAME = "experiences"


class LanceExperienceStore(ExperienceStore):
    """
    LanceDB 经验存储实现

    表结构：
    - episode_id: str (主键)
    - search_text: str (用于全文/向量搜索)
    - vector: list[float] (embedding)
    - outcome: str
    - task_type: str
    - agents: str (JSON array)
    - tags: str (JSON array)
    - user_id: str
    - user_satisfaction: float
    - created_at_ms: int
    - data: str (完整 Episode JSON)
    """

    def __init__(
        self,
        *,
        path: str = "./data/experience",
        embedding_func: Any | None = None,
    ):
        """
        初始化 LanceDB 存储

        Args:
            path: 数据库路径
            embedding_func: LanceDB embedding 函数（可选，不提供则需手动传入 embedding）
        """
        self._path = path
        self._embedding_func = embedding_func
        self._db = None
        self._table = None

    async def initialize(self) -> None:
        """初始化数据库和表"""
        import lancedb

        logger.info(f"初始化 LanceDB 经验存储: {self._path}")

        # 异步连接
        self._db = await lancedb.connect_async(self._path)

        # 检查表是否存在
        table_names = await self._db.table_names()

        if TABLE_NAME in table_names:
            self._table = await self._db.open_table(TABLE_NAME)
            logger.info(f"打开已存在的表: {TABLE_NAME}")
        else:
            # 创建表（需要初始数据）
            logger.info(f"表 {TABLE_NAME} 不存在，将在首次添加数据时创建")

        logger.info("LanceDB 经验存储初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        self._db = None
        self._table = None
        logger.info("LanceDB 经验存储已关闭")

    def _episode_to_row(self, episode: Episode) -> dict[str, Any]:
        """将 Episode 转换为表行"""
        return {
            "episode_id": episode.episode_id,
            "search_text": episode.to_search_text(),
            "vector": episode.goal_embedding or [],
            "outcome": episode.outcome.value,
            "task_type": episode.task_type,
            "agents": json.dumps(episode.agents_involved),
            "tags": json.dumps(episode.tags),
            "user_id": episode.user_id,
            "user_satisfaction": episode.user_satisfaction or 0.0,
            "created_at_ms": episode.created_at_ms,
            "goal": episode.goal,
            "data": episode.model_dump_json(),
        }

    def _row_to_episode(self, row: dict[str, Any]) -> Episode:
        """将表行转换为 Episode"""
        return Episode.model_validate_json(row["data"])

    async def _ensure_table(self, first_row: dict[str, Any]) -> None:
        """确保表存在"""
        if self._table is None:
            import pyarrow as pa

            # 定义 schema
            schema = pa.schema(
                [
                    pa.field("episode_id", pa.string()),
                    pa.field("search_text", pa.string()),
                    pa.field("vector", pa.list_(pa.float32())),
                    pa.field("outcome", pa.string()),
                    pa.field("task_type", pa.string()),
                    pa.field("agents", pa.string()),
                    pa.field("tags", pa.string()),
                    pa.field("user_id", pa.string()),
                    pa.field("user_satisfaction", pa.float32()),
                    pa.field("created_at_ms", pa.int64()),
                    pa.field("goal", pa.string()),
                    pa.field("data", pa.string()),
                ]
            )

            self._table = await self._db.create_table(
                TABLE_NAME,
                data=[first_row],
                schema=schema,
            )
            logger.info(f"创建表: {TABLE_NAME}")

    # ==================== 写操作 ====================

    async def add(self, episode: Episode) -> str:
        """添加经验"""
        row = self._episode_to_row(episode)

        if self._table is None:
            await self._ensure_table(row)
        else:
            await self._table.add([row])

        logger.debug(f"添加经验: {episode.episode_id}")
        return episode.episode_id

    async def add_batch(self, episodes: list[Episode]) -> list[str]:
        """批量添加经验"""
        if not episodes:
            return []

        rows = [self._episode_to_row(ep) for ep in episodes]

        if self._table is None:
            await self._ensure_table(rows[0])
            if len(rows) > 1:
                await self._table.add(rows[1:])
        else:
            await self._table.add(rows)

        logger.info(f"批量添加 {len(episodes)} 条经验")
        return [ep.episode_id for ep in episodes]

    async def update(self, episode: Episode) -> bool:
        """更新经验"""
        if self._table is None:
            return False

        # LanceDB 更新：先删除再添加
        await self.delete(episode.episode_id)
        await self.add(episode)

        logger.debug(f"更新经验: {episode.episode_id}")
        return True

    async def delete(self, episode_id: str) -> bool:
        """删除经验"""
        if self._table is None:
            return False

        await self._table.delete(f"episode_id = '{episode_id}'")
        logger.debug(f"删除经验: {episode_id}")
        return True

    # ==================== 读操作 ====================

    async def get(self, episode_id: str) -> Episode | None:
        """获取经验"""
        if self._table is None:
            return None

        results = (
            await self._table.query().where(f"episode_id = '{episode_id}'").limit(1).to_pandas()
        )

        if results.empty:
            return None

        row = results.iloc[0].to_dict()
        return self._row_to_episode(row)

    def _build_filter_sql(self, filter: SearchFilter | None) -> str | None:
        """构建过滤 SQL"""
        if filter is None:
            return None

        conditions = []

        if filter.outcome:
            conditions.append(f"outcome = '{filter.outcome.value}'")

        if filter.task_type:
            conditions.append(f"task_type = '{filter.task_type}'")

        if filter.user_id:
            conditions.append(f"user_id = '{filter.user_id}'")

        if filter.min_satisfaction is not None:
            conditions.append(f"user_satisfaction >= {filter.min_satisfaction}")

        if filter.created_after_ms is not None:
            conditions.append(f"created_at_ms >= {filter.created_after_ms}")

        if filter.created_before_ms is not None:
            conditions.append(f"created_at_ms <= {filter.created_before_ms}")

        # agents 和 tags 需要 JSON 包含查询
        # LanceDB 目前不直接支持，这里简化处理
        if filter.agents:
            for agent in filter.agents:
                conditions.append(f"agents LIKE '%\"{agent}\"%'")

        if filter.tags:
            for tag in filter.tags:
                conditions.append(f"tags LIKE '%\"{tag}\"%'")

        if not conditions:
            return None

        return " AND ".join(conditions)

    async def search(
        self,
        query: str,
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        """文本检索相似经验"""
        if self._table is None:
            return []

        # 构建查询
        search_query = self._table.search(query, query_type="fts")

        # 添加过滤条件
        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            search_query = search_query.where(filter_sql)

        # 执行查询
        results_df = await search_query.limit(k).to_pandas()

        # 转换结果
        results = []
        for _, row in results_df.iterrows():
            episode = self._row_to_episode(row.to_dict())
            score = 1.0 / (1.0 + row.get("_distance", 0))  # 距离转相似度
            results.append(
                SearchResult(
                    episode=episode,
                    score=score,
                    distance=row.get("_distance"),
                )
            )

        return results

    async def search_by_embedding(
        self,
        embedding: list[float],
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        """向量检索相似经验"""
        if self._table is None:
            return []

        # 构建向量查询
        search_query = self._table.search(embedding, query_type="vector")

        # 添加过滤条件
        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            search_query = search_query.where(filter_sql)

        # 执行查询
        results_df = await search_query.limit(k).to_pandas()

        # 转换结果
        results = []
        for _, row in results_df.iterrows():
            episode = self._row_to_episode(row.to_dict())
            distance = row.get("_distance", 0)
            score = 1.0 / (1.0 + distance)
            results.append(
                SearchResult(
                    episode=episode,
                    score=score,
                    distance=distance,
                )
            )

        return results

    # ==================== 统计操作 ====================

    async def count(self, filter: SearchFilter | None = None) -> int:
        """统计经验数量"""
        if self._table is None:
            return 0

        query = self._table.query()

        filter_sql = self._build_filter_sql(filter)
        if filter_sql:
            query = query.where(filter_sql)

        results = await query.to_pandas()
        return len(results)

    async def list_task_types(self) -> list[str]:
        """列出所有任务类型"""
        if self._table is None:
            return []

        results = await self._table.query().select(["task_type"]).to_pandas()
        return results["task_type"].unique().tolist()

    async def list_tags(self) -> list[str]:
        """列出所有标签"""
        if self._table is None:
            return []

        results = await self._table.query().select(["tags"]).to_pandas()

        all_tags = set()
        for tags_json in results["tags"]:
            tags = json.loads(tags_json)
            all_tags.update(tags)

        return list(all_tags)
