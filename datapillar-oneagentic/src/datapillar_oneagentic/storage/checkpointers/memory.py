"""
MemoryCheckpointer - 内存 Checkpointer

开发和测试环境使用，进程重启后数据丢失。

使用示例：
```python
from datapillar_oneagentic.storage import MemoryCheckpointer

checkpointer = MemoryCheckpointer()
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from langgraph.checkpoint.memory import MemorySaver

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver

logger = logging.getLogger(__name__)


class MemoryCheckpointer:
    """
    内存 Checkpointer

    开发和测试环境使用，零配置。
    """

    def __init__(self):
        """初始化内存 Checkpointer"""
        self._saver = MemorySaver()
        logger.debug("初始化 MemoryCheckpointer")

    def get_saver(self) -> "BaseCheckpointSaver":
        """获取 LangGraph Checkpointer"""
        return self._saver

    async def delete_thread(self, thread_id: str) -> None:
        """删除线程（内存实现无需操作）"""
        pass

    async def close(self) -> None:
        """关闭（内存实现无需操作）"""
        pass
