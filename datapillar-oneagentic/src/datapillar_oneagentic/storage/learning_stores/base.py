"""
VectorStore 抽象接口 + 通用数据结构

VectorStore 是纯向量存储层，不关心业务逻辑：
- LanceVectorStore (默认，嵌入式)
- ChromaLocalVectorStore (嵌入式)
- ChromaRemoteVectorStore (远程服务)
- MilvusLocalVectorStore (Milvus Lite，嵌入式)
- MilvusRemoteVectorStore (远程服务)

LearningStore 是业务层，负责 Episode 序列化和统计分析。
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

logger = logging.getLogger(__name__)


@dataclass
class VectorRecord:
    """向量存储记录"""

    id: str
    vector: list[float]
    text: str  # 用于全文搜索
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class VectorSearchResult:
    """向量搜索结果"""

    id: str
    score: float  # 相似度得分 0-1
    distance: float | None  # 向量距离
    metadata: dict[str, Any] = field(default_factory=dict)
    text: str = ""


class VectorStore(ABC):
    """
    向量存储抽象接口

    纯存储层，不关心业务逻辑。提供统一的向量 CRUD 和搜索接口。

    使用示例：
    ```python
    from datapillar_oneagentic.storage.learning_stores import (
        LearningStore,
        LanceVectorStore,
    )

    vector_store = LanceVectorStore(path="./data/experience")
    learning_store = LearningStore(vector_store=vector_store)
    ```
    """

    @abstractmethod
    async def initialize(self) -> None:
        """初始化存储（创建表/索引等）"""
        pass

    @abstractmethod
    async def close(self) -> None:
        """关闭存储连接"""
        pass

    # ==================== 写操作 ====================

    @abstractmethod
    async def add(self, record: VectorRecord) -> str:
        """
        添加记录

        Args:
            record: 向量记录

        Returns:
            记录 ID
        """
        pass

    @abstractmethod
    async def add_batch(self, records: list[VectorRecord]) -> list[str]:
        """
        批量添加记录

        Args:
            records: 向量记录列表

        Returns:
            记录 ID 列表
        """
        pass

    @abstractmethod
    async def update(self, record: VectorRecord) -> bool:
        """
        更新记录

        Args:
            record: 向量记录（必须包含 id）

        Returns:
            是否成功
        """
        pass

    @abstractmethod
    async def delete(self, record_id: str) -> bool:
        """
        删除记录

        Args:
            record_id: 记录 ID

        Returns:
            是否成功
        """
        pass

    # ==================== 读操作 ====================

    @abstractmethod
    async def get(self, record_id: str) -> VectorRecord | None:
        """
        获取记录

        Args:
            record_id: 记录 ID

        Returns:
            向量记录，不存在返回 None
        """
        pass

    @abstractmethod
    async def search_by_vector(
        self,
        vector: list[float],
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """
        向量相似度搜索

        Args:
            vector: 查询向量
            k: 返回数量
            filter: 元数据过滤条件，格式：{"field": value} 或 {"field": {"$gte": value}}

        Returns:
            搜索结果列表（按相似度排序）
        """
        pass

    @abstractmethod
    async def search_by_text(
        self,
        query: str,
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """
        全文搜索

        Args:
            query: 查询文本
            k: 返回数量
            filter: 元数据过滤条件

        Returns:
            搜索结果列表
        """
        pass

    # ==================== 统计操作 ====================

    @abstractmethod
    async def count(self, filter: dict[str, Any] | None = None) -> int:
        """
        统计记录数量

        Args:
            filter: 过滤条件

        Returns:
            数量
        """
        pass

    @abstractmethod
    async def distinct(self, field: str) -> list[Any]:
        """
        获取字段的去重值列表

        Args:
            field: 字段名

        Returns:
            去重值列表
        """
        pass
