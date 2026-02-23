from __future__ import annotations

import pytest
from langgraph.graph import END, StateGraph

from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import EventBus, EventType
from datapillar_oneagentic.exception import AgentExecutionFailedException
from datapillar_oneagentic.runtime.orchestrator import Orchestrator
from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.storage import create_checkpointer, create_store


@pytest.mark.asyncio
async def test_orchestrator_emits() -> None:
    async def bad_node(_state: Blackboard):
        raise AgentExecutionFailedException(
            "boom",
            agent_id="a1",
        )

    graph = StateGraph(Blackboard)
    graph.add_node("a1", bad_node)
    graph.set_entry_point("a1")
    graph.add_edge("a1", END)

    agent_config = AgentConfig()
    async with create_checkpointer("ns", agent_config=agent_config) as checkpointer:
        async with create_store("ns", agent_config=agent_config) as store:
            orchestrator = Orchestrator(
                namespace="ns",
                name="test",
                graph=graph,
                entry_agent_id="a1",
                agent_ids=["a1"],
                checkpointer=checkpointer,
                store=store,
                event_bus=EventBus(),
            )

            key = SessionKey(namespace="ns", session_id="s1")
            stream = orchestrator.stream(query="hello", key=key)

            first_event = await anext(stream)
            assert first_event["event"] == EventType.AGENT_FAILED.value
            error = first_event.get("data", {}).get("error", {})
            assert error.get("message") == "Execution failed"
            assert error.get("error_type") == "AgentExecutionFailedException"

            with pytest.raises(StopAsyncIteration):
                await anext(stream)
