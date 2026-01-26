from __future__ import annotations

import pytest
from pydantic import BaseModel, Field

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.core.status import FailureKind
from datapillar_oneagentic.core.types import AgentResult
from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import to_langchain
from datapillar_oneagentic.state import StateBuilder


class _OutputSchema(BaseModel):
    answer: str = Field(...)


class _DummyStructuredLLM:
    def __init__(self, result: BaseModel):
        self._result = result

    async def ainvoke(self, _messages):
        return self._result


class _DummyLLM:
    def with_structured_output(self, schema, **_kwargs):
        return _DummyStructuredLLM(schema(answer="ok"))


@pytest.mark.asyncio
async def test_agent_context() -> None:
    spec = AgentSpec(id="demo_agent", name="DemoAgent", deliverable_schema=_OutputSchema)
    ctx = AgentContext(
        namespace="ns",
        session_id="s1",
        query="hello",
        _spec=spec,
        _llm=_DummyLLM(),
        _tools=[],
        _state={"messages": []},
    )

    messages = Messages([Message.system("sys"), Message.user("hi")])
    out = await ctx.invoke_tools(messages)

    assert all(isinstance(m, Message) for m in out)


@pytest.mark.asyncio
async def test_delete_work() -> None:
    from datapillar_oneagentic.context.checkpoint.manager import CheckpointManager
    from datapillar_oneagentic.core.types import SessionKey

    class _SyncCheckpointer:
        def __init__(self):
            self.deleted_thread_id: str | None = None

        def delete_thread(self, thread_id: str) -> bool:
            self.deleted_thread_id = thread_id
            return True

    mgr = CheckpointManager(key=SessionKey(namespace="ns", session_id="s1"), checkpointer=_SyncCheckpointer())
    assert mgr.get_config()["configurable"]["thread_id"] == "ns:s1"
    ok = await mgr.delete()
    assert ok is True


@pytest.mark.asyncio
async def test_fail_fast() -> None:
    event_bus = EventBus()
    timeline_recorder = TimelineRecorder(event_bus)
    nf = NodeFactory(
        agent_specs=[],
        agent_ids=["a1"],
        get_executor=lambda _aid: None,
        timeline_recorder=timeline_recorder,
    )

    state = {
        "namespace": "ns",
        "session_id": "s1",
        "messages": [],
        "deliverable_keys": [],
    }

    result = AgentResult.failed(
        error="Need more information",
        messages=Messages([Message.user("additional details")]),
    )
    with pytest.raises(AgentError) as exc_info:
        await nf._handle_result(  # type: ignore[arg-type]
            state=state,
            agent_id="a1",
            result=result,
            store=None,
            compression_context=None,
        )

    error = exc_info.value
    assert error.category == AgentErrorCategory.BUSINESS
    assert error.failure_kind == FailureKind.BUSINESS


def test_state_builder() -> None:
    state = {
        "session_id": "s1",
        "messages": to_langchain(
            Messages([Message.system("sys"), Message.user("hi"), Message.assistant("ok")])
        ),
    }
    sb = StateBuilder(state)

    assert all(m.role != "system" for m in sb.memory.snapshot())

    sb.memory.append(Messages([Message.system("sys2"), Message.user("hi2")]))

    assert all(m.role != "system" for m in sb.memory.snapshot())
