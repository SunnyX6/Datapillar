"""
ExperienceStore 抽象接口

定义经验存储的标准接口，支持多种后端实现：
- LanceDB (默认)
- ChromaDB
- Milvus
- 其他向量数据库

设计原则：
- 异步接口
- 支持向量检索 + 过滤条件
- 支持批量操作
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod

from src.modules.oneagentic.experience.episode import Episode, Outcome

logger = logging.getLogger(__name__)


class SearchResult:
    """检索结果"""

    def __init__(
        self,
        episode: Episode,
        score: float,
        distance: float | None = None,
    ):
        self.episode = episode
        self.score = score  # 相似度得分 0-1
        self.distance = distance  # 向量距离


class SearchFilter:
    """检索过滤条件"""

    def __init__(
        self,
        *,
        team_id: str | None = None,
        outcome: Outcome | None = None,
        task_type: str | None = None,
        agents: list[str] | None = None,
        tags: list[str] | None = None,
        user_id: str | None = None,
        min_satisfaction: float | None = None,
        created_after_ms: int | None = None,
        created_before_ms: int | None = None,
    ):
        self.team_id = team_id
        self.outcome = outcome
        self.task_type = task_type
        self.agents = agents
        self.tags = tags
        self.user_id = user_id
        self.min_satisfaction = min_satisfaction
        self.created_after_ms = created_after_ms
        self.created_before_ms = created_before_ms


class ExperienceStore(ABC):
    """
    经验存储抽象接口

    使用示例：
    ```python
    # 使用默认 LanceDB 实现
    store = LanceExperienceStore(path="./data/experience")

    # 存储经验
    await store.add(episode)

    # 检索相似经验
    results = await store.search(
        query="创建用户宽表",
        k=5,
        filter=SearchFilter(outcome=Outcome.SUCCESS),
    )

    # 批量检索
    results = await store.search_by_embedding(embedding, k=5)
    ```
    """

    @abstractmethod
    async def initialize(self) -> None:
        """
        初始化存储

        创建表/索引等。
        """
        pass

    @abstractmethod
    async def close(self) -> None:
        """关闭存储连接"""
        pass

    # ==================== 写操作 ====================

    @abstractmethod
    async def add(self, episode: Episode) -> str:
        """
        添加经验

        Args:
            episode: 经验片段

        Returns:
            episode_id
        """
        pass

    @abstractmethod
    async def add_batch(self, episodes: list[Episode]) -> list[str]:
        """
        批量添加经验

        Args:
            episodes: 经验列表

        Returns:
            episode_id 列表
        """
        pass

    @abstractmethod
    async def update(self, episode: Episode) -> bool:
        """
        更新经验

        Args:
            episode: 经验片段（必须包含 episode_id）

        Returns:
            是否成功
        """
        pass

    @abstractmethod
    async def delete(self, episode_id: str) -> bool:
        """
        删除经验

        Args:
            episode_id: 经验 ID

        Returns:
            是否成功
        """
        pass

    # ==================== 读操作 ====================

    @abstractmethod
    async def get(self, episode_id: str) -> Episode | None:
        """
        获取经验

        Args:
            episode_id: 经验 ID

        Returns:
            经验片段，不存在返回 None
        """
        pass

    @abstractmethod
    async def search(
        self,
        query: str,
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        """
        文本检索相似经验

        Args:
            query: 查询文本
            k: 返回数量
            filter: 过滤条件

        Returns:
            检索结果列表（按相似度排序）
        """
        pass

    @abstractmethod
    async def search_by_embedding(
        self,
        embedding: list[float],
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        """
        向量检索相似经验

        Args:
            embedding: 查询向量
            k: 返回数量
            filter: 过滤条件

        Returns:
            检索结果列表
        """
        pass

    # ==================== 统计操作 ====================

    @abstractmethod
    async def count(self, filter: SearchFilter | None = None) -> int:
        """
        统计经验数量

        Args:
            filter: 过滤条件

        Returns:
            数量
        """
        pass

    @abstractmethod
    async def list_task_types(self) -> list[str]:
        """列出所有任务类型"""
        pass

    @abstractmethod
    async def list_tags(self) -> list[str]:
        """列出所有标签"""
        pass

    # ==================== 分析操作 ====================

    async def get_success_rate(
        self,
        task_type: str | None = None,
        agent_id: str | None = None,
    ) -> float:
        """
        获取成功率

        Args:
            task_type: 任务类型
            agent_id: Agent ID

        Returns:
            成功率 0-1
        """
        filter_success = SearchFilter(
            outcome=Outcome.SUCCESS,
            task_type=task_type,
            agents=[agent_id] if agent_id else None,
        )
        filter_all = SearchFilter(
            task_type=task_type,
            agents=[agent_id] if agent_id else None,
        )

        success_count = await self.count(filter_success)
        total_count = await self.count(filter_all)

        if total_count == 0:
            return 0.0

        return success_count / total_count

    async def get_common_failure_reasons(
        self,
        task_type: str | None = None,
        limit: int = 10,
    ) -> list[tuple[str, int]]:
        """
        获取常见失败原因

        Args:
            task_type: 任务类型
            limit: 返回数量

        Returns:
            [(原因, 次数), ...]
        """
        # 默认实现：子类可覆盖以提供更高效的实现
        filter = SearchFilter(outcome=Outcome.FAILURE, task_type=task_type)

        # 获取失败的经验
        results = await self.search("", k=100, filter=filter)

        # 统计失败原因
        reason_counts: dict[str, int] = {}
        for result in results:
            for reason in result.episode.failure_reasons:
                reason_counts[reason] = reason_counts.get(reason, 0) + 1

        # 排序并返回
        sorted_reasons = sorted(reason_counts.items(), key=lambda x: x[1], reverse=True)
        return sorted_reasons[:limit]
