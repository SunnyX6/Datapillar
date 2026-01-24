"""
ReAct Controller - 规划-执行-反思 控制器

在 Orchestrator 层面控制 ReAct 循环：
1. 规划：调用 Planner 生成任务计划
2. 执行：按计划调度 Agent（通过设置 active_agent）
3. 反思：调用 Reflector 评估结果，决定下一步

路由机制：
- Controller 通过 StateBuilder 更新 active_agent 来指定下一个执行的 Agent
- Graph 的 conditional_edges 根据 active_agent 路由到具体 Agent 节点
- Agent 执行完后返回 react_controller，继续循环

使用示例：
```python
from datapillar_oneagentic.core.graphs.react.controller import react_controller_node

# 在 Graph 中添加节点
graph.add_node("react_controller", react_controller_node)
```
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import AIMessage

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind, ProcessStage
from datapillar_oneagentic.core.graphs.react.planner import create_plan, replan
from datapillar_oneagentic.core.graphs.react.reflector import decide_next_action, reflect
from datapillar_oneagentic.core.graphs.react.schemas import Plan
from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.state import StateBuilder

logger = logging.getLogger(__name__)

# 系统异常最大快速重试次数
MAX_ERROR_RETRIES = 3

# 最大重规划次数（防止无限循环）
MAX_REPLAN_DEPTH = 5


async def react_controller_node(
    state: Blackboard,
    *,
    llm: Any,
    agent_ids: list[str],
    agent_specs: list[AgentSpec],
) -> dict:
    """
    ReAct 控制器节点

    根据当前状态决定下一步：
    1. 没有 goal -> 提取 goal，创建计划
    2. 有计划，有待执行任务 -> 设置 active_agent 执行下一个任务
    3. 有计划，当前任务完成 -> 反思
    4. 反思后 -> 根据反思结果设置下一步

    Args:
        state: Blackboard 状态
        llm: LLM 实例
        agent_ids: 可用的 Agent ID 列表
        agent_specs: 可用的 Agent 规格列表

    Returns:
        dict: 状态更新（通过 active_agent 控制路由）
    """
    sb = StateBuilder(state)

    # 1. 提取或获取 goal
    goal = sb.react.snapshot().goal
    if not goal:
        goal = _extract_goal_from_messages(sb)
        if not goal:
            logger.warning("无法提取用户目标，结束流程")
            sb.routing.clear_active()
            return sb.patch()
    sb.react.save_goal(goal)

    # 2. 获取或创建 Plan
    plan_data = sb.react.snapshot().plan
    if plan_data:
        plan = Plan.model_validate(plan_data)
    else:
        logger.info(f"ReAct: 开始规划 - {goal[:100]}...")
        plan = await create_plan(goal=goal, llm=llm, available_agents=agent_specs)
        logger.info(f"ReAct: 规划完成，共 {len(plan.tasks)} 个任务")
        sb.react.save_plan(plan.model_dump(mode="json"))

    # 3. 迭代决定下一步（避免递归）
    replan_count = 0

    while True:
        action = _decide_next_step(
            sb=sb,
            plan=plan,
            agent_ids=agent_ids,
        )

        # 如果有明确的下一步，直接返回
        if action != "reflect":
            sb.react.save_plan(plan.model_dump(mode="json"))
            return sb.patch()

        # 需要反思
        logger.info("ReAct: 进入反思阶段...")
        last_result = _extract_result_summary(sb)

        reflection = await reflect(
            goal=goal,
            plan=plan,
            llm=llm,
            last_result_summary=last_result,
        )

        logger.info(f"ReAct: 反思完成 - next_action={reflection.next_action}")

        # 根据反思结果处理
        next_action = decide_next_action(plan=plan, reflection=reflection)

        if next_action == "end":
            # 结束（成功或失败）
            status = ExecutionStatus.COMPLETED if reflection.is_complete() else ExecutionStatus.FAILED
            plan.status = status
            plan.stage = ProcessStage.REFLECTING
            logger.info(f"ReAct: 流程结束 - {status}")
            sb.react.save_plan(plan.model_dump(mode="json"))
            sb.react.save_reflection(reflection.model_dump(mode="json"))
            sb.routing.clear_active()
            return sb.patch()

        if next_action == "planner":
            # 重新规划
            replan_count += 1
            if replan_count > MAX_REPLAN_DEPTH:
                logger.error(f"ReAct: 重规划次数超过 {MAX_REPLAN_DEPTH} 次，强制结束")
                plan.status = ExecutionStatus.FAILED
                plan.stage = ProcessStage.REFLECTING
                sb.react.save_plan(plan.model_dump(mode="json"))
                sb.routing.clear_active()
                return sb.patch()

            logger.info(f"ReAct: 重新规划 ({replan_count}/{MAX_REPLAN_DEPTH})...")
            plan = await replan(
                plan=plan,
                reflection_summary=reflection.summary,
                llm=llm,
                available_agents=agent_specs,
            )
            # 继续循环，基于新计划决定下一步
            continue

        if next_action == "executor":
            # 继续执行或重试
            if reflection.should_retry():
                current_task = plan.get_current_task()
                if current_task:
                    current_task.status = ExecutionStatus.PENDING
                    current_task.error = None
                    logger.info(f"ReAct: 重试任务 {current_task.id}")
            # 继续循环
            continue

        # 未知状态，结束
        logger.warning("ReAct: 未知状态，结束流程")
        sb.react.save_plan(plan.model_dump(mode="json"))
        sb.routing.clear_active()
        return sb.patch()


def _decide_next_step(
    *,
    sb: StateBuilder,
    plan: Plan,
    agent_ids: list[str],
) -> str:
    """
    根据计划状态决定下一步

    返回：
    - "execute"  执行 Agent
    - "finalize" 结束
    - "reflect"  需要反思
    """
    # 检查是否有进行中的任务
    current_task = plan.get_current_task()
    if current_task and current_task.status == ExecutionStatus.RUNNING:
        # 任务正在执行，检查是否刚完成（从 agent 返回）
        routing = sb.routing.snapshot()
        if routing.active_agent is None:
            # Agent 执行完毕，检查执行结果
            last_status = routing.last_status
            last_error = routing.last_error
            last_failure_kind = routing.last_failure_kind
            error_retry_count = sb.react.snapshot().error_retry_count

            if last_status == ExecutionStatus.COMPLETED:
                result_summary = _extract_result_summary(sb)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: 任务 {current_task.id} 成功完成")
                error_retry_count = 0
                sb.react.set_error_retry_count(error_retry_count)

            elif last_status == ExecutionStatus.FAILED and last_failure_kind == FailureKind.SYSTEM:
                # 系统异常：快速重试
                error_retry_count += 1
                if error_retry_count < MAX_ERROR_RETRIES:
                    logger.warning(
                        f"ReAct: 任务 {current_task.id} 系统异常 ({error_retry_count}/{MAX_ERROR_RETRIES})，"
                        f"快速重试: {last_error}"
                    )
                    current_task.status = ExecutionStatus.PENDING
                    # 验证 agent_id 有效
                    agent_id = current_task.assigned_agent
                    if agent_id not in agent_ids:
                        logger.error(f"ReAct: Agent {agent_id} 不在可用列表中")
                        current_task.mark_failed(f"Agent {agent_id} 不存在")
                        return "reflect"
                    sb.routing.activate(agent_id)
                    sb.memory.append_task_instruction(
                        task_id=current_task.id,
                        description=current_task.description,
                    )
                    sb.react.set_error_retry_count(error_retry_count)
                    return "execute"
                else:
                    logger.error(
                        f"ReAct: 任务 {current_task.id} 系统异常，重试 {MAX_ERROR_RETRIES} 次后仍失败"
                    )
                    current_task.mark_failed(f"系统异常（重试 {MAX_ERROR_RETRIES} 次）: {last_error}")
                    plan.status = ExecutionStatus.FAILED
                    plan.stage = ProcessStage.REFLECTING
                    sb.routing.clear_active()
                    sb.react.set_error_retry_count(0)
                    return "finalize"

            elif last_status == ExecutionStatus.FAILED:
                current_task.mark_failed(last_error or "业务失败")
                logger.warning(f"ReAct: 任务 {current_task.id} 业务失败: {last_error}")
                error_retry_count = 0
                sb.react.set_error_retry_count(error_retry_count)

            else:
                result_summary = _extract_result_summary(sb)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: 任务 {current_task.id} 完成 (状态: {last_status})")
                error_retry_count = 0
                sb.react.set_error_retry_count(error_retry_count)

        else:
            # 任务还在执行（不应该走到这里，但做个保护）
            return "execute"

    # 检查是否所有任务都已完成
    if plan.is_all_completed():
        logger.info("ReAct: 所有任务已完成，结束流程")
        plan.status = ExecutionStatus.COMPLETED
        plan.stage = ProcessStage.EXECUTING
        sb.routing.clear_active()
        return "finalize"

    # 检查是否有待执行任务（无失败任务时才继续）
    if not plan.has_failed_task():
        next_task = plan.get_next_task()
        if next_task:
            next_task.mark_running()
            plan.current_task_id = next_task.id
            plan.stage = ProcessStage.EXECUTING

            # 验证 agent_id 有效
            agent_id = next_task.assigned_agent
            if agent_id not in agent_ids:
                logger.error(f"ReAct: Agent {agent_id} 不在可用列表中: {agent_ids}")
                next_task.mark_failed(f"Agent {agent_id} 不存在")
                return "reflect"

            logger.info(f"ReAct: 执行任务 {next_task.id} - {next_task.description[:50]}...")
            logger.info(f"  分配给: {agent_id}")
            sb.routing.activate(agent_id)
            sb.memory.append_task_instruction(
                task_id=next_task.id,
                description=next_task.description,
            )
            return "execute"
    else:
        logger.info("ReAct: 检测到任务失败，跳过后续任务，进入反思阶段...")

    # 需要反思
    plan.stage = ProcessStage.REFLECTING
    return "reflect"


def _extract_goal_from_messages(sb: StateBuilder) -> str | None:
    """从 messages 中提取用户目标"""
    return sb.memory.latest_user_text()


def _extract_result_summary(sb: StateBuilder) -> str:
    """从状态中提取最近执行结果摘要"""
    for msg in reversed(sb.memory.snapshot()):
        if isinstance(msg, AIMessage):
            return msg.content or ""

    deliverable_keys = sb.deliverables.snapshot().keys
    if deliverable_keys:
        return f"交付物: {deliverable_keys}"

    return "无执行结果"
