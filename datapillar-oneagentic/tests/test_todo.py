"""Todo 模块测试"""

from __future__ import annotations

import json

import pytest

from langchain_core.messages import ToolMessage

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoPlanOp, TodoUpdate
from datapillar_oneagentic.todo.store import (
    apply_todo_updates,
    apply_todo_plan,
)
from datapillar_oneagentic.todo.tool import (
    TODO_TOOL_NAME,
    build_todo_tool_message,
    extract_todo_updates,
)


def test_session_todo_apply_updates_should_remove_terminal_items() -> None:
    """Todo 终态应被移除"""
    todo = SessionTodoList(session_id="s1", goal="测试目标")
    item = todo.add_item("准备数据")
    before_ts = todo.updated_at_ms

    updates = [TodoUpdate(id=item.id, status=ExecutionStatus.COMPLETED, result="完成")]
    changed = todo.apply_updates(updates)

    assert changed is True
    assert todo.get_item(item.id) is None
    assert todo.updated_at_ms >= before_ts


def test_extract_todo_updates_should_parse_tool_message() -> None:
    """应能从 ToolMessage 解析 Todo 更新"""
    payload = {"id": "t1", "status": "running", "result": "推进中"}
    msg = ToolMessage(
        content=json.dumps(payload, ensure_ascii=False),
        name=TODO_TOOL_NAME,
        tool_call_id="tool_1",
    )

    updates = extract_todo_updates([msg])
    assert len(updates) == 1
    assert updates[0].id == "t1"
    assert updates[0].status == ExecutionStatus.RUNNING


def test_build_todo_tool_message_should_roundtrip() -> None:
    """审计生成的 ToolMessage 应可被解析"""
    updates = [TodoUpdate(id="t2", status=ExecutionStatus.COMPLETED, result="完成")]
    msg = build_todo_tool_message(updates)

    parsed = extract_todo_updates([msg])
    assert len(parsed) == 1
    assert parsed[0].id == "t2"
    assert parsed[0].status == ExecutionStatus.COMPLETED


def test_session_todo_apply_plan_should_add_remove_replace() -> None:
    """Todo 规划操作应正确生效"""
    todo = SessionTodoList(session_id="s1", goal="目标")
    changed = todo.apply_plan([TodoPlanOp(op="add", items=["任务1", "任务2"])])
    assert changed is True
    assert [item.description for item in todo.items] == ["任务1", "任务2"]

    changed = todo.apply_plan([TodoPlanOp(op="remove", todo_ids=["t1"])])
    assert changed is True
    assert [item.id for item in todo.items] == ["t2"]

    changed = todo.apply_plan([TodoPlanOp(op="replace", items=["新任务"])])
    assert changed is True
    assert [item.description for item in todo.items] == ["新任务"]
    assert todo.items[0].id == "t1"


@pytest.mark.asyncio
async def test_apply_todo_plan_should_init_when_empty() -> None:
    """没有 Todo 时应可直接创建"""
    ops = [TodoPlanOp(op="replace", items=["任务1", "任务2"], goal="新目标")]
    window = await apply_todo_plan(
        session_id="s1",
        current_todo=None,
        ops=ops,
    )

    assert window is not None
    assert len(window["items"]) == 2
    assert window["items"][0]["description"] == "任务1"


@pytest.mark.asyncio
async def test_apply_todo_updates_should_remove_completed_items() -> None:
    """更新完成后应移除条目"""
    todo = SessionTodoList(session_id="s1", goal="测试目标")
    for idx in range(2):
        todo.add_item(f"任务{idx + 1}")

    updates = [
        TodoUpdate(id="t1", status=ExecutionStatus.COMPLETED, result="完成")
    ]

    updated_window = await apply_todo_updates(
        current_todo=todo.model_dump(mode="json"),
        updates=updates,
    )

    assert updated_window is not None
    assert [item["id"] for item in updated_window["items"]] == ["t2"]
