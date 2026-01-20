from __future__ import annotations

import pytest
from langgraph.graph import END, StateGraph

from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory
from datapillar_oneagentic.core.status import FailureKind
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import EventBus, EventType
from datapillar_oneagentic.exception import RecoveryAction
from datapillar_oneagentic.runtime.orchestrator import Orchestrator
from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.storage import create_checkpointer, create_store


@pytest.mark.asyncio
async def test_orchestrator_emits_error_event_and_raises() -> None:
    async def bad_node(_state: Blackboard):
        raise AgentError(
            "boom",
            agent_id="a1",
            category=AgentErrorCategory.SYSTEM,
            action=RecoveryAction.FAIL_FAST,
            failure_kind=FailureKind.SYSTEM,
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
            assert error.get("message") == "Agent 执行失败"
            assert error.get("error_type") == "agent"

            with pytest.raises(AgentError):
                await anext(stream)
