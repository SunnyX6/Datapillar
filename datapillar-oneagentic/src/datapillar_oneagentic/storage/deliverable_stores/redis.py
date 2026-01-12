"""
RedisDeliverableStore - Redis 交付物存储

生产环境推荐，支持分布式部署。

依赖：pip install datapillar-oneagentic[redis]

使用示例：
```python
from datapillar_oneagentic.storage import RedisDeliverableStore

store = RedisDeliverableStore(url="redis://localhost:6379")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


class RedisDeliverableStore:
    """
    Redis 交付物存储

    生产环境推荐，支持分布式部署。
    """

    def __init__(self, url: str):
        """
        初始化 Redis 交付物存储

        Args:
            url: Redis 连接 URL（必须，如 redis://localhost:6379）
        """
        self._url = url
        self._store: "BaseStore | None" = None
        self._initialized = False
        logger.info("初始化 RedisDeliverableStore")

    async def _ensure_initialized(self) -> None:
        """确保已初始化"""
        if self._initialized:
            return

        try:
            from langgraph.store.redis.aio import AsyncRedisStore
        except ImportError:
            raise ImportError(
                "需要安装 Redis 依赖：pip install datapillar-oneagentic[redis]"
            )

        self._store = AsyncRedisStore.from_conn_string(self._url)
        await self._store.setup()
        self._initialized = True

    def get_store(self) -> "BaseStore":
        """获取 LangGraph Store（需要先调用 _ensure_initialized）"""
        if not self._store:
            from langgraph.store.redis import RedisStore as LangGraphRedisStore
            self._store = LangGraphRedisStore.from_conn_string(self._url)
        return self._store

    async def get_store_async(self) -> "BaseStore":
        """获取异步 LangGraph Store"""
        await self._ensure_initialized()
        return self._store  # type: ignore

    async def close(self) -> None:
        """关闭连接"""
        if self._store and hasattr(self._store, 'close'):
            await self._store.close()
        self._initialized = False
