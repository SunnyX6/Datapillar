# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Planner.

Responsibilities:
- Receive user goals
- Decompose into executable tasks
- Assign tasks to appropriate agents

Example:
```python
from datapillar_oneagentic.core.graphs.react.planner import create_plan

plan = await create_plan(
    goal="Analyze sales data and produce a report",
    llm=llm,
    available_agents=available_agents,
)
```
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.context import ContextBuilder

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.status import ExecutionStatus, ProcessStage
from datapillar_oneagentic.core.graphs.react.schemas import Plan, PlannerOutput
from datapillar_oneagentic.utils.prompt_format import format_code_block, format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_planner_output(result: Any) -> PlannerOutput:
    """
    Parse planner output (strict mode).
    """
    return parse_structured_output(result, PlannerOutput, strict=False)


PLANNER_OUTPUT_SCHEMA = """{
  "understanding": "...",
  "tasks": [
    {
      "description": "...",
      "assigned_agent": "...",
      "depends_on": []
    }
  ]
}"""


def _build_planner_prompt(agent_list: str) -> str:
    return format_markdown(
        title=None,
        sections=[
            (
                "Role",
                "You are a planning agent that breaks a user goal into executable tasks.",
            ),
            (
                "Responsibilities",
                [
                    "Understand the user goal.",
                    "Break the goal into executable tasks.",
                    "Assign each task to the best agent.",
                    "Define task dependencies when needed.",
                ],
            ),
            ("Available Agents", agent_list),
            (
                "Rules",
                [
                    "Keep task granularity reasonable.",
                    "Dependencies must be explicit.",
                    "Use agent_id from the list.",
                    "Order tasks logically.",
                ],
            ),
            ("Output (JSON)", format_code_block("json", PLANNER_OUTPUT_SCHEMA)),
        ],
    )


def _format_agent_list(agents: list[AgentSpec]) -> str:
    """Format agent list."""
    lines = []
    for agent in agents:
        lines.append(f"- **{agent.id}** ({agent.name}): {agent.description or 'No description'}")
    return "\n".join(lines)


async def create_plan(
    *,
    goal: str,
    llm: Any,
    available_agents: list[AgentSpec],
) -> Plan:
    """
    Create a plan.

    Args:
        goal: user goal
        llm: LLM instance
        available_agents: available agents

    Returns:
        Plan: generated plan
    """
    if not available_agents:
        logger.warning("No available agents; cannot create plan")
        return Plan(goal=goal, status=ExecutionStatus.FAILED, stage=ProcessStage.PLANNING)

    # Build prompt.
    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_planner_prompt(agent_list)

    messages = ContextBuilder.build_react_planner(
        system_prompt=system_prompt,
        goal=goal,
    )

    # Call LLM.
    logger.info(f"Planner started: {goal[:100]}...")

    structured_llm = llm.with_structured_output(
        PlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # Parse output (with fallback).
    output = _parse_planner_output(result)

    # Build Plan.
    plan = Plan(goal=goal, status=ExecutionStatus.RUNNING, stage=ProcessStage.EXECUTING)

    for task_output in output.tasks:
        # Convert dependencies (index to task_id).
        depends_on = [f"t{int(d)}" for d in task_output.depends_on if d.isdigit()]

        plan.add_task(
            description=task_output.description,
            assigned_agent=task_output.assigned_agent,
            depends_on=depends_on,
        )

    logger.info(f"Planner completed: {len(plan.tasks)} tasks")

    return plan


async def replan(
    *,
    plan: Plan,
    reflection_summary: str,
    llm: Any,
    available_agents: list[AgentSpec],
) -> Plan:
    """
    Replan based on reflection summary.

    Args:
        plan: original plan
        reflection_summary: reflection summary
        llm: LLM instance
        available_agents: available agents

    Returns:
        Plan: updated plan
    """
    # Build prompt.
    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_planner_prompt(agent_list)

    # Add original plan and reflection.
    context = format_markdown(
        title=None,
        sections=[
            ("User Goal", plan.goal),
            ("Current Plan", plan.to_prompt(include_title=False)),
            ("Reflection Summary", reflection_summary),
            (
                "Replan Request",
                [
                    "Adjust task order if needed.",
                    "Add or remove tasks if needed.",
                    "Change assigned agents if needed.",
                ],
            ),
        ],
    )

    messages = ContextBuilder.build_react_replan(
        system_prompt=system_prompt,
        context=context,
    )

    logger.info(f"Planner replan started: {plan.goal[:100]}...")

    structured_llm = llm.with_structured_output(
        PlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # Parse output (with fallback).
    output = _parse_planner_output(result)

    # Build new Plan.
    new_plan = Plan(
        goal=plan.goal,
        status=ExecutionStatus.RUNNING,
        stage=ProcessStage.EXECUTING,
        retry_count=plan.retry_count + 1,
        max_retries=plan.max_retries,
    )

    for task_output in output.tasks:
        depends_on = [f"t{int(d)}" for d in task_output.depends_on if d.isdigit()]
        new_plan.add_task(
            description=task_output.description,
            assigned_agent=task_output.assigned_agent,
            depends_on=depends_on,
        )

    logger.info(f"Planner replan completed: {len(new_plan.tasks)} tasks")

    return new_plan
