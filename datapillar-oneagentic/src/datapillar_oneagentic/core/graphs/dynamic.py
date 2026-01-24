"""
动态执行图构建

Agent 自主决定是否委派：
- 返回 Command(goto="other_agent") 跳转到其他 Agent
- 不返回 goto 或返回 Command(goto=END) 则流程结束
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
    构建动态执行图

    Args:
        agent_specs: Agent 规格列表
        agent_ids: 所有 Agent ID 列表
        create_agent_node: 节点创建函数

    Returns:
        StateGraph 实例
    """
    graph = StateGraph(Blackboard)

    # 为每个 Agent 创建节点，声明可跳转的目标（包括 END）
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        # destinations: 其他 Agent + END
        other_agents = tuple(aid for aid in agent_ids if aid != spec.id)
        graph.add_node(spec.id, node_fn, destinations=(*other_agents, END))

    # 条件入口：根据 active_agent 路由
    route_map = {agent_id: agent_id for agent_id in agent_ids}
    route_map["end"] = END
    graph.set_conditional_entry_point(_route_by_active_agent(agent_ids), route_map)

    # 每个 Agent 执行后：由 Command(goto=...) 决定下一步，不委派则结束
    for spec in agent_specs:
        graph.add_edge(spec.id, END)

    return graph


def _route_by_active_agent(agent_ids: list[str]):
    """创建根据 active_agent 路由的函数"""
    def router(state) -> str:
        sb = StateBuilder(state)
        active = sb.routing.snapshot().active_agent
        if active and active in agent_ids:
            return active
        return "end"
    return router
