"""
顺序执行图构建

Agent 按定义顺序依次执行：A → B → C → END

支持 interrupt 暂停：
- 正常完成 → 下一个 Agent
- interrupt 暂停 → 用户回复后继续执行当前 Agent
"""

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.core.status import ExecutionStatus


def build_sequential_graph(
    *,
    agent_specs: list,
    entry_agent_id: str,
    create_agent_node,
) -> StateGraph:
    """
    构建顺序执行图

    Args:
        agent_specs: Agent 规格列表
        entry_agent_id: 入口 Agent ID
        create_agent_node: 节点创建函数

    Returns:
        StateGraph 实例
    """
    graph = StateGraph(Blackboard)

    # 为每个 Agent 创建节点
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        graph.add_node(spec.id, node_fn)

    # 设置入口
    graph.set_entry_point(entry_agent_id)

    # 条件边：根据 active_agent 决定下一步
    for i, spec in enumerate(agent_specs[:-1]):
        next_spec = agent_specs[i + 1]

        def make_router(current_id: str, next_id: str):
            def router(state) -> str:
                active = state.get("active_agent")
                # 如果 active_agent 仍是当前 Agent（clarification 后重试），返回当前
                if active == current_id:
                    return current_id
                if state.get("last_agent_status") == ExecutionStatus.FAILED:
                    return "end"
                # 否则继续下一个
                return next_id
            return router

        graph.add_conditional_edges(
            spec.id,
            make_router(spec.id, next_spec.id),
            {spec.id: spec.id, next_spec.id: next_spec.id, "end": END},
        )

    # 最后一个 Agent 的条件边
    if agent_specs:
        last_spec = agent_specs[-1]

        def last_router(state) -> str:
            active = state.get("active_agent")
            if active == last_spec.id:
                return last_spec.id
            if state.get("last_agent_status") == ExecutionStatus.FAILED:
                return "end"
            return "end"

        graph.add_conditional_edges(
            last_spec.id,
            last_router,
            {last_spec.id: last_spec.id, "end": END},
        )

    return graph
