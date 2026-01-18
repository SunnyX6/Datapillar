"""
MapReduce 执行图构建

流程：
planner → (fan-out) workers → reducer → END
"""

from __future__ import annotations

from typing import Any

from langchain_core.messages import HumanMessage
from langgraph.graph import END, StateGraph
from langgraph.types import Send

from datapillar_oneagentic.core.nodes import _extract_text
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
        messages = state.get("messages", [])
        query = ""
        for msg in reversed(messages):
            if isinstance(msg, HumanMessage):
                query = _extract_text(msg.content)
                break

        if not query:
            raise ValueError("MapReduce Planner 未找到用户输入")

        experience_context = state.get("experience_context")
        plan = await create_mapreduce_plan(
            goal=query,
            llm=llm,
            available_agents=worker_specs,
            experience_context=experience_context,
        )

        return {
            "mapreduce_goal": plan.goal,
            "mapreduce_understanding": plan.understanding,
            "mapreduce_tasks": [task.model_dump(mode="json") for task in plan.tasks],
            "mapreduce_results": [],
        }

    graph.add_node("mapreduce_planner", planner_node)

    worker_node = create_mapreduce_worker_node(worker_ids)
    graph.add_node("mapreduce_worker", worker_node)

    reducer_node = create_mapreduce_reducer_node(
        reducer_agent_id=reducer_spec.id,
        reducer_llm=llm,
        reducer_schema=reducer_spec.deliverable_schema,
    )
    graph.add_node("mapreduce_reducer", reducer_node)

    graph.set_entry_point("mapreduce_planner")

    def fan_out(state: Blackboard):
        tasks = state.get("mapreduce_tasks") or []
        if not tasks:
            return "mapreduce_reducer"

        sends = []
        base_payload = {
            "namespace": state.get("namespace"),
            "session_id": state.get("session_id"),
            "experience_context": state.get("experience_context"),
            "todo": state.get("todo"),
        }
        for task_data in tasks:
            task = MapReduceTask.model_validate(task_data)
            sends.append(
                Send(
                    "mapreduce_worker",
                    {
                        **base_payload,
                        "mapreduce_task": task.model_dump(mode="json"),
                        "mapreduce_results": [],
                    },
                )
            )
        return sends

    graph.add_conditional_edges(
        "mapreduce_planner",
        fan_out,
        ["mapreduce_worker", "mapreduce_reducer"],
    )

    graph.add_edge("mapreduce_worker", "mapreduce_reducer")
    graph.add_edge("mapreduce_reducer", END)

    return graph
