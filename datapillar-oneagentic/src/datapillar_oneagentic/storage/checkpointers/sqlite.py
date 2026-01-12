"""
SqliteCheckpointer - SQLite Checkpointer

本地持久化，适合单机部署和开发环境。

依赖：pip install datapillar-oneagentic[sqlite]

使用示例：
```python
from datapillar_oneagentic.storage import SqliteCheckpointer

checkpointer = SqliteCheckpointer(path="./checkpoint.db")

# 或使用内存数据库（测试用）
checkpointer = SqliteCheckpointer(path=":memory:")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver

logger = logging.getLogger(__name__)


class SqliteCheckpointer:
    """
    SQLite Checkpointer

    本地持久化，适合单机部署。
    """

    def __init__(self, path: str):
        """
        初始化 SQLite Checkpointer

        Args:
            path: 数据库路径（必须，如 ./checkpoint.db 或 :memory:）
        """
        self._path = path
        self._saver: "BaseCheckpointSaver | None" = None
        self._initialized = False
        logger.info(f"初始化 SqliteCheckpointer: {path}")

    async def _ensure_initialized(self) -> None:
        """确保已初始化"""
        if self._initialized:
            return

        try:
            from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
        except ImportError:
            raise ImportError(
                "需要安装 SQLite 依赖：pip install datapillar-oneagentic[sqlite]"
            )

        self._saver = AsyncSqliteSaver.from_conn_string(self._path)
        await self._saver.setup()
        self._initialized = True

    def get_saver(self) -> "BaseCheckpointSaver":
        """获取 LangGraph Checkpointer（需要先调用 _ensure_initialized）"""
        if not self._saver:
            # 同步场景使用同步版本
            from langgraph.checkpoint.sqlite import SqliteSaver
            self._saver = SqliteSaver.from_conn_string(self._path)
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
