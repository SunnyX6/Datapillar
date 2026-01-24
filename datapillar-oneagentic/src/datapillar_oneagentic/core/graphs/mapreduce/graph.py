"""
MapReduce 执行图构建

流程：
planner → (fan-out) workers → reducer → END
"""

from __future__ import annotations

from typing import Any

from langgraph.graph import END, StateGraph
from langgraph.types import Send

from datapillar_oneagentic.context import ContextCollector, ContextScenario
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.core.graphs.mapreduce.planner import create_mapreduce_plan
from datapillar_oneagentic.core.graphs.mapreduce.schemas import MapReduceTask
from datapillar_oneagentic.state.blackboard import Blackboard


def build_mapreduce_graph(
    *,
    agent_specs: list,
    agent_ids: list[str],
    create_mapreduce_worker_node,
    create_mapreduce_reducer_node,
    llm: Any,
    context_collector: ContextCollector | None = None,
) -> StateGraph:
    """
    构建 MapReduce 执行图

    Args:
        agent_specs: Agent 规格列表
        agent_ids: 所有 Agent ID 列表
        create_mapreduce_worker_node: Map Worker 节点创建函数
        create_mapreduce_reducer_node: Reducer 节点创建函数
        llm: LLM 实例（用于 planner/reducer）

    Returns:
        StateGraph 实例
    """
    if len(agent_specs) < 2:
        raise ValueError("MAPREDUCE 模式需要至少 2 个 Agent（最后一个作为 Reducer）")

    worker_specs = agent_specs[:-1]
    reducer_spec = agent_specs[-1]
    worker_ids = [spec.id for spec in worker_specs]

    graph = StateGraph(Blackboard)

    async def planner_node(state: Blackboard) -> dict:
        sb = StateBuilder(state)
        query = sb.memory.latest_user_text() or ""

        if not query:
            raise ValueError("MapReduce Planner 未找到用户输入")

        contexts: dict[str, str] = {}
        if context_collector is not None:
            contexts = await context_collector.collect(
                scenario=ContextScenario.MAPREDUCE_PLANNER,
                state=state,
                query=query,
                session_id=sb.session_id,
                spec=None,
            )
        plan = await create_mapreduce_plan(
            goal=query,
            llm=llm,
            available_agents=worker_specs,
            contexts=contexts,
        )
        sb.mapreduce.init_plan(
            goal=plan.goal,
            understanding=plan.understanding,
            tasks=[task.model_dump(mode="json") for task in plan.tasks],
        )
        return sb.patch()

    graph.add_node("mapreduce_planner", planner_node)

    worker_node = create_mapreduce_worker_node(worker_ids)
    graph.add_node("mapreduce_worker", worker_node)

    reducer_node_name = reducer_spec.id
    reducer_node = create_mapreduce_reducer_node(
        reducer_agent_id=reducer_spec.id,
        reducer_llm=llm,
        reducer_schema=reducer_spec.deliverable_schema,
    )
    graph.add_node(reducer_node_name, reducer_node)

    graph.set_entry_point("mapreduce_planner")

    def fan_out(state: Blackboard):
        sb = StateBuilder(state)
        tasks = sb.mapreduce.snapshot().tasks
        if not tasks:
            return reducer_node_name

        sends = []
        base_payload = {
            "namespace": sb.namespace,
            "session_id": sb.session_id,
        }
        for task_data in tasks:
            task = MapReduceTask.model_validate(task_data)
            sends.append(
                Send(
                    "mapreduce_worker",
                    {
                        **base_payload,
                        "mapreduce_task": task.model_dump(mode="json"),
                    },
                )
            )
        return sends

    graph.add_conditional_edges(
        "mapreduce_planner",
        fan_out,
        ["mapreduce_worker", reducer_node_name],
    )

    graph.add_edge("mapreduce_worker", reducer_node_name)
    graph.add_edge(reducer_node_name, END)

    return graph
