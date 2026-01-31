# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Todo reporting tools.

Used by agents to report Todo progress; the framework parses automatically.
"""

from __future__ import annotations

import ast
import json
from typing import Annotated

from langchain_core.tools import BaseTool, tool

from datapillar_oneagentic.todo.session_todo import TodoPlanOp, TodoUpdate
from datapillar_oneagentic.messages import Message, Messages

TODO_TOOL_NAME = "report_todo"
TODO_PLAN_TOOL_NAME = "plan_todo"


@tool(TODO_TOOL_NAME, description="Report team todo progress (internal use).")
def report_todo(
    todo_id: Annotated[str, "Todo ID (t1, t2, ...)"],
    status: Annotated[str, "Status: pending/running/completed/failed/skipped"],
    result: Annotated[str | None, "Short result note (optional)"] = None,
) -> dict:
    """Report Todo progress."""
    return {
        "id": todo_id,
        "status": status,
        "result": result,
    }

@tool(TODO_PLAN_TOOL_NAME, description="Plan or adjust team todo (internal use).")
def plan_todo(
    op: Annotated[str, "Operation: add/remove/replace"],
    items: Annotated[list[str] | None, "Items for add/replace"] = None,
    todo_ids: Annotated[list[str] | None, "Todo IDs for remove"] = None,
    goal: Annotated[str | None, "Optional goal"] = None,
) -> dict:
    """Plan or adjust Todo list."""
    return {
        "op": op,
        "items": items or [],
        "todo_ids": todo_ids or [],
        "goal": goal,
    }


def create_todo_tools() -> list[BaseTool]:
    """Create Todo tools."""
    return [report_todo, plan_todo]


def extract_todo_updates(messages: Messages) -> list[TodoUpdate]:
    """Parse Todo updates from messages."""
    updates: list[TodoUpdate] = []
    for msg in messages:
        if msg.role != "tool":
            continue
        if msg.name != TODO_TOOL_NAME:
            continue
        updates.extend(_parse_tool_payload(msg.content))
    return updates


def extract_todo_plan(messages: Messages) -> list[TodoPlanOp]:
    """Parse Todo plan operations from messages."""
    ops: list[TodoPlanOp] = []
    for msg in messages:
        if msg.role != "tool":
            continue
        if msg.name != TODO_PLAN_TOOL_NAME:
            continue
        ops.extend(_parse_plan_payload(msg.content))
    return ops


def build_todo_message(updates: list[TodoUpdate]) -> Message:
    """Build a Todo tool message (audit fallback)."""
    payload = {"updates": [u.model_dump(mode="json") for u in updates]}
    content = json.dumps(payload, ensure_ascii=False)
    return Message.tool(
        content=content,
        name=TODO_TOOL_NAME,
        tool_call_id="todo_audit",
    )


def _parse_tool_payload(payload: object) -> list[TodoUpdate]:
    """Parse tool payload into update list."""
    data: object = payload
    if isinstance(payload, str):
        try:
            data = json.loads(payload)
        except Exception:
            try:
                data = ast.literal_eval(payload)
            except Exception:
                return []

    if isinstance(data, list):
        return _parse_update_items(data)

    if isinstance(data, dict):
        if "updates" in data and isinstance(data["updates"], list):
            return _parse_update_items(data["updates"])
        return _parse_update_items([data])

    return []


def _parse_update_items(items: list[object]) -> list[TodoUpdate]:
    updates: list[TodoUpdate] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        try:
            updates.append(TodoUpdate.model_validate(item))
        except Exception:
            continue
    return updates


def _parse_plan_payload(payload: object) -> list[TodoPlanOp]:
    """Parse tool payload into plan operations."""
    data: object = payload
    if isinstance(payload, str):
        try:
            data = json.loads(payload)
        except Exception:
            try:
                data = ast.literal_eval(payload)
            except Exception:
                return []

    if isinstance(data, list):
        return _parse_plan_items(data)

    if isinstance(data, dict):
        if "ops" in data and isinstance(data["ops"], list):
            return _parse_plan_items(data["ops"])
        return _parse_plan_items([data])

    return []


def _parse_plan_items(items: list[object]) -> list[TodoPlanOp]:
    ops: list[TodoPlanOp] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        try:
            ops.append(TodoPlanOp.model_validate(item))
        except Exception:
            continue
    return ops
