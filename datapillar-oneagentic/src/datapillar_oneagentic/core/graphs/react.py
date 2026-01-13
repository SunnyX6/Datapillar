"""
ReAct 执行图构建

ReAct (Reasoning + Acting) 模式：
1. react_controller: 规划和反思
2. agents: 根据 active_agent 执行具体 Agent
3. finalize: 整理最终输出

流程：
START → react_controller → [agent_id] → react_controller → ... → finalize → END
"""

from typing import Any

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state.blackboard import Blackboard


def build_react_graph(
    *,
    agent_specs: list,
    agent_ids: list[str],
    create_agent_node,
    llm: Any,
) -> StateGraph:
    """
    构建 ReAct 执行图

    Args:
        agent_specs: Agent 规格列表
        agent_ids: 所有 Agent ID 列表
        create_agent_node: 节点创建函数
        llm: LLM 实例（用于规划和反思）

    Returns:
        StateGraph 实例
    """
    from datapillar_oneagentic.react.controller import react_controller_node

    graph = StateGraph(Blackboard)

    # 1. 添加 react_controller 节点
    async def controller_node(state: Blackboard):
        return await react_controller_node(state, llm=llm, agent_ids=agent_ids)

    graph.add_node("react_controller", controller_node)

    # 2. 添加所有 Agent 节点
    for spec in agent_specs:
        node_fn = create_agent_node(spec.id)
        graph.add_node(spec.id, node_fn)

    # 3. 添加 finalize 节点（整理最终输出）
    def finalize_node(state: Blackboard) -> dict:
        """整理最终输出"""
        return {
            "active_agent": None,
        }

    graph.add_node("finalize", finalize_node)

    # 4. 设置边

    # 入口 → react_controller
    graph.set_entry_point("react_controller")

    # react_controller → 具体 Agent 或 finalize（通过 active_agent 路由）
    graph.add_conditional_edges(
        "react_controller",
        _react_controller_router(agent_ids),
        {
            **{aid: aid for aid in agent_ids},
            "finalize": "finalize",
        },
    )

    # 每个 Agent 执行后 → react_controller
    for spec in agent_specs:
        graph.add_edge(spec.id, "react_controller")

    # finalize → END
    graph.add_edge("finalize", END)

    return graph


def _react_controller_router(agent_ids: list[str]):
    """
    ReAct 控制器路由函数

    根据 active_agent 决定下一个节点：
    - 有 active_agent 且在 agent_ids 中 → 路由到该 Agent
    - 否则 → finalize
    """
    def router(state: Blackboard) -> str:
        active = state.get("active_agent")
        if active and active in agent_ids:
            return active
        return "finalize"
    return router
