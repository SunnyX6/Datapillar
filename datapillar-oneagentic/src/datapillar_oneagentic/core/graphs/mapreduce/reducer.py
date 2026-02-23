# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
MapReduce reducer.

Responsibilities:
- Aggregate map phase results
- Produce the final deliverable (using reducer output schema)
"""

from __future__ import annotations

import json
import logging
from typing import Any

from pydantic import BaseModel

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.core.graphs.mapreduce.schemas import MapReducePlan, MapReduceResult
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.exception import AgentExecutionFailedException
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_reducer_output(result: Any, schema: type[BaseModel]) -> BaseModel:
    """
    Parse reducer output (strict mode).
    """
    return parse_structured_output(result, schema, strict=False)


MAPREDUCE_REDUCER_SYSTEM_PROMPT = format_markdown(
    title=None,
    sections=[
        (
            "Role",
            "You are the final reducer that aggregates map results into a structured deliverable.",
        ),
        (
            "Responsibilities",
            [
                "Combine all task results.",
                "Resolve conflicts and duplication.",
                "Produce the final structured output.",
            ],
        ),
        (
            "Rules",
            [
                "Use only the provided results; do not fabricate.",
                "If results conflict, prefer better-supported evidence and be cautious.",
                "Output must strictly match the target schema (JSON).",
            ],
        ),
    ],
)


def _format_results(plan: MapReducePlan, results: list[MapReduceResult]) -> str:
    """Format map phase results."""
    lines: list[str] = []

    for result in results:
        lines.append(f"- Task {result.task_id} / {result.agent_id} / {result.description}")
        lines.append(f"  Input: {result.input}")
        status_value = result.status.value if hasattr(result.status, "value") else result.status
        lines.append(f"  Status: {status_value}")
        if result.output is not None:
            lines.append(f"  Output: {json.dumps(result.output, ensure_ascii=False)}")
        if result.error:
            lines.append(f"  Error: {result.error}")
        lines.append("")

    body = "\n".join(lines).strip()
    return format_markdown(
        title=None,
        sections=[
            ("User Goal", plan.goal),
            ("Plan Understanding", plan.understanding),
            ("Map Results", body),
        ],
    )


async def reduce_map_results(
    *,
    plan: MapReducePlan,
    results: list[MapReduceResult],
    llm: Any,
    output_schema: type[BaseModel],
    contexts: dict[str, str] | None = None,
) -> BaseModel:
    """
    Aggregate map results and produce the final deliverable.

    Args:
        plan: MapReduce plan
        results: map phase results
        llm: LLM instance
        output_schema: final deliverable schema
        contexts: _context blocks (optional)
    """
    if not results:
        raise ValueError("MapReduce reducer has no available results")

    failed_results = [
        result
        for result in results
        if result.status in (ExecutionStatus.FAILED, ExecutionStatus.ABORTED)
    ]
    if failed_results:
        detail = "; ".join(
            f"{item.task_id}/{item.agent_id}:{item.error or 'Unknown error'}"
            for item in failed_results
        )
        raise AgentExecutionFailedException(
            f"MapReduce map phase failed: {detail}",
            agent_id=failed_results[0].agent_id,
        )

    content = _format_results(plan, results)

    messages = ContextBuilder.build_mapreduce_reducer(
        system_prompt=MAPREDUCE_REDUCER_SYSTEM_PROMPT,
        content=content,
        contexts=contexts or {},
    )

    logger.info("MapReduce reducer started aggregation")

    structured_llm = llm.with_structured_output(
        output_schema,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    return _parse_reducer_output(result, output_schema)
