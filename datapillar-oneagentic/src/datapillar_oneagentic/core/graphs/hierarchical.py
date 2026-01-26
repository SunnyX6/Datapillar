"""
Hierarchical execution graph builder.

Manager (first agent) coordinates tasks:
- Manager can delegate tasks to sub-agents
- Sub-agents return to Manager on completion
- Manager decides to continue or end
"""

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state.blackboard import Blackboard


def build_hierarchical_graph(
    *,
    agent_specs: list,
    entry_agent_id: str,
    agent_ids: list[str],
    create_agent_node,
) -> StateGraph:
    """
    Build a hierarchical execution graph.

    Args:
        agent_specs: agent spec list
        entry_agent_id: entry agent ID (manager)
        agent_ids: all agent IDs
        create_agent_node: node factory

    Returns:
        StateGraph instance
    """
    graph = StateGraph(Blackboard)

    manager_id = entry_agent_id
    subordinate_ids = [aid for aid in agent_ids if aid != manager_id]

    # Manager node: delegate via Command.goto or end.
    manager_node = create_agent_node(manager_id)
    graph.add_node(manager_id, manager_node, destinations=(*subordinate_ids, END))

    # Subordinate nodes: return to Manager after execution.
    for sub_id in subordinate_ids:
        sub_node = create_agent_node(sub_id)
        graph.add_node(sub_id, sub_node)
        # Always return to Manager after execution.
        graph.add_edge(sub_id, manager_id)

    # Entry point: start from Manager.
    graph.set_entry_point(manager_id)

    # Manager ends if no delegation.
    graph.add_edge(manager_id, END)

    return graph
