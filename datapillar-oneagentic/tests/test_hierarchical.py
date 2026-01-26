from __future__ import annotations

import pytest
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
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import to_langchain


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
            messages=Messages(),
        )


@pytest.mark.asyncio
async def test_agent_context() -> None:
    spec = AgentSpec(id="demo", name="Demo", deliverable_schema=_OutputSchema)
    state = {"messages": [], "assigned_task": "handle assigned task"}
    collector = ContextCollector()
    contexts = await collector.collect(
        scenario=ContextScenario.AGENT,
        state=state,
        query="user input",
        session_id="s1",
        spec=spec,
        has_knowledge_tool=False,
    )
    runtime_state = dict(state)
    runtime_state.update(contexts)
    ctx = AgentContext(
        namespace="ns",
        session_id="s1",
        query="user input",
        _spec=spec,
        _state=runtime_state,
    )

    base_messages = ctx.messages().system("system").user(ctx.query)
    messages = ctx._compose_messages(base_messages)
    assert any(
        msg.role == "system" and "assigned task" in msg.content for msg in messages
    )


@pytest.mark.asyncio
async def test_agent_context2() -> None:
    class _DummyTool:
        name = "knowledge_retrieve"

    spec = AgentSpec(
        id="demo",
        name="Demo",
        deliverable_schema=_OutputSchema,
        knowledge=Knowledge(sources=[KnowledgeSource(source_id="s1", name="example", source_type="doc")]),
    )
    state = {"messages": []}
    collector = ContextCollector()
    contexts = await collector.collect(
        scenario=ContextScenario.AGENT,
        state=state,
        query="user input",
        session_id="s1",
        spec=spec,
        has_knowledge_tool=True,
    )
    runtime_state = dict(state)
    runtime_state.update(contexts)
    ctx = AgentContext(
        namespace="ns",
        session_id="s1",
        query="user input",
        _spec=spec,
        _tools=[_DummyTool()],
        _state=runtime_state,
    )

    base_messages = ctx.messages().system("system").user(ctx.query)
    messages = ctx._compose_messages(base_messages)
    assert any(
        msg.role == "system" and "knowledge_retrieve" in msg.content for msg in messages
    )


@pytest.mark.asyncio
async def test_use_assigned(monkeypatch) -> None:
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
        "messages": to_langchain(Messages([Message.user("user input")])),
        "assigned_task": "handle assigned task",
        "deliverable_keys": [],
    }

    node = node_factory.create_agent_node("worker")
    cmd = await node(state)

    assert executor.last_query == "handle assigned task"
    update = getattr(cmd, "update", None)
    assert isinstance(update, dict)
    assert update.get("assigned_task") is None
