# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Dynamic execution graph builder.

Agents decide whether to delegate:
- Return Command(goto="other_agent") to jump to another agent
- No goto or Command(goto=END) ends the flow
"""

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.state.blackboard import Blackboard


def build_dynamic_graph(
    *,
    agent_specs: list,
    agent_ids: list[str],
    create_agent_node,
) -> StateGraph:
    """
    Build a dynamic execution graph.

    Args:
        agent_specs: agent spec list
        agent_ids: all agent IDs
        create_agent_node: node factory

    Returns:
        StateGraph instance
    """
    graph = StateGraph(Blackboard)

    # Create nodes for each agent with allowed destinations (including END).
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        # Destinations: other agents + END.
        other_agents = tuple(aid for aid in agent_ids if aid != spec.id)
        graph.add_node(spec.id, node_fn, destinations=(*other_agents, END))

    # Conditional entry: route by active_agent.
    route_map = {agent_id: agent_id for agent_id in agent_ids}
    route_map["end"] = END
    graph.set_conditional_entry_point(_route_active_agent(agent_ids), route_map)

    # After each agent: Command(goto=...) decides next step, otherwise end.
    for spec in agent_specs:
        graph.add_edge(spec.id, END)

    return graph


def _route_active_agent(agent_ids: list[str]):
    """Create a router based on active_agent."""
    def router(state) -> str:
        sb = StateBuilder(state)
        active = sb.routing.snapshot().active_agent
        if active and active in agent_ids:
            return active
        return "end"
    return router
