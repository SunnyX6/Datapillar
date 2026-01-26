"""
ReAct execution graph builder.

ReAct (Reasoning + Acting) mode:
1. react_controller: planning and reflection
2. agents: execute agent by active_agent
3. finalize: format final output

Flow:
START -> react_controller -> [agent_id] -> react_controller -> ... -> finalize -> END
"""

from typing import Any

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.state.blackboard import Blackboard


def build_react_graph(
    *,
    agent_specs: list,
    agent_ids: list[str],
    create_agent_node,
    llm: Any,
) -> StateGraph:
    """
    Build a ReAct execution graph.

    Args:
        agent_specs: agent spec list
        agent_ids: all agent IDs
        create_agent_node: node factory
        llm: LLM instance (planning/reflection)

    Returns:
        StateGraph instance
    """
    from datapillar_oneagentic.core.graphs.react.controller import react_controller_node

    graph = StateGraph(Blackboard)

    # 1. Add react_controller node.
    async def controller_node(state: Blackboard):
        return await react_controller_node(
            state,
            llm=llm,
            agent_ids=agent_ids,
            agent_specs=agent_specs,
        )

    graph.add_node("react_controller", controller_node)

    # 2. Add all agent nodes.
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        graph.add_node(spec.id, node_fn)

    # 3. Add finalize node.
    def finalize_node(state: Blackboard) -> dict:
        """Prepare final output."""
        sb = StateBuilder(state)
        sb.routing.clear_active()
        return sb.patch()

    graph.add_node("finalize", finalize_node)

    # 4. Set edges.

    # Entry -> react_controller.
    graph.set_entry_point("react_controller")

    # react_controller -> agent or finalize via active_agent routing.
    graph.add_conditional_edges(
        "react_controller",
        _react_controller_router(agent_ids),
        {
            **{aid: aid for aid in agent_ids},
            "finalize": "finalize",
        },
    )

    # After each agent -> react_controller.
    for spec in agent_specs:
        graph.add_edge(spec.id, "react_controller")

    # finalize -> END.
    graph.add_edge("finalize", END)

    return graph


def _react_controller_router(agent_ids: list[str]):
    """
    ReAct controller router.

    Route by active_agent:
    - If active_agent is in agent_ids -> that agent
    - Otherwise -> finalize
    """
    def router(state: Blackboard) -> str:
        sb = StateBuilder(state)
        active = sb.routing.snapshot().active_agent
        if active and active in agent_ids:
            return active
        return "finalize"
    return router
