from __future__ import annotations

import pytest
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.core.status import FailureKind
from datapillar_oneagentic.core.types import AgentResult
from datapillar_oneagentic.context.compaction.compact_policy import CompactResult
from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
from datapillar_oneagentic.events import EventBus


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
async def test_agent_context_invoke_tools_without_tools_should_not_pollute_messages() -> None:
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

    messages = [SystemMessage(content="sys"), HumanMessage(content="hi")]
    out = await ctx.invoke_tools(messages)

    assert all(isinstance(m, BaseMessage) for m in out)


@pytest.mark.asyncio
async def test_checkpoint_manager_delete_should_work_with_sync_checkpointer() -> None:
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


class _DummyCompactor:
    async def compact(self, messages):
        return messages, CompactResult.no_action("无需压缩")


@pytest.mark.asyncio
async def test_node_factory_should_fail_fast_on_failed_result() -> None:
    event_bus = EventBus()
    timeline_recorder = TimelineRecorder(event_bus)
    nf = NodeFactory(
        agent_specs=[],
        agent_ids=["a1"],
        get_executor=lambda _aid: None,
        compactor=_DummyCompactor(),
        timeline_recorder=timeline_recorder,
    )

    state = {
        "namespace": "ns",
        "session_id": "s1",
        "messages": [],
        "deliverable_keys": [],
    }

    result = AgentResult.failed(error="需要补充信息", messages=[HumanMessage(content="补充内容")])
    with pytest.raises(AgentError) as exc_info:
        await nf._handle_result(state=state, agent_id="a1", result=result, store=None)  # type: ignore[arg-type]

    error = exc_info.value
    assert error.category == AgentErrorCategory.BUSINESS
    assert error.failure_kind == FailureKind.BUSINESS
