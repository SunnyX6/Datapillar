"""
PostgresCheckpointer - PostgreSQL Checkpointer

生产环境推荐，支持持久化和高可用。

依赖：pip install datapillar-oneagentic[postgres]

使用示例：
```python
from datapillar_oneagentic.storage import PostgresCheckpointer

checkpointer = PostgresCheckpointer(url="postgresql://user:pass@localhost/db")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver

logger = logging.getLogger(__name__)


class PostgresCheckpointer:
    """
    PostgreSQL Checkpointer

    生产环境推荐，支持持久化和高可用。
    """

    def __init__(self, url: str):
        """
        初始化 PostgreSQL Checkpointer

        Args:
            url: PostgreSQL 连接 URL（必须，如 postgresql://user:pass@localhost/db）
        """
        self._url = url
        self._saver: "BaseCheckpointSaver | None" = None
        self._initialized = False
        logger.info(f"初始化 PostgresCheckpointer")

    async def _ensure_initialized(self) -> None:
        """确保已初始化"""
        if self._initialized:
            return

        try:
            from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        except ImportError:
            raise ImportError(
                "需要安装 PostgreSQL 依赖：pip install datapillar-oneagentic[postgres]"
            )

        self._saver = AsyncPostgresSaver.from_conn_string(self._url)
        await self._saver.setup()
        self._initialized = True

    def get_saver(self) -> "BaseCheckpointSaver":
        """获取 LangGraph Checkpointer（需要先调用 _ensure_initialized）"""
        if not self._saver:
            from langgraph.checkpoint.postgres import PostgresSaver
            self._saver = PostgresSaver.from_conn_string(self._url)
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
