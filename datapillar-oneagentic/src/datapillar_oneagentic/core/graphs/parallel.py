"""
并行执行图构建

根据 depends_on 分析依赖关系，无依赖的 Agent 并行执行。

示例：
    A (无依赖)    B (无依赖)
           ↘    ↙
         C (depends_on=[A, B])
             ↓
         D (depends_on=[C])

执行顺序：A, B 并行 → C → D
"""

import logging

from langgraph.graph import END, StateGraph

from datapillar_oneagentic.state.blackboard import Blackboard

logger = logging.getLogger(__name__)


def build_parallel_graph(
    *,
    agent_specs: list,
    create_parallel_layer_node,
) -> StateGraph:
    """
    构建并行执行图

    Args:
        agent_specs: Agent 规格列表
        create_parallel_layer_node: 并行层节点创建函数

    Returns:
        StateGraph 实例
    """
    graph = StateGraph(Blackboard)

    # 构建依赖图并分层
    layers = topological_sort_layers(agent_specs)

    if not layers:
        raise ValueError("PARALLEL 模式需要至少一个 Agent")

    # 为每一层创建并行执行节点
    layer_node_ids = []
    for layer_idx, agent_ids in enumerate(layers):
        layer_node_id = f"__parallel_layer_{layer_idx}__"
        layer_node_ids.append(layer_node_id)

        # 创建并行执行节点
        layer_node = create_parallel_layer_node(agent_ids)
        graph.add_node(layer_node_id, layer_node)

    # 设置入口
    graph.set_entry_point(layer_node_ids[0])

    # 顺序连接各层
    for i in range(len(layer_node_ids) - 1):
        graph.add_edge(layer_node_ids[i], layer_node_ids[i + 1])

    # 最后一层连接到 END
    graph.add_edge(layer_node_ids[-1], END)

    return graph


def topological_sort_layers(agent_specs: list) -> list[list[str]]:
    """
    拓扑排序并分层

    Args:
        agent_specs: Agent 规格列表

    Returns:
        [[layer0_agents], [layer1_agents], ...]
        同一层的 Agent 无互相依赖，可以并行执行。
    """
    # 构建依赖图
    in_degree = {spec.id: 0 for spec in agent_specs}
    dependents = {spec.id: [] for spec in agent_specs}

    for spec in agent_specs:
        for dep_id in spec.depends_on:
            if dep_id in in_degree:
                in_degree[spec.id] += 1
                dependents[dep_id].append(spec.id)
            else:
                logger.warning(
                    f"Agent {spec.id} 依赖的 {dep_id} 不在团队内，将被忽略"
                )

    # Kahn 算法分层
    layers = []
    remaining = set(in_degree.keys())

    while remaining:
        # 找出所有入度为 0 的节点（当前层）
        current_layer = [
            agent_id for agent_id in remaining
            if in_degree[agent_id] == 0
        ]

        if not current_layer:
            # 存在循环依赖
            raise ValueError(
                f"Agent 依赖存在循环！剩余未处理: {remaining}"
            )

        layers.append(current_layer)

        # 移除当前层，更新入度
        for agent_id in current_layer:
            remaining.remove(agent_id)
            for dependent in dependents[agent_id]:
                in_degree[dependent] -= 1

    return layers
