"""
图构建模块

根据 Process 类型构建不同的执行图。
"""

from datapillar_oneagentic.core.graphs.dynamic import build_dynamic_graph
from datapillar_oneagentic.core.graphs.hierarchical import build_hierarchical_graph
from datapillar_oneagentic.core.graphs.parallel import build_parallel_graph
from datapillar_oneagentic.core.graphs.react import build_react_graph
from datapillar_oneagentic.core.graphs.sequential import build_sequential_graph
from datapillar_oneagentic.core.process import Process

__all__ = [
    "build_graph",
    "build_sequential_graph",
    "build_dynamic_graph",
    "build_hierarchical_graph",
    "build_parallel_graph",
    "build_react_graph",
]


def build_graph(
    *,
    process: Process,
    agent_specs: list,
    entry_agent_id: str,
    agent_ids: list[str],
    create_agent_node,
    create_parallel_layer_node,
    llm=None,
):
    """
    根据 Process 类型构建执行图

    Args:
        process: 执行模式
        agent_specs: Agent 规格列表
        entry_agent_id: 入口 Agent ID
        agent_ids: 所有 Agent ID 列表
        create_agent_node: 节点创建函数
        create_parallel_layer_node: 并行层节点创建函数
        llm: LLM 实例（REACT 模式需要）

    Returns:
        StateGraph 实例
    """
    if process == Process.REACT:
        if llm is None:
            raise ValueError("process=Process.REACT 时必须提供 llm 参数")
        return build_react_graph(
            agent_specs=agent_specs,
            agent_ids=agent_ids,
            create_agent_node=create_agent_node,
            llm=llm,
        )

    if process == Process.SEQUENTIAL:
        return build_sequential_graph(
            agent_specs=agent_specs,
            entry_agent_id=entry_agent_id,
            create_agent_node=create_agent_node,
        )
    elif process == Process.DYNAMIC:
        return build_dynamic_graph(
            agent_specs=agent_specs,
            agent_ids=agent_ids,
            create_agent_node=create_agent_node,
        )
    elif process == Process.HIERARCHICAL:
        return build_hierarchical_graph(
            agent_specs=agent_specs,
            entry_agent_id=entry_agent_id,
            agent_ids=agent_ids,
            create_agent_node=create_agent_node,
        )
    elif process == Process.PARALLEL:
        return build_parallel_graph(
            agent_specs=agent_specs,
            create_parallel_layer_node=create_parallel_layer_node,
        )
    else:
        raise ValueError(f"不支持的执行模式: {process}")
