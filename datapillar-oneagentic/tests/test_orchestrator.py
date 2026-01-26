"""Orchestrator edge tests.

Core orchestrator edge cases:
1. Invalid input handling
2. State restoration logic
3. Interrupt resume logic
"""

from __future__ import annotations

import pytest
from dataclasses import dataclass

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.runtime.orchestrator import Orchestrator
from datapillar_oneagentic.events import EventType


class _MockStateGraph:
    """Mock StateGraph"""

    def __init__(self, events: list[dict] | None = None, state=None):
        self._events = events or []
        self._state = state

    def compile(self, checkpointer=None, store=None):
        return _MockCompiledGraph(self._events, state=self._state)


class _MockCompiledGraph:
    """Mock CompiledGraph"""

    def __init__(self, events: list[dict], state=None):
        self._events = events
        self._state = state
        self.updated: list[dict] = []

    async def aget_state(self, config):
        return self._state

    async def astream(self, input_data, config):
        for event in self._events:
            yield event

    async def aupdate_state(self, config, updates):
        self.updated.append(updates)


@dataclass(slots=True)
class _StoreItem:
    key: str
    value: object


class _StubStore:
    def __init__(self) -> None:
        self._data: dict[tuple[tuple, str], object] = {}

    async def aput(self, namespace: tuple, key: str, value: object) -> None:
        self._data[(namespace, key)] = value

    async def aget(self, namespace: tuple, key: str) -> _StoreItem | None:
        value = self._data.get((namespace, key))
        if value is None:
            return None
        return _StoreItem(key=key, value=value)

    async def asearch(self, namespace: tuple) -> list[_StoreItem]:
        return [
            _StoreItem(key=key, value=value)
            for (ns, key), value in self._data.items()
            if ns == namespace
        ]

    async def adelete(self, namespace: tuple, key: str) -> None:
        self._data.pop((namespace, key), None)


@pytest.mark.asyncio
async def test_orchestrator_stream() -> None:
    """Should return an error event when query and resume_value are missing."""
    graph = _MockStateGraph()

    orchestrator = Orchestrator(
        namespace="test",
        name="test_team",
        graph=graph,
        entry_agent_id="agent1",
        agent_ids=["agent1"],
        checkpointer=None,
        store=None,
        event_bus=EventBus(),
    )

    key = SessionKey(namespace="test", session_id="s1")

    events = []
    async for event in orchestrator.stream(query=None, key=key, resume_value=None):
        events.append(event)

    assert len(events) == 1
    assert events[0]["event"] == EventType.AGENT_FAILED.value
    assert "Invalid call" in events[0]["data"]["error"]["message"]


@pytest.mark.asyncio
async def test_emit_agent() -> None:
    """Should emit agent events when query is provided."""
    graph = _MockStateGraph(events=[{"agent1": {"last_agent_status": ExecutionStatus.COMPLETED}}])

    orchestrator = Orchestrator(
        namespace="test",
        name="test_team",
        graph=graph,
        entry_agent_id="agent1",
        agent_ids=["agent1"],
        checkpointer=None,
        store=None,
        event_bus=EventBus(),
    )

    key = SessionKey(namespace="test", session_id="s1")

    events = []
    async for event in orchestrator.stream(query="hello", key=key):
        events.append(event)

    # Should only have agent.start/agent.end events.
    event_types = [e["event"] for e in events]
    assert event_types == [EventType.AGENT_START.value, EventType.AGENT_END.value]


@pytest.mark.asyncio
async def test_orchestrator_stream2() -> None:
    """Agent events should include session info."""
    graph = _MockStateGraph(events=[{"entry": {"last_agent_status": ExecutionStatus.COMPLETED}}])

    orchestrator = Orchestrator(
        namespace="ns1",
        name="my_team",
        graph=graph,
        entry_agent_id="entry",
        agent_ids=["entry"],
        checkpointer=None,
        store=None,
        event_bus=EventBus(),
    )

    key = SessionKey(namespace="ns1", session_id="session123")

    events = []
    async for event in orchestrator.stream(query="test", key=key):
        events.append(event)

    agent_event = next(e for e in events if e["event"] == EventType.AGENT_START.value)
    assert agent_event["session_id"] == "session123"
    assert agent_event["namespace"] == "ns1"


@dataclass(slots=True)
class _MockStateSnapshot:
    values: dict
    tasks: list | None = None


@pytest.mark.asyncio
async def test_cleanup_state() -> None:
    """Should clear deliverables and state references on completion."""
    store = _StubStore()
    await store.aput(("deliverables", "test", "s1"), "agent1", {"ok": True})

    state = _MockStateSnapshot(
        values={
            "deliverable_keys": ["agent1"],
            "todo": {"items": [{"id": "t1"}]},
        },
        tasks=[],
    )
    graph = _MockStateGraph(events=[], state=state)

    orchestrator = Orchestrator(
        namespace="test",
        name="test_team",
        graph=graph,
        entry_agent_id="agent1",
        agent_ids=["agent1"],
        checkpointer=None,
        store=store,
        event_bus=EventBus(),
    )

    key = SessionKey(namespace="test", session_id="s1")
    events = []
    async for event in orchestrator.stream(query="hello", key=key):
        events.append(event)

    assert await store.aget(("deliverables", "test", "s1"), "agent1") is None

    compiled = orchestrator._compiled_graph
    assert compiled.updated
    last_update = compiled.updated[-1]
    assert last_update["todo"] is None
    assert last_update["deliverable_keys"] == []
