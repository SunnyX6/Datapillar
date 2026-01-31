# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Graph builder module.

Builds execution graphs based on Process type.
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
    create_mapreduce_worker,
    create_mapreduce_reducer,
    llm=None,
    context_collector=None,
):
    """
    Build an execution graph based on Process.

    Args:
        process: Execution mode
        agent_specs: Agent spec list
        entry_agent_id: Entry agent ID
        agent_ids: All agent IDs
        create_agent_node: Node factory
        create_mapreduce_worker: MapReduce worker node factory
        create_mapreduce_reducer: MapReduce reducer node factory
        llm: LLM instance (required for REACT / MAPREDUCE)

    Returns:
        StateGraph instance
    """
    if process == Process.REACT:
        if llm is None:
            raise ValueError("llm is required when process=Process.REACT")
        return build_react_graph(
            agent_specs=agent_specs,
            agent_ids=agent_ids,
            create_agent_node=create_agent_node,
            llm=llm,
        )

    if process == Process.MAPREDUCE:
        if llm is None:
            raise ValueError("llm is required when process=Process.MAPREDUCE")
        return build_mapreduce_graph(
            agent_specs=agent_specs,
            agent_ids=agent_ids,
            create_mapreduce_worker=create_mapreduce_worker,
            create_mapreduce_reducer=create_mapreduce_reducer,
            llm=llm,
            context_collector=context_collector,
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
        raise ValueError(f"Unsupported execution mode: {process}")
