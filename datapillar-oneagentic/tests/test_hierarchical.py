from __future__ import annotations

import pytest
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

import datapillar_oneagentic.core.nodes as nodes_module
from datapillar_oneagentic.context import ContextCollector, ContextScenario
from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.knowledge.models import Knowledge, KnowledgeSource
from datapillar_oneagentic.core.types import AgentResult
from datapillar_oneagentic.events import EventBus


class _OutputSchema(BaseModel):
    answer: str = Field(...)


class _DummyStore:
    def __init__(self) -> None:
        self.items: list[tuple[tuple, str, object]] = []

    async def aput(self, namespace, key, value):
        self.items.append((namespace, key, value))

class _DummyExecutor:
    def __init__(self) -> None:
        self.last_query: str | None = None

    async def execute(self, *, query: str, state: dict, additional_tools=None):
        self.last_query = query
        return AgentResult.completed(
            deliverable=_OutputSchema(answer="ok"),
            deliverable_type="dummy",
            messages=[],
        )


@pytest.mark.asyncio
async def test_agent_context_should_include_assigned_task() -> None:
    spec = AgentSpec(id="demo", name="Demo", deliverable_schema=_OutputSchema)
    state = {"messages": [], "assigned_task": "处理下发任务"}
    collector = ContextCollector()
    contexts = await collector.collect(
        scenario=ContextScenario.AGENT,
        state=state,
        query="用户输入",
        session_id="s1",
        spec=spec,
        has_knowledge_tool=False,
    )
    runtime_state = dict(state)
    runtime_state.update(contexts)
    ctx = AgentContext(
        namespace="ns",
        session_id="s1",
        query="用户输入",
        _spec=spec,
        _state=runtime_state,
    )

    messages = ctx.build_messages("system")
    assert any(
        isinstance(msg, SystemMessage) and "下发任务" in msg.content for msg in messages
    )


@pytest.mark.asyncio
async def test_agent_context_should_attach_knowledge_tool_instruction() -> None:
    class _DummyTool:
        name = "knowledge_retrieve"

    spec = AgentSpec(
        id="demo",
        name="Demo",
        deliverable_schema=_OutputSchema,
        knowledge=Knowledge(sources=[KnowledgeSource(source_id="s1", name="示例", source_type="doc")]),
    )
    state = {"messages": []}
    collector = ContextCollector()
    contexts = await collector.collect(
        scenario=ContextScenario.AGENT,
        state=state,
        query="用户输入",
        session_id="s1",
        spec=spec,
        has_knowledge_tool=True,
    )
    runtime_state = dict(state)
    runtime_state.update(contexts)
    ctx = AgentContext(
        namespace="ns",
        session_id="s1",
        query="用户输入",
        _spec=spec,
        _tools=[_DummyTool()],
        _state=runtime_state,
    )

    messages = ctx.build_messages("system")
    assert any(
        isinstance(msg, SystemMessage) and "knowledge_retrieve" in msg.content for msg in messages
    )


@pytest.mark.asyncio
async def test_node_factory_should_use_assigned_task_and_clear(monkeypatch) -> None:
    executor = _DummyExecutor()
    event_bus = EventBus()
    timeline_recorder = TimelineRecorder(event_bus)
    node_factory = NodeFactory(
        agent_specs=[],
        agent_ids=["worker"],
        get_executor=lambda _aid: executor,
        timeline_recorder=timeline_recorder,
    )

    dummy_store = _DummyStore()
    monkeypatch.setattr(nodes_module, "get_store", lambda: dummy_store)

    state = {
        "namespace": "ns",
        "session_id": "s1",
        "messages": [HumanMessage(content="用户输入")],
        "assigned_task": "处理下发任务",
        "deliverable_keys": [],
    }

    node = node_factory.create_agent_node("worker")
    cmd = await node(state)

    assert executor.last_query == "处理下发任务"
    update = getattr(cmd, "update", None)
    assert isinstance(update, dict)
    assert update.get("assigned_task") is None
