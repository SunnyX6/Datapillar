from __future__ import annotations

import pytest
from pydantic import BaseModel

import datapillar_oneagentic.runtime.executor as executor_module
from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.runtime.executor import AgentExecutor
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoUpdate
from datapillar_oneagentic.todo.tool import extract_todo_updates
from datapillar_oneagentic.context.compaction.compact_policy import CompactResult
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import to_langchain


class _OutputSchema(BaseModel):
    text: str


class _DummyCompactor:
    def __init__(self, compressed, result: CompactResult) -> None:
        self._compressed = compressed
        self._result = result

    async def compact(self, _messages):
        return self._compressed, self._result


def _stub_llm_provider(**_kwargs):
    return object()


@pytest.mark.asyncio
async def test_compress_state() -> None:
    original = Messages([Message.assistant("a"), Message.assistant("b")])
    compressed = Messages([Message.assistant("summary"), Message.assistant("b")])
    compactor = _DummyCompactor(
        compressed,
        CompactResult(success=True, summary="ok", kept_count=1, removed_count=1),
    )
    executor = AgentExecutor(
        AgentSpec(id="a1", name="A1", deliverable_schema=_OutputSchema),
        agent_config=AgentConfig(),
        event_bus=EventBus(),
        compactor=compactor,
        llm_provider=_stub_llm_provider,
    )

    state = {"messages": to_langchain(original)}
    new_state = await executor._compress_state_messages(state)

    assert new_state["messages"] == to_langchain(compressed)


@pytest.mark.asyncio
async def test_compress_state2() -> None:
    original = Messages([Message.assistant("a")])
    compactor = _DummyCompactor(
        original,
        CompactResult(
            success=False,
            summary="",
            kept_count=0,
            removed_count=0,
            error="fail",
        ),
    )
    executor = AgentExecutor(
        AgentSpec(id="a1", name="A1", deliverable_schema=_OutputSchema),
        agent_config=AgentConfig(),
        event_bus=EventBus(),
        compactor=compactor,
        llm_provider=_stub_llm_provider,
    )

    state = {"messages": to_langchain(original)}
    new_state = await executor._compress_state_messages(state)

    assert new_state["messages"] == to_langchain(original)


@pytest.mark.asyncio
async def test_audit_updates(monkeypatch) -> None:
    compactor = _DummyCompactor([], CompactResult.no_action("no compaction needed"))
    executor = AgentExecutor(
        AgentSpec(id="a1", name="A1", deliverable_schema=_OutputSchema),
        agent_config=AgentConfig(),
        event_bus=EventBus(),
        compactor=compactor,
        llm_provider=_stub_llm_provider,
    )

    todo = SessionTodoList(session_id="s1", goal="goal")
    todo.add_item("task 1")
    state = {"todo": todo.model_dump(mode="json")}

    updates = [TodoUpdate(id="t1", status=ExecutionStatus.COMPLETED, result="completed")]

    async def fake_audit(*_args, **_kwargs):
        return updates

    monkeypatch.setattr(executor_module, "audit_todo_updates", fake_audit)

    messages = Messages()
    await executor._append_todo_audit(
        state=state,
        result_status=ExecutionStatus.COMPLETED,
        failure_kind=None,
        deliverable={"ok": True},
        error=None,
        messages=messages,
        llm=object(),
    )

    parsed = extract_todo_updates(messages)
    assert len(parsed) == 1
