"""
Storage 模块 - 统一存储管理

提供三类存储，使用方分开选择：

1. Checkpointers（状态持久化）：
   - MemoryCheckpointer: 内存（开发/测试）
   - SqliteCheckpointer: SQLite（本地持久化）
   - PostgresCheckpointer: PostgreSQL（生产环境）
   - RedisCheckpointer: Redis（生产环境，支持分布式）

2. Deliverable Stores（Agent 交付物存储）：
   - InMemoryDeliverableStore: 内存（开发/测试）
   - PostgresDeliverableStore: PostgreSQL（生产环境）
   - RedisDeliverableStore: Redis（生产环境，支持分布式）

3. Learning Stores（经验学习向量存储）：
   - LanceVectorStore: LanceDB（默认，嵌入式）
   - ChromaVectorStore: Chroma（支持 local/remote）
   - MilvusVectorStore: Milvus（支持 local/remote）

使用示例：
```python
from datapillar_oneagentic import Datapillar
from datapillar_oneagentic.storage import (
    RedisCheckpointer,
    PostgresDeliverableStore,
    LanceVectorStore,
    ChromaVectorStore,
    MilvusVectorStore,
)

checkpointer = RedisCheckpointer(url="redis://localhost:6379")
deliverable_store = PostgresDeliverableStore(url="postgresql://...")

team = Datapillar(
    name="分析团队",
    agents=[...],
    checkpointer=checkpointer,
    deliverable_store=deliverable_store,
    # 选择其一：
    learning_store=LanceVectorStore(path="./data/experience"),
    # learning_store=ChromaVectorStore(path="./data/chroma"),
    # learning_store=ChromaVectorStore(host="localhost", port=8000),
    # learning_store=MilvusVectorStore(uri="./data/milvus.db"),
    # learning_store=MilvusVectorStore(uri="http://localhost:19530"),
    enable_learning=True,
)
```
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING

# Checkpointers
from datapillar_oneagentic.storage.checkpointers import (
    MemoryCheckpointer,
    SqliteCheckpointer,
    PostgresCheckpointer,
    RedisCheckpointer,
)

# Deliverable Stores
from datapillar_oneagentic.storage.deliverable_stores import (
    InMemoryDeliverableStore,
    PostgresDeliverableStore,
    RedisDeliverableStore,
)

# Learning Stores
from datapillar_oneagentic.storage.learning_stores import (
    VectorStore,
    LanceVectorStore,
    ChromaVectorStore,
    MilvusVectorStore,
)

if TYPE_CHECKING:
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


class StorageProvider:
    """
    存储提供者

    根据配置提供 Checkpointer 和 DeliverableStore。
    """

    def __init__(self):
        self._checkpointer: MemoryCheckpointer | None = None
        self._deliverable_store: InMemoryDeliverableStore | None = None

    @asynccontextmanager
    async def get_checkpointer(self):
        """获取 Checkpointer（上下文管理器）"""
        if self._checkpointer is None:
            self._checkpointer = MemoryCheckpointer()
        yield self._checkpointer.get_saver()

    def get_deliverable_store(self) -> "BaseStore":
        """获取 DeliverableStore"""
        if self._deliverable_store is None:
            self._deliverable_store = InMemoryDeliverableStore()
        return self._deliverable_store.get_store()

    async def delete_thread(self, thread_id: str) -> None:
        """删除线程（内存实现无需操作）"""
        pass

    async def close(self) -> None:
        """关闭存储"""
        if self._checkpointer:
            await self._checkpointer.close()
        if self._deliverable_store:
            await self._deliverable_store.close()


_storage_provider: StorageProvider | None = None


def get_storage_provider() -> StorageProvider:
    """获取全局存储提供者"""
    global _storage_provider
    if _storage_provider is None:
        _storage_provider = StorageProvider()
    return _storage_provider


def reset_storage_provider() -> None:
    """重置存储提供者"""
    global _storage_provider
    _storage_provider = None


__all__ = [
    # Checkpointers
    "MemoryCheckpointer",
    "SqliteCheckpointer",
    "PostgresCheckpointer",
    "RedisCheckpointer",
    # Deliverable Stores
    "InMemoryDeliverableStore",
    "PostgresDeliverableStore",
    "RedisDeliverableStore",
    # Learning Stores
    "VectorStore",
    "LanceVectorStore",
    "ChromaVectorStore",
    "MilvusVectorStore",
    # Storage Provider
    "StorageProvider",
    "get_storage_provider",
    "reset_storage_provider",
]
