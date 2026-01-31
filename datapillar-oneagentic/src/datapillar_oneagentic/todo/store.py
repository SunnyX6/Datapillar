# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Todo updates and persistence.

Todo is stored only in state; no history or windowing is maintained.
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
        logger.warning(f"Todo parse failed: {exc}")
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
            logger.warning(f"Todo parse failed: {exc}")
            return None

    if todo_list.apply_plan(ops):
        return todo_list.model_dump(mode="json")
    return None
