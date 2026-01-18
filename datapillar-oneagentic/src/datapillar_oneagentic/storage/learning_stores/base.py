"""
ExperienceStore 抽象接口

所有向量数据库实现此接口，数据结构统一使用 ExperienceRecord。

实现类：
- VectorExperienceStore（基于 VectorStore 的统一实现）

使用示例：
```python
from datapillar_oneagentic.storage import create_learning_store
from datapillar_oneagentic.storage.config import VectorStoreConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

embedding_config = EmbeddingConfig(model="text-embedding-3-small", api_key="sk-xxx")
store = create_learning_store(
    namespace="my_app",
    vector_store_config=VectorStoreConfig(type="lance", path="./data/vectors"),
    embedding_config=embedding_config,
)
```
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord

logger = logging.getLogger(__name__)


class ExperienceStore(ABC):
    """
    经验存储抽象接口

    所有向量数据库实现此接口，数据结构统一使用 ExperienceRecord。
    切换向量库实现时，业务代码无需改动。
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
    async def add(self, record: ExperienceRecord) -> str:
        """
        添加经验记录

        Args:
            record: 经验记录（必须包含 vector）

        Returns:
            记录 ID
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
    async def get(self, record_id: str) -> ExperienceRecord | None:
        """
        获取记录

        Args:
            record_id: 记录 ID

        Returns:
            经验记录，不存在返回 None
        """
        pass

    @abstractmethod
    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        """
        向量相似度搜索

        Args:
            query_vector: 查询向量
            k: 返回数量
            outcome: 过滤条件（success / failure / None=全部）

        Returns:
            经验记录列表（按相似度排序）
        """
        pass

    # ==================== 统计操作 ====================

    @abstractmethod
    async def count(self) -> int:
        """
        统计记录数量

        Returns:
            数量
        """
        pass
