from __future__ import annotations

from dataclasses import dataclass

import pytest
from langgraph.types import Command

from datapillar_oneagentic.core.graphs.sequential import build_sequential_graph
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.state.blackboard import create_blackboard


@dataclass(slots=True)
class _Spec:
    id: str


@pytest.mark.asyncio
async def test_sequential_should_stop_when_failed() -> None:
    executed: list[str] = []

    async def node_failed(_state):
        executed.append("a1")
        return Command(
            update={
                "active_agent": None,
                "last_agent_status": ExecutionStatus.FAILED,
            }
        )

    async def node_should_not_run(_state):
        executed.append("a2")
        return Command(update={"active_agent": None})

    specs = [_Spec(id="a1"), _Spec(id="a2")]
    nodes = {"a1": node_failed, "a2": node_should_not_run}

    graph = build_sequential_graph(
        agent_specs=specs,
        entry_agent_id="a1",
        create_agent_node=lambda agent_id: nodes[agent_id],
    )
    compiled = graph.compile()

    state = create_blackboard(namespace="ns", session_id="s1")
    state["active_agent"] = "a1"

    async for _ in compiled.astream(state, {"configurable": {"thread_id": "t1"}}):
        pass

    assert executed == ["a1"]
