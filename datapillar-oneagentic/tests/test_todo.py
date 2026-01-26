"""Todo module tests."""

from __future__ import annotations

import json

import pytest

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoPlanOp, TodoUpdate
from datapillar_oneagentic.todo.store import (
    apply_todo_updates,
    apply_todo_plan,
)
from datapillar_oneagentic.todo.tool import (
    TODO_TOOL_NAME,
    build_todo_message,
    extract_todo_updates,
)


def test_apply_updates() -> None:
    """Completed items should be removed."""
    todo = SessionTodoList(session_id="s1", goal="test goal")
    item = todo.add_item("prepare data")
    before_ts = todo.updated_at_ms

    updates = [TodoUpdate(id=item.id, status=ExecutionStatus.COMPLETED, result="completed")]
    changed = todo.apply_updates(updates)

    assert changed is True
    assert todo.get_item(item.id) is None
    assert todo.updated_at_ms >= before_ts


def test_extract_todo() -> None:
    """Should parse Todo updates from tool messages."""
    payload = {"id": "t1", "status": "running", "result": "in progress"}
    msg = Message.tool(
        content=json.dumps(payload, ensure_ascii=False),
        name=TODO_TOOL_NAME,
        tool_call_id="tool_1",
    )

    updates = extract_todo_updates(Messages([msg]))
    assert len(updates) == 1
    assert updates[0].id == "t1"
    assert updates[0].status == ExecutionStatus.RUNNING


def test_todo_message() -> None:
    """Audited tool message should be parseable."""
    updates = [TodoUpdate(id="t2", status=ExecutionStatus.COMPLETED, result="completed")]
    msg = build_todo_message(updates)

    parsed = extract_todo_updates(Messages([msg]))
    assert len(parsed) == 1
    assert parsed[0].id == "t2"
    assert parsed[0].status == ExecutionStatus.COMPLETED


def test_apply_replace() -> None:
    """Todo plan operations should apply correctly."""
    todo = SessionTodoList(session_id="s1", goal="goal")
    changed = todo.apply_plan([TodoPlanOp(op="add", items=["task 1", "task 2"])])
    assert changed is True
    assert [item.description for item in todo.items] == ["task 1", "task 2"]

    changed = todo.apply_plan([TodoPlanOp(op="remove", todo_ids=["t1"])])
    assert changed is True
    assert [item.id for item in todo.items] == ["t2"]

    changed = todo.apply_plan([TodoPlanOp(op="replace", items=["new task"])])
    assert changed is True
    assert [item.description for item in todo.items] == ["new task"]
    assert todo.items[0].id == "t1"


@pytest.mark.asyncio
async def test_apply_todo() -> None:
    """Should create Todo when none exists."""
    ops = [TodoPlanOp(op="replace", items=["task 1", "task 2"], goal="new goal")]
    window = await apply_todo_plan(
        session_id="s1",
        current_todo=None,
        ops=ops,
    )

    assert window is not None
    assert len(window["items"]) == 2
    assert window["items"][0]["description"] == "task 1"


@pytest.mark.asyncio
async def test_apply_todo2() -> None:
    """Completed updates should remove items."""
    todo = SessionTodoList(session_id="s1", goal="test goal")
    for idx in range(2):
        todo.add_item(f"task {idx + 1}")

    updates = [
        TodoUpdate(id="t1", status=ExecutionStatus.COMPLETED, result="completed")
    ]

    updated_window = await apply_todo_updates(
        current_todo=todo.model_dump(mode="json"),
        updates=updates,
    )

    assert updated_window is not None
    assert [item["id"] for item in updated_window["items"]] == ["t2"]
