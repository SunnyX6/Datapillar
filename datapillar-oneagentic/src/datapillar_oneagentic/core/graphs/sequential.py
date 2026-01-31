# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Sequential execution graph builder.

Agents execute in order: A -> B -> C -> END

Supports interrupt pause:
- Normal completion -> next agent
- Interrupt pause -> resume current agent after user reply
"""

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.core.status import ExecutionStatus


def build_sequential_graph(
    *,
    agent_specs: list,
    entry_agent_id: str,
    create_agent_node,
) -> StateGraph:
    """
    Build a sequential execution graph.

    Args:
        agent_specs: agent spec list
        entry_agent_id: entry agent ID
        create_agent_node: node factory

    Returns:
        StateGraph instance
    """
    graph = StateGraph(Blackboard)

    # Create nodes for each agent.
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        graph.add_node(spec.id, node_fn)

    # Set entry point.
    graph.set_entry_point(entry_agent_id)

    # Conditional edges: route by active_agent.
    for i, spec in enumerate(agent_specs[:-1]):
        next_spec = agent_specs[i + 1]

        def make_router(current_id: str, next_id: str):
            def router(state) -> str:
                sb = StateBuilder(state)
                routing = sb.routing.snapshot()
                active = routing.active_agent
                # If active_agent stays on the current agent (clarification retry), return current.
                if active == current_id:
                    return current_id
                if routing.last_status in {ExecutionStatus.FAILED, ExecutionStatus.ABORTED}:
                    return "end"
                # Otherwise continue to next.
                return next_id
            return router

        graph.add_conditional_edges(
            spec.id,
            make_router(spec.id, next_spec.id),
            {spec.id: spec.id, next_spec.id: next_spec.id, "end": END},
        )

    # Conditional edges for the last agent.
    if agent_specs:
        last_spec = agent_specs[-1]

        def last_router(state) -> str:
            sb = StateBuilder(state)
            routing = sb.routing.snapshot()
            active = routing.active_agent
            if active == last_spec.id:
                return last_spec.id
            if routing.last_status in {ExecutionStatus.FAILED, ExecutionStatus.ABORTED}:
                return "end"
            return "end"

        graph.add_conditional_edges(
            last_spec.id,
            last_router,
            {last_spec.id: last_spec.id, "end": END},
        )

    return graph
