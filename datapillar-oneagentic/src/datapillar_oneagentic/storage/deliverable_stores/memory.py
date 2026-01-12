"""
InMemoryDeliverableStore - 内存交付物存储

开发和测试环境使用，进程重启后数据丢失。

使用示例：
```python
from datapillar_oneagentic.storage import InMemoryDeliverableStore

store = InMemoryDeliverableStore()
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from langgraph.store.memory import InMemoryStore as LangGraphInMemoryStore

if TYPE_CHECKING:
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


class InMemoryDeliverableStore:
    """
    内存交付物存储

    开发和测试环境使用，零配置。
    """

    def __init__(self):
        """初始化内存交付物存储"""
        self._store = LangGraphInMemoryStore()
        logger.debug("初始化 InMemoryDeliverableStore")

    def get_store(self) -> "BaseStore":
        """获取 LangGraph Store"""
        return self._store

    async def close(self) -> None:
        """关闭（内存实现无需操作）"""
        pass
