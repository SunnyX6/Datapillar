"""
Reflector - 反思器

职责：
- 评估任务执行结果
- 判断目标是否达成
- 决定下一步行动（继续/重试/重新规划/完成/失败）

使用示例：
```python
from datapillar_oneagentic.core.graphs.react.reflector import reflect

reflection = await reflect(
    goal="帮我分析销售数据并生成报告",
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
    解析 Reflector 输出（严格模式）
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
    反思

    Args:
        goal: 用户目标
        plan: 当前计划
        llm: LLM 实例
        last_result_summary: 最近一次执行结果摘要

    Returns:
        Reflection: 反思结果
    """
    # 构建上下文
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

    # 添加状态摘要
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

    messages = ContextBuilder.build_react_reflector_messages(
        system_prompt=REFLECTOR_SYSTEM_PROMPT,
        context=context,
    )

    logger.info("Reflector 开始反思...")

    structured_llm = llm.with_structured_output(
        ReflectorOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # 解析结果（带 fallback）
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
        f"Reflector 完成反思: goal_achieved={reflection.goal_achieved}, "
        f"next_action={reflection.next_action}"
    )

    return reflection


def decide_next_action(
    *,
    plan: Plan,
    reflection: Reflection,
) -> str:
    """
    根据反思结果决定下一步

    返回:
    - "planner": 重新规划
    - "executor": 继续执行
    - "end": 结束（成功或失败）
    """
    if reflection.is_complete():
        logger.info("ReAct 循环结束: 目标达成")
        return "end"

    if reflection.is_failed():
        logger.info("ReAct 循环结束: 目标失败")
        return "end"

    if reflection.should_replan():
        if plan.can_retry():
            logger.info("ReAct: 重新规划")
            return "planner"
        else:
            logger.info("ReAct: 重试次数已达上限，结束")
            return "end"

    if reflection.should_retry():
        logger.info("ReAct: 重试当前任务")
        return "executor"

    if reflection.should_continue():
        logger.info("ReAct: 继续执行下一个任务")
        return "executor"

    # 默认结束
    logger.warning("ReAct: 未知状态，默认结束")
    return "end"
