"""
Reflector - 反思器

职责：
- 评估任务执行结果
- 判断目标是否达成
- 决定下一步行动（继续/重试/重新规划/完成/失败）

使用示例：
```python
from datapillar_oneagentic.react.reflector import reflect

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

from langchain_core.messages import HumanMessage, SystemMessage

from datapillar_oneagentic.react.schemas import Plan, Reflection, ReflectorOutput

logger = logging.getLogger(__name__)


REFLECTOR_SYSTEM_PROMPT = """你是一个智能反思器，负责评估任务执行结果并决定下一步行动。

## 你的职责
1. 评估当前执行进度
2. 判断用户目标是否达成
3. 分析存在的问题
4. 决定下一步行动

## 评估原则
1. 客观评估：基于实际执行结果，不要臆测
2. 关注目标：最终目的是达成用户目标，不是完成所有任务
3. 合理重试：如果是临时错误，可以重试；如果是方案问题，需要重新规划
4. 及时止损：如果多次失败且无法恢复，应该放弃

## 下一步行动选项（next_action）
- **continue**: 继续执行下一个任务（当前任务成功，还有待执行任务）
- **retry**: 重试当前任务（当前任务失败，但可能是临时错误）
- **replan**: 重新规划（当前方案有问题，需要调整计划）
- **complete**: 完成（目标已达成）
- **fail**: 失败（无法达成目标，放弃）

## 决策逻辑
1. 如果所有任务都完成，且目标达成 -> complete
2. 如果有任务失败，但重试次数未达上限 -> retry
3. 如果重试多次仍失败，且可以调整方案 -> replan
4. 如果重新规划次数达上限，或无法调整 -> fail
5. 如果当前任务完成，还有待执行任务 -> continue

## 输出格式
你必须输出以下 JSON 格式：
{{
  "goal_achieved": false,
  "confidence": 0.8,
  "summary": "当前进度总结",
  "issues": ["问题1", "问题2"],
  "suggestions": ["建议1", "建议2"],
  "next_action": "continue",
  "reason": "决策理由说明"
}}
"""


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
    context_parts = [
        f"## 用户目标\n{goal}",
        f"\n## 当前计划和执行状态\n{plan.to_prompt()}",
        f"\n## 重试信息\n- 已重试: {plan.retry_count} 次\n- 最大重试: {plan.max_retries} 次",
    ]

    if last_result_summary:
        context_parts.append(f"\n## 最近执行结果\n{last_result_summary}")

    # 添加状态摘要
    completed_count = sum(1 for t in plan.tasks if t.status == "completed")
    failed_count = sum(1 for t in plan.tasks if t.status == "failed")
    pending_count = sum(1 for t in plan.tasks if t.status == "pending")

    context_parts.append(
        f"\n## 进度摘要\n- 完成: {completed_count}/{len(plan.tasks)}\n"
        f"- 失败: {failed_count}\n- 待执行: {pending_count}"
    )

    context = "\n".join(context_parts)

    messages = [
        SystemMessage(content=REFLECTOR_SYSTEM_PROMPT),
        HumanMessage(content=context),
    ]

    logger.info("Reflector 开始反思...")

    structured_llm = llm.with_structured_output(ReflectorOutput)
    output: ReflectorOutput = await structured_llm.ainvoke(messages)

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
