"""
Todo 更新与持久化

Todo 仅保留在 state 中，不再维护历史或窗口。
"""

from __future__ import annotations

import logging

from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoPlanOp, TodoUpdate

logger = logging.getLogger(__name__)


async def apply_todo_updates(
    *,
    current_todo: dict,
    updates: list[TodoUpdate],
) -> dict | None:
    if not current_todo or not updates:
        return None

    try:
        todo_list = SessionTodoList.model_validate(current_todo)
    except Exception as exc:
        logger.warning(f"Todo 解析失败: {exc}")
        return None

    if todo_list.apply_updates(updates):
        return todo_list.model_dump(mode="json")
    return None


async def apply_todo_plan(
    *,
    session_id: str,
    current_todo: dict | None,
    ops: list[TodoPlanOp],
) -> dict | None:
    if not ops:
        return None

    if current_todo is None:
        todo_list = SessionTodoList(session_id=session_id)
    else:
        try:
            todo_list = SessionTodoList.model_validate(current_todo)
        except Exception as exc:
            logger.warning(f"Todo 解析失败: {exc}")
            return None

    if todo_list.apply_plan(ops):
        return todo_list.model_dump(mode="json")
    return None
