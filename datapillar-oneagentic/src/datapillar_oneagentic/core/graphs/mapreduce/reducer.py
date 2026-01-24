"""
MapReduce Reducer

职责：
- 汇总 Map 阶段结果
- 生成最终交付物（使用 Reducer 目标 schema）
"""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.context import ContextBuilder
from pydantic import BaseModel

from datapillar_oneagentic.exception import AgentErrorClassifier
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.graphs.mapreduce.schemas import MapReducePlan, MapReduceResult
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_reducer_output(result: Any, schema: type[BaseModel]) -> BaseModel:
    """
    解析 Reducer 输出（严格模式）
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
    """格式化 Map 阶段结果"""
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
    汇总 Map 阶段结果并输出最终交付物

    Args:
        plan: MapReduce 计划
        results: Map 阶段结果列表
        llm: LLM 实例
        output_schema: 最终交付物 Schema
        contexts: __context 分层块（可选）
    """
    if not results:
        raise ValueError("MapReduce Reducer 没有可用结果")

    failed_results = [result for result in results if result.status == ExecutionStatus.FAILED]
    if failed_results:
        failure_kind = failed_results[0].failure_kind or FailureKind.BUSINESS
        detail = "; ".join(
            f"{item.task_id}/{item.agent_id}:{item.error or '未知错误'}"
            for item in failed_results
        )
        raise AgentErrorClassifier.from_failure(
            agent_id=failed_results[0].agent_id,
            error=f"MapReduce Map 阶段失败: {detail}",
            failure_kind=failure_kind,
        )

    content = _format_results(plan, results)

    messages = ContextBuilder.build_mapreduce_reducer_messages(
        system_prompt=MAPREDUCE_REDUCER_SYSTEM_PROMPT,
        content=content,
        contexts=contexts or {},
    )

    logger.info("MapReduce Reducer 开始汇总结果...")

    structured_llm = llm.with_structured_output(
        output_schema,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    return _parse_reducer_output(result, output_schema)
