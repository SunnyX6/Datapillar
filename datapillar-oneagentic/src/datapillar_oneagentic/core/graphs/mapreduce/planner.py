"""
MapReduce planner.

Responsibilities:
- Receive the user goal
- Generate parallelizable tasks
- Assign an agent to each task
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
    Parse planner output (strict mode).
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


def _build_planner_prompt(agent_list: str) -> str:
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
    """Format agent list."""
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
    Create a MapReduce plan.

    Args:
        goal: user goal
        llm: LLM instance
        available_agents: available agents (excluding reducer)
        contexts: _context blocks (optional)

    Returns:
        MapReducePlan
    """
    if not available_agents:
        raise ValueError("MapReduce planner has no available agents")

    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_planner_prompt(agent_list)

    messages = ContextBuilder.build_mapreduce_planner(
        system_prompt=system_prompt,
        goal=goal,
        contexts=contexts or {},
    )

    logger.info(f"MapReduce planner started: {goal[:100]}...")

    structured_llm = llm.with_structured_output(
        MapReducePlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    output = _parse_planner_output(result)
    if not output.tasks:
        raise ValueError("MapReduce planner output contains no tasks")

    available_ids = {agent.id for agent in available_agents}
    tasks: list[MapReduceTask] = []
    for idx, task_output in enumerate(output.tasks, 1):
        agent_id = task_output.agent_id.strip()
        if agent_id not in available_ids:
            raise ValueError(f"MapReduce planner assigned an invalid agent: {agent_id}")
        task_input = task_output.input.strip()
        if not task_input:
            raise ValueError(f"MapReduce planner task {idx} has empty input")

        tasks.append(
            MapReduceTask(
                id=f"t{idx}",
                description=task_output.description,
                agent_id=agent_id,
                input=task_input,
            )
        )

    logger.info(f"MapReduce planner completed: {len(tasks)} tasks")
    return MapReducePlan(goal=goal, understanding=output.understanding, tasks=tasks)
