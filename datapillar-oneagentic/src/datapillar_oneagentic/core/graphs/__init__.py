"""
图构建模块

根据 Process 类型构建不同的执行图。
"""

from datapillar_oneagentic.core.graphs.dynamic import build_dynamic_graph
from datapillar_oneagentic.core.graphs.hierarchical import build_hierarchical_graph
from datapillar_oneagentic.core.graphs.mapreduce.graph import build_mapreduce_graph
from datapillar_oneagentic.core.graphs.react.graph import build_react_graph
from datapillar_oneagentic.core.graphs.sequential import build_sequential_graph
from datapillar_oneagentic.core.process import Process

__all__ = [
    "build_graph",
    "build_sequential_graph",
    "build_dynamic_graph",
    "build_hierarchical_graph",
    "build_mapreduce_graph",
    "build_react_graph",
]


def build_graph(
    *,
    process: Process,
    agent_specs: list,
    entry_agent_id: str,
    agent_ids: list[str],
    create_agent_node,
    create_mapreduce_worker_node,
    create_mapreduce_reducer_node,
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
        create_mapreduce_worker_node: MapReduce Worker 节点创建函数
        create_mapreduce_reducer_node: MapReduce Reducer 节点创建函数
        llm: LLM 实例（REACT / MAPREDUCE 模式需要）

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

    if process == Process.MAPREDUCE:
        if llm is None:
            raise ValueError("process=Process.MAPREDUCE 时必须提供 llm 参数")
        return build_mapreduce_graph(
            agent_specs=agent_specs,
            agent_ids=agent_ids,
            create_mapreduce_worker_node=create_mapreduce_worker_node,
            create_mapreduce_reducer_node=create_mapreduce_reducer_node,
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
    else:
        raise ValueError(f"不支持的执行模式: {process}")
