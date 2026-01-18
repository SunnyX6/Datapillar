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

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel

from datapillar_oneagentic.exception import AgentErrorClassifier
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.graphs.mapreduce.schemas import MapReducePlan, MapReduceResult
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_reducer_output(result: Any, schema: type[BaseModel]) -> BaseModel:
    """
    解析 Reducer 输出（带 fallback）

    优先使用 LangChain 解析结果，失败时用内部解析器兜底。
    """
    if isinstance(result, schema):
        return result

    if isinstance(result, dict):
        parsed = result.get("parsed")
        if isinstance(parsed, schema):
            return parsed
        if isinstance(parsed, dict):
            return schema.model_validate(parsed)

        raw = result.get("raw")
        if raw:
            content = getattr(raw, "content", None)
            if content:
                return parse_structured_output(content, schema)

    raise ValueError(f"无法解析 MapReduce Reducer 输出: {type(result)}")


MAPREDUCE_REDUCER_SYSTEM_PROMPT = """你是 MapReduce 的最终聚合器，负责汇总多个 Agent 的结果并输出最终交付物。

## 你的职责
1. 综合所有任务结果
2. 解决冲突与重复
3. 输出最终结构化结果

## 规则
1. 只基于已给出的结果，不要编造
2. 若存在冲突，以证据更充分者为准，并在结果中体现谨慎
3. 输出必须严格符合目标 Schema（JSON）
"""


def _format_results(plan: MapReducePlan, results: list[MapReduceResult]) -> str:
    """格式化 Map 阶段结果"""
    lines = [
        f"用户目标：{plan.goal}",
        f"规划理解：{plan.understanding}",
        "",
        "## Map 结果汇总",
    ]

    for result in results:
        lines.append(f"- 任务 {result.task_id} / {result.agent_id} / {result.description}")
        lines.append(f"  输入：{result.input}")
        status_value = result.status.value if hasattr(result.status, "value") else result.status
        lines.append(f"  状态：{status_value}")
        if result.output is not None:
            lines.append(f"  输出：{json.dumps(result.output, ensure_ascii=False)}")
        if result.error:
            lines.append(f"  错误：{result.error}")
        lines.append("")

    return "\n".join(lines)


async def reduce_map_results(
    *,
    plan: MapReducePlan,
    results: list[MapReduceResult],
    llm: Any,
    output_schema: type[BaseModel],
    experience_context: str | None = None,
) -> BaseModel:
    """
    汇总 Map 阶段结果并输出最终交付物

    Args:
        plan: MapReduce 计划
        results: Map 阶段结果列表
        llm: LLM 实例
        output_schema: 最终交付物 Schema
        experience_context: 经验上下文（可选）
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

    messages = [SystemMessage(content=MAPREDUCE_REDUCER_SYSTEM_PROMPT)]
    if experience_context:
        messages.append(SystemMessage(content=experience_context))
    messages.append(HumanMessage(content=content))

    logger.info("MapReduce Reducer 开始汇总结果...")

    structured_llm = llm.with_structured_output(
        output_schema,
        method="json_mode",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    return _parse_reducer_output(result, output_schema)
