"""
RedisCheckpointer - Redis Checkpointer

生产环境推荐，支持分布式部署和 TTL。

依赖：pip install datapillar-oneagentic[redis]

使用示例：
```python
from datapillar_oneagentic.storage import RedisCheckpointer

checkpointer = RedisCheckpointer(url="redis://localhost:6379")

# 自定义 TTL（不传则用框架配置默认值）
checkpointer = RedisCheckpointer(url="redis://localhost:6379", ttl_minutes=60)
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver

logger = logging.getLogger(__name__)


class RedisCheckpointer:
    """
    Redis Checkpointer

    生产环境推荐，支持分布式部署和 TTL。
    """

    def __init__(self, url: str, *, ttl_minutes: float | None = None):
        """
        初始化 Redis Checkpointer

        Args:
            url: Redis 连接 URL（必须，如 redis://localhost:6379）
            ttl_minutes: TTL 分钟数（可选，不传则从框架配置读取默认值）
        """
        self._url = url

        # ttl 不传就从框架配置读默认值
        if ttl_minutes is None:
            from datapillar_oneagentic.config import datapillar
            ttl_minutes = datapillar.cache.checkpoint_ttl_seconds / 60.0

        self._ttl_minutes = ttl_minutes
        self._saver: "BaseCheckpointSaver | None" = None
        self._initialized = False
        logger.info(f"初始化 RedisCheckpointer, ttl={ttl_minutes}分钟")

    async def _ensure_initialized(self) -> None:
        """确保已初始化"""
        if self._initialized:
            return

        try:
            from langgraph.checkpoint.redis.aio import AsyncRedisSaver
        except ImportError:
            raise ImportError(
                "需要安装 Redis 依赖：pip install datapillar-oneagentic[redis]"
            )

        self._saver = AsyncRedisSaver.from_conn_string(self._url)
        if self._ttl_minutes and self._ttl_minutes > 0:
            self._saver.ttl = {"default": self._ttl_minutes}
        await self._saver.setup()
        self._initialized = True

    def get_saver(self) -> "BaseCheckpointSaver":
        """获取 LangGraph Checkpointer（需要先调用 _ensure_initialized）"""
        if not self._saver:
            from langgraph.checkpoint.redis import RedisSaver
            self._saver = RedisSaver.from_conn_string(self._url)
            if self._ttl_minutes and self._ttl_minutes > 0:
                self._saver.ttl = {"default": self._ttl_minutes}
        return self._saver

    async def get_saver_async(self) -> "BaseCheckpointSaver":
        """获取异步 LangGraph Checkpointer"""
        await self._ensure_initialized()
        return self._saver  # type: ignore

    async def delete_thread(self, thread_id: str) -> None:
        """删除线程"""
        await self._ensure_initialized()
        if self._saver and hasattr(self._saver, 'adelete_thread'):
            await self._saver.adelete_thread(thread_id)

    async def close(self) -> None:
        """关闭连接"""
        if self._saver and hasattr(self._saver, 'close'):
            await self._saver.close()
        self._initialized = False
