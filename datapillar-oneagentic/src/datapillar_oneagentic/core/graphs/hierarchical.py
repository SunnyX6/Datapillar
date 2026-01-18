"""
层级执行图构建

Manager（第一个 Agent）协调分配任务：
- Manager 可以委派任务给下属 Agent
- 下属执行完毕后自动返回 Manager
- Manager 决定继续分配或结束
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
    构建层级执行图

    Args:
        agent_specs: Agent 规格列表
        entry_agent_id: 入口 Agent ID（Manager）
        agent_ids: 所有 Agent ID 列表
        create_agent_node: 节点创建函数

    Returns:
        StateGraph 实例
    """
    graph = StateGraph(Blackboard)

    manager_id = entry_agent_id
    subordinate_ids = [aid for aid in agent_ids if aid != manager_id]

    # Manager 节点：通过 Command.goto 委派到下属或结束
    manager_node = create_agent_node(manager_id)
    graph.add_node(manager_id, manager_node, destinations=(*subordinate_ids, END))

    # 下属节点：执行完返回 Manager
    for sub_id in subordinate_ids:
        sub_node = create_agent_node(sub_id)
        graph.add_node(sub_id, sub_node)
        # 下属执行完固定返回 Manager
        graph.add_edge(sub_id, manager_id)

    # 入口：从 Manager 开始
    graph.set_entry_point(manager_id)

    # Manager 未委派时默认结束
    graph.add_edge(manager_id, END)

    return graph
