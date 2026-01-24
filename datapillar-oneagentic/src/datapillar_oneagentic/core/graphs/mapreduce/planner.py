"""
MapReduce Planner

职责：
- 接收用户目标
- 生成可并行执行的任务列表
- 为每个任务分配 Agent
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.context import ContextBuilder

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlan,
    MapReducePlannerOutput,
    MapReduceTask,
)
from datapillar_oneagentic.utils.prompt_format import format_code_block, format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_planner_output(result: Any) -> MapReducePlannerOutput:
    """
    解析 Planner 输出（严格模式）
    """
    return parse_structured_output(result, MapReducePlannerOutput, strict=False)


MAPREDUCE_PLANNER_OUTPUT_SCHEMA = """{
  "understanding": "...",
  "tasks": [
    {
      "description": "...",
      "agent_id": "...",
      "input": "..."
    }
  ]
}"""


def _build_mapreduce_planner_system_prompt(agent_list: str) -> str:
    return format_markdown(
        title=None,
        sections=[
            (
                "Role",
                "You are a MapReduce planner that decomposes a goal into independent, parallel tasks.",
            ),
            (
                "Responsibilities",
                [
                    "Understand the user goal.",
                    "Decompose into independent tasks.",
                    "Assign the best agent for each task.",
                    "Provide clear task inputs.",
                ],
            ),
            ("Available Agents", agent_list),
            (
                "Rules",
                [
                    "Tasks must be independent with no dependencies.",
                    "Keep task granularity reasonable.",
                    "Task input must be specific and actionable.",
                    "Use agent_id from the list only.",
                ],
            ),
            ("Output (JSON)", format_code_block("json", MAPREDUCE_PLANNER_OUTPUT_SCHEMA)),
        ],
    )


def _format_agent_list(agents: list[AgentSpec]) -> str:
    """格式化 Agent 列表"""
    lines = []
    for agent in agents:
        lines.append(f"- **{agent.id}** ({agent.name}): {agent.description or 'No description'}")
    return "\n".join(lines)


async def create_mapreduce_plan(
    *,
    goal: str,
    llm: Any,
    available_agents: list[AgentSpec],
    contexts: dict[str, str] | None = None,
) -> MapReducePlan:
    """
    创建 MapReduce 计划

    Args:
        goal: 用户目标
        llm: LLM 实例
        available_agents: 可用的 Agent 列表（不含 reducer）
        contexts: __context 分层块（可选）

    Returns:
        MapReducePlan
    """
    if not available_agents:
        raise ValueError("MapReduce Planner 没有可用 Agent")

    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_mapreduce_planner_system_prompt(agent_list)

    messages = ContextBuilder.build_mapreduce_planner_messages(
        system_prompt=system_prompt,
        goal=goal,
        contexts=contexts or {},
    )

    logger.info(f"MapReduce Planner 开始规划: {goal[:100]}...")

    structured_llm = llm.with_structured_output(
        MapReducePlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    output = _parse_planner_output(result)
    if not output.tasks:
        raise ValueError("MapReduce Planner 输出为空任务列表")

    available_ids = {agent.id for agent in available_agents}
    tasks: list[MapReduceTask] = []
    for idx, task_output in enumerate(output.tasks, 1):
        agent_id = task_output.agent_id.strip()
        if agent_id not in available_ids:
            raise ValueError(f"MapReduce Planner 分配了无效 Agent: {agent_id}")
        task_input = task_output.input.strip()
        if not task_input:
            raise ValueError(f"MapReduce Planner 任务 {idx} 的 input 为空")

        tasks.append(
            MapReduceTask(
                id=f"t{idx}",
                description=task_output.description,
                agent_id=agent_id,
                input=task_input,
            )
        )

    logger.info(f"MapReduce Planner 完成规划: {len(tasks)} 个任务")
    return MapReducePlan(goal=goal, understanding=output.understanding, tasks=tasks)
