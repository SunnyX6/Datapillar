"""
会话级记忆清理：删除 LangGraph checkpointer 的 thread。

注意：
- 这是"session 隔离"的权威清理动作：删掉 thread_id 对应的所有 checkpoint。
- 仅按 user_id + session_id 组合定位 thread_id（当前产品无 tenant 概念）。

实现：
- 使用 Checkpoint.delete_thread() 执行删除
"""

from src.infrastructure.repository.checkpoint import Checkpoint


async def clear_session_checkpoints(*, thread_id: str) -> None:
    """
    清理会话的所有 checkpoint

    Args:
        thread_id: thread ID（通常是 user_id + session_id 的组合）
    """
    await Checkpoint.delete_thread(thread_id)
