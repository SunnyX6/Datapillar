"""
会话级记忆清理：删除 LangGraph checkpointer 的 thread。

注意：
- 这是“session 隔离”的权威清理动作：删掉 thread_id 对应的所有 checkpoint。
- 仅按 user_id + session_id 组合定位 thread_id（当前产品无 tenant 概念）。
"""

from __future__ import annotations

from typing import Any


async def clear_session_checkpoints(*, checkpointer: Any, thread_id: str) -> None:
    adelete = getattr(checkpointer, "adelete_thread", None)
    if callable(adelete):
        await adelete(thread_id)
        return

    delete = getattr(checkpointer, "delete_thread", None)
    if callable(delete):
        delete(thread_id)
        return

    raise RuntimeError("checkpointer 不支持 delete_thread/adelete_thread，无法清理会话")
