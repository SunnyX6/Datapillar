from __future__ import annotations

from dataclasses import dataclass

import pytest
from langgraph.types import Command

from datapillar_oneagentic.core.graphs.sequential import build_sequential_graph
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.state.blackboard import create_blackboard


@dataclass(slots=True)
class _Spec:
    id: str


@pytest.mark.asyncio
async def test_stop_failed() -> None:
    executed: list[str] = []

    async def node_failed(state):
        executed.append("a1")
        sb = StateBuilder(state)
        sb.routing.finish_agent(status=ExecutionStatus.FAILED, error=None)
        return Command(update=sb.patch())

    async def run_node(state):
        executed.append("a2")
        sb = StateBuilder(state)
        sb.routing.clear_active()
        return Command(update=sb.patch())

    specs = [_Spec(id="a1"), _Spec(id="a2")]
    nodes = {"a1": node_failed, "a2": run_node}

    graph = build_sequential_graph(
        agent_specs=specs,
        entry_agent_id="a1",
        create_agent_node=lambda agent_id: nodes[agent_id],
    )
    compiled = graph.compile()

    state = create_blackboard(namespace="ns", session_id="s1")
    StateBuilder(state).routing.activate("a1")

    async for _ in compiled.astream(state, {"configurable": {"thread_id": "t1"}}):
        pass

    assert executed == ["a1"]
