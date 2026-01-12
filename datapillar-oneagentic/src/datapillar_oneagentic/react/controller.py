"""
ReAct Controller - 规划-执行-反思 控制器

在 Orchestrator 层面控制 ReAct 循环：
1. 规划：调用 Planner 生成任务计划
2. 执行：按计划调度 Agent（通过设置 active_agent）
3. 反思：调用 Reflector 评估结果，决定下一步

使用示例：
```python
from datapillar_oneagentic.react.controller import react_controller_node

# 在 Orchestrator 中添加节点
graph.add_node("react_controller", react_controller_node)
```
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from datapillar_oneagentic.react.planner import create_plan, replan
from datapillar_oneagentic.react.reflector import decide_next_action, reflect
from datapillar_oneagentic.react.schemas import Plan, Reflection
from datapillar_oneagentic.state.blackboard import Blackboard

logger = logging.getLogger(__name__)

# 系统异常最大快速重试次数
MAX_ERROR_RETRIES = 3


async def react_controller_node(state: Blackboard, *, llm: Any) -> Command:
    """
    ReAct 控制器节点

    根据当前状态决定下一步：
    1. 没有 goal -> 提取 goal，创建计划
    2. 有计划，有待执行任务 -> 执行下一个任务
    3. 有计划，当前任务完成 -> 反思
    4. 反思后 -> 根据反思结果路由

    Args:
        state: Blackboard 状态
        llm: LLM 实例

    Returns:
        Command: 路由指令
    """
    # 1. 提取或获取 goal
    goal = state.get("goal")
    if not goal:
        goal = _extract_goal_from_messages(state)
        if not goal:
            logger.warning("无法提取用户目标，结束流程")
            return Command(goto="finalize", update={"active_agent": None})

    # 2. 获取或创建 Plan
    plan_data = state.get("plan")
    if plan_data:
        plan = Plan.model_validate(plan_data)
    else:
        logger.info(f"ReAct: 开始规划 - {goal[:100]}...")
        plan = await create_plan(goal=goal, llm=llm)
        logger.info(f"ReAct: 规划完成，共 {len(plan.tasks)} 个任务")

    # 3. 检查计划状态，决定下一步
    return await _decide_next_step(
        state=state,
        goal=goal,
        plan=plan,
        llm=llm,
    )


async def _decide_next_step(
    *,
    state: Blackboard,
    goal: str,
    plan: Plan,
    llm: Any,
) -> Command:
    """
    根据计划状态决定下一步

    逻辑：
    1. 有进行中的任务 -> 继续执行（不做任何操作）
    2. 有待执行任务 -> 执行下一个
    3. 全部完成或有失败 -> 反思
    4. 根据反思结果路由
    """
    # 检查是否有进行中的任务
    current_task = plan.get_current_task()
    if current_task and current_task.status == "in_progress":
        # 任务正在执行，检查是否刚完成（从 agent 返回）
        # 通过 active_agent 判断：如果 active_agent=None，说明 agent 执行完了
        if state.get("active_agent") is None:
            # Agent 执行完毕，检查执行结果
            last_status = state.get("last_agent_status")
            last_error = state.get("last_agent_error")
            error_retry_count = state.get("error_retry_count", 0)

            if last_status == "completed":
                # 任务成功，重置重试计数
                result_summary = _extract_result_summary(state)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: 任务 {current_task.id} 成功完成")
                error_retry_count = 0

            elif last_status == "error":
                # 系统异常：快速重试，不进入反思
                error_retry_count += 1
                if error_retry_count < MAX_ERROR_RETRIES:
                    logger.warning(
                        f"ReAct: 任务 {current_task.id} 系统异常 ({error_retry_count}/{MAX_ERROR_RETRIES})，"
                        f"快速重试: {last_error}"
                    )
                    # 快速重试：重置任务状态，立即重新执行
                    current_task.status = "pending"
                    return Command(
                        goto="agents",
                        update={
                            "goal": goal,
                            "plan": plan.model_dump(mode="json"),
                            "active_agent": current_task.assigned_agent,
                            "task_description": current_task.description,
                            "error_retry_count": error_retry_count,
                        },
                    )
                else:
                    # 超过重试次数，标记为失败并终止
                    logger.error(
                        f"ReAct: 任务 {current_task.id} 系统异常，重试 {MAX_ERROR_RETRIES} 次后仍失败: {last_error}"
                    )
                    current_task.mark_failed(f"系统异常（重试 {MAX_ERROR_RETRIES} 次）: {last_error}")
                    plan.status = "failed"
                    return Command(
                        goto="finalize",
                        update={
                            "goal": goal,
                            "plan": plan.model_dump(mode="json"),
                            "active_agent": None,
                            "error_retry_count": 0,
                        },
                    )

            elif last_status == "failed":
                # 业务失败：标记任务失败，后续进入反思
                current_task.mark_failed(last_error or "业务失败")
                logger.warning(f"ReAct: 任务 {current_task.id} 业务失败: {last_error}")
                error_retry_count = 0

            elif last_status == "needs_clarification":
                # 需要澄清：这个状态应该由 Graph 处理 interrupt
                logger.info(f"ReAct: 任务 {current_task.id} 需要澄清")
                error_retry_count = 0

            else:
                # 未知状态，默认标记为完成
                result_summary = _extract_result_summary(state)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: 任务 {current_task.id} 完成 (状态: {last_status})")
                error_retry_count = 0

        else:
            # 任务还在执行，继续等待
            return Command(
                goto="agents",
                update={
                    "goal": goal,
                    "plan": plan.model_dump(mode="json"),
                },
            )

    # 检查是否有待执行任务
    # 注意：如果有任务失败，直接进入反思，不继续执行
    if not plan.has_failed_task():
        next_task = plan.get_next_task()
        if next_task:
            # 执行下一个任务
            next_task.mark_in_progress()
            plan.current_task_id = next_task.id

            logger.info(f"ReAct: 执行任务 {next_task.id} - {next_task.description[:50]}...")
            logger.info(f"  分配给: {next_task.assigned_agent}")

            return Command(
                goto="agents",
                update={
                    "goal": goal,
                    "plan": plan.model_dump(mode="json"),
                    "active_agent": next_task.assigned_agent,
                    "task_description": next_task.description,
                },
            )
    else:
        logger.info("ReAct: 检测到任务失败，跳过后续任务，进入反思阶段...")

    # 没有待执行任务或有失败任务，进入反思
    logger.info("ReAct: 进入反思阶段...")

    # 获取最近执行结果
    last_result = _extract_result_summary(state)

    reflection = await reflect(
        goal=goal,
        plan=plan,
        llm=llm,
        last_result_summary=last_result,
    )

    logger.info(f"ReAct: 反思完成 - next_action={reflection.next_action}")

    # 根据反思结果路由
    return await _route_after_reflection(
        state=state,
        goal=goal,
        plan=plan,
        reflection=reflection,
        llm=llm,
    )


async def _route_after_reflection(
    *,
    state: Blackboard,
    goal: str,
    plan: Plan,
    reflection: Reflection,
    llm: Any,
) -> Command:
    """
    根据反思结果路由

    - complete: 完成，进入 finalize
    - fail: 失败，进入 finalize
    - continue: 继续执行下一个任务
    - retry: 重试当前任务
    - replan: 重新规划
    """
    next_action = decide_next_action(plan=plan, reflection=reflection)

    if next_action == "end":
        # 结束（成功或失败）
        status = "completed" if reflection.is_complete() else "failed"
        plan.status = status
        logger.info(f"ReAct: 流程结束 - {status}")

        return Command(
            goto="finalize",
            update={
                "goal": goal,
                "plan": plan.model_dump(mode="json"),
                "reflection": reflection.model_dump(mode="json"),
                "active_agent": None,
            },
        )

    if next_action == "planner":
        # 重新规划
        logger.info("ReAct: 重新规划...")
        new_plan = await replan(
            plan=plan,
            reflection_summary=reflection.summary,
            llm=llm,
        )

        # 递归调用，基于新计划决定下一步
        return await _decide_next_step(
            state=state,
            goal=goal,
            plan=new_plan,
            llm=llm,
        )

    if next_action == "executor":
        # 继续执行或重试
        if reflection.should_retry():
            # 重试：重置当前任务状态
            current_task = plan.get_current_task()
            if current_task:
                current_task.status = "pending"
                current_task.error = None
                logger.info(f"ReAct: 重试任务 {current_task.id}")

        # 递归调用，执行下一个任务
        return await _decide_next_step(
            state=state,
            goal=goal,
            plan=plan,
            llm=llm,
        )

    # 默认结束
    logger.warning("ReAct: 未知状态，结束流程")
    return Command(
        goto="finalize",
        update={
            "goal": goal,
            "plan": plan.model_dump(mode="json"),
            "reflection": reflection.model_dump(mode="json"),
            "active_agent": None,
        },
    )


def _extract_goal_from_messages(state: Blackboard) -> str | None:
    """从 messages 中提取用户目标"""
    messages = state.get("messages", [])
    for msg in messages:
        if isinstance(msg, HumanMessage):
            return msg.content
    return None


def _extract_result_summary(state: Blackboard) -> str:
    """从状态中提取最近执行结果摘要"""
    # 从 messages 中找最后一条 AI 消息
    messages = state.get("messages", [])
    for msg in reversed(messages):
        if isinstance(msg, AIMessage):
            return msg.content or ""

    # 从 deliverables 中提取
    deliverables = state.get("deliverables")
    if deliverables:
        return f"交付物: {list(deliverables.keys())}"

    return "无执行结果"
