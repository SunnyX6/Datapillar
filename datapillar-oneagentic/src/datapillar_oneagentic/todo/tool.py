"""
Todo 上报工具

用于 Agent 上报 Todo 进度，框架自动解析。
"""

from __future__ import annotations

import ast
import json
from typing import Annotated

from langchain_core.messages import BaseMessage, ToolMessage
from langchain_core.tools import BaseTool, tool

from datapillar_oneagentic.todo.session_todo import TodoPlanOp, TodoUpdate

TODO_TOOL_NAME = "report_todo"
TODO_PLAN_TOOL_NAME = "plan_todo"


@tool(TODO_TOOL_NAME, description="Report team todo progress (internal use).")
def report_todo(
    todo_id: Annotated[str, "Todo ID (t1, t2, ...)"],
    status: Annotated[str, "Status: pending/running/completed/failed/skipped"],
    result: Annotated[str | None, "Short result note (optional)"] = None,
) -> dict:
    """上报 Todo 进度"""
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
    """规划 Todo 列表"""
    return {
        "op": op,
        "items": items or [],
        "todo_ids": todo_ids or [],
        "goal": goal,
    }


def create_todo_tools() -> list[BaseTool]:
    """创建 Todo 工具列表"""
    return [report_todo, plan_todo]


def extract_todo_updates(messages: list[BaseMessage]) -> list[TodoUpdate]:
    """从消息中解析 Todo 更新"""
    updates: list[TodoUpdate] = []
    for msg in messages:
        if not isinstance(msg, ToolMessage):
            continue
        if getattr(msg, "name", "") != TODO_TOOL_NAME:
            continue
        updates.extend(_parse_tool_payload(msg.content))
    return updates


def extract_todo_plan_ops(messages: list[BaseMessage]) -> list[TodoPlanOp]:
    """从消息中解析 Todo 规划操作"""
    ops: list[TodoPlanOp] = []
    for msg in messages:
        if not isinstance(msg, ToolMessage):
            continue
        if getattr(msg, "name", "") != TODO_PLAN_TOOL_NAME:
            continue
        ops.extend(_parse_plan_payload(msg.content))
    return ops


def build_todo_tool_message(updates: list[TodoUpdate]) -> ToolMessage:
    """构建 Todo 工具消息（用于审计兜底）"""
    payload = {"updates": [u.model_dump(mode="json") for u in updates]}
    content = json.dumps(payload, ensure_ascii=False)
    return ToolMessage(
        content=content,
        name=TODO_TOOL_NAME,
        tool_call_id="todo_audit",
    )


def _parse_tool_payload(payload: object) -> list[TodoUpdate]:
    """解析工具 payload 为更新列表"""
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
    """解析工具 payload 为规划操作列表"""
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
