# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Reflector.

Responsibilities:
- Evaluate task execution results
- Decide whether the goal is achieved
- Choose next action (continue/retry/replan/complete/fail)

Example:
```python
from datapillar_oneagentic.core.graphs.react.reflector import reflect

reflection = await reflect(
    goal="Analyze sales data and produce a report",
    plan=plan,
    llm=llm,
)
```
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.context import ContextBuilder

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.core.graphs.react.schemas import Plan, Reflection, ReflectorOutput
from datapillar_oneagentic.utils.prompt_format import format_code_block, format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_reflector_output(result: Any) -> ReflectorOutput:
    """
    Parse reflector output (strict mode).
    """
    return parse_structured_output(result, ReflectorOutput, strict=False)


REFLECTOR_OUTPUT_SCHEMA = """{
  "goal_achieved": false,
  "confidence": 0.8,
  "summary": "...",
  "issues": ["..."],
  "suggestions": ["..."],
  "next_action": "continue",
  "reason": "..."
}"""

REFLECTOR_SYSTEM_PROMPT = format_markdown(
    title=None,
    sections=[
        (
            "Role",
            "You are a reflection agent that evaluates execution results and decides the next action.",
        ),
        (
            "Responsibilities",
            [
                "Evaluate the current progress.",
                "Decide whether the goal is achieved.",
                "Identify issues based on evidence.",
                "Choose the next action.",
            ],
        ),
        (
            "Rules",
            [
                "Base decisions on observed results, not speculation.",
                "Focus on achieving the user goal, not completing every task.",
                "Retry for transient errors; replan for strategy issues.",
                "Fail fast if recovery is unlikely.",
            ],
        ),
        (
            "Next Action Options",
            [
                "continue: current task succeeded and there are remaining tasks.",
                "retry: current task failed but may be transient.",
                "replan: the plan needs adjustment.",
                "complete: the goal is achieved.",
                "fail: the goal cannot be achieved.",
            ],
        ),
        (
            "Decision Logic",
            [
                "All tasks complete and goal achieved -> complete.",
                "Failed task and retries remain -> retry.",
                "Repeated failures but plan can be adjusted -> replan.",
                "Replan limit reached or no viable adjustment -> fail.",
                "Current task done and tasks remain -> continue.",
            ],
        ),
        ("Output (JSON)", format_code_block("json", REFLECTOR_OUTPUT_SCHEMA)),
    ],
)


async def reflect(
    *,
    goal: str,
    plan: Plan,
    llm: Any,
    last_result_summary: str | None = None,
) -> Reflection:
    """
    Reflect on execution status.

    Args:
        goal: user goal
        plan: current plan
        llm: LLM instance
        last_result_summary: last execution summary

    Returns:
        Reflection: reflection result
    """
    # Build context.
    context_sections: list[tuple[str, str | list[str]]] = [
        ("User Goal", goal),
        ("Plan Status", plan.to_prompt(include_title=False)),
        (
            "Retry Info",
            [
                f"Retried: {plan.retry_count}",
                f"Max retries: {plan.max_retries}",
            ],
        ),
    ]

    if last_result_summary:
        context_sections.append(("Latest Result", last_result_summary))

    # Add status summary.
    completed_count = sum(1 for t in plan.tasks if t.status == ExecutionStatus.COMPLETED)
    failed_count = sum(1 for t in plan.tasks if t.status == ExecutionStatus.FAILED)
    pending_count = sum(1 for t in plan.tasks if t.status == ExecutionStatus.PENDING)

    context_sections.append(
        (
            "Progress Summary",
            [
                f"Completed: {completed_count}/{len(plan.tasks)}",
                f"Failed: {failed_count}",
                f"Pending: {pending_count}",
            ],
        )
    )

    context = format_markdown(title=None, sections=context_sections)

    messages = ContextBuilder.build_react_reflector(
        system_prompt=REFLECTOR_SYSTEM_PROMPT,
        context=context,
    )

    logger.info("Reflector started")

    structured_llm = llm.with_structured_output(
        ReflectorOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # Parse output (with fallback).
    output = _parse_reflector_output(result)

    reflection = Reflection(
        goal_achieved=output.goal_achieved,
        confidence=output.confidence,
        summary=output.summary,
        issues=output.issues,
        suggestions=output.suggestions,
        next_action=output.next_action,
        reason=output.reason,
    )

    logger.info(
        "Reflector completed: goal_achieved=%s, next_action=%s",
        reflection.goal_achieved,
        reflection.next_action,
    )

    return reflection


def decide_next_action(
    *,
    plan: Plan,
    reflection: Reflection,
) -> str:
    """
    Decide next action based on reflection.

    Returns:
    - "planner": replan
    - "executor": continue execution
    - "end": end (success or failure)
    """
    if reflection.is_complete():
        logger.info("ReAct loop ended: goal achieved")
        return "end"

    if reflection.is_failed():
        logger.info("ReAct loop ended: goal failed")
        return "end"

    if reflection.should_replan():
        if plan.can_retry():
            logger.info("ReAct: replanning")
            return "planner"
        else:
            logger.info("ReAct: retry limit reached, ending")
            return "end"

    if reflection.should_retry():
        logger.info("ReAct: retry current task")
        return "executor"

    if reflection.should_continue():
        logger.info("ReAct: continue with next task")
        return "executor"

    # Default to ending.
    logger.warning("ReAct: unknown state, ending by default")
    return "end"
