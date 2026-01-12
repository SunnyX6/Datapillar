"""
PostgresDeliverableStore - PostgreSQL 交付物存储

生产环境推荐，支持持久化和高可用。

依赖：pip install datapillar-oneagentic[postgres]

使用示例：
```python
from datapillar_oneagentic.storage import PostgresDeliverableStore

store = PostgresDeliverableStore(url="postgresql://user:pass@localhost/db")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


class PostgresDeliverableStore:
    """
    PostgreSQL 交付物存储

    生产环境推荐，支持持久化和高可用。
    """

    def __init__(self, url: str):
        """
        初始化 PostgreSQL 交付物存储

        Args:
            url: PostgreSQL 连接 URL（必须，如 postgresql://user:pass@localhost/db）
        """
        self._url = url
        self._store: "BaseStore | None" = None
        self._initialized = False
        logger.info("初始化 PostgresDeliverableStore")

    async def _ensure_initialized(self) -> None:
        """确保已初始化"""
        if self._initialized:
            return

        try:
            from langgraph.store.postgres import AsyncPostgresStore
        except ImportError:
            raise ImportError(
                "需要安装 PostgreSQL 依赖：pip install datapillar-oneagentic[postgres]"
            )

        self._store = AsyncPostgresStore.from_conn_string(self._url)
        await self._store.setup()
        self._initialized = True

    def get_store(self) -> "BaseStore":
        """获取 LangGraph Store（需要先调用 _ensure_initialized）"""
        if not self._store:
            from langgraph.store.postgres import PostgresStore as LangGraphPostgresStore
            self._store = LangGraphPostgresStore.from_conn_string(self._url)
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
