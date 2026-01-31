# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
ReAct controller (plan-execute-reflect).

Controls the ReAct loop at the orchestrator level:
1. Plan: call Planner to generate a task plan
2. Execute: schedule agents by setting active_agent
3. Reflect: call Reflector to evaluate results and choose next step

Routing:
- Controller updates active_agent via StateBuilder
- Graph conditional_edges route to the agent node
- Agents return to react_controller after execution

Example:
```python
from datapillar_oneagentic.core.graphs.react.controller import react_controller_node

# Add node in the graph
graph.add_node("react_controller", react_controller_node)
```
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind, ProcessStage
from datapillar_oneagentic.core.graphs.react.planner import create_plan, replan
from datapillar_oneagentic.core.graphs.react.reflector import decide_next_action, reflect
from datapillar_oneagentic.core.graphs.react.schemas import Plan
from datapillar_oneagentic.state.blackboard import Blackboard
from datapillar_oneagentic.state import StateBuilder

logger = logging.getLogger(__name__)

# Max fast-retry count for system errors.
MAX_ERROR_RETRIES = 3

# Max replan attempts to prevent infinite loops.
MAX_REPLAN_DEPTH = 5


async def react_controller_node(
    state: Blackboard,
    *,
    llm: Any,
    agent_ids: list[str],
    agent_specs: list[AgentSpec],
) -> dict:
    """
    ReAct controller node.

    Decide next steps based on current state:
    1. No goal -> extract goal and create plan
    2. Has plan and pending tasks -> set active_agent for next task
    3. Has plan and current task completed -> reflect
    4. After reflection -> set next action

    Args:
        state: Blackboard state
        llm: LLM instance
        agent_ids: available agent IDs
        agent_specs: available agent specs

    Returns:
        dict: state patch (active_agent controls routing)
    """
    sb = StateBuilder(state)

    # 1. Extract or load goal.
    goal = sb.react.snapshot().goal
    if not goal:
        goal = _extract_goal(sb)
        if not goal:
            logger.warning("Failed to extract user goal; ending flow")
            sb.routing.clear_active()
            return sb.patch()
    sb.react.save_goal(goal)

    # 2. Load or create plan.
    plan_data = sb.react.snapshot().plan
    if plan_data:
        plan = Plan.model_validate(plan_data)
    else:
        logger.info(f"ReAct: planning started - {goal[:100]}...")
        plan = await create_plan(goal=goal, llm=llm, available_agents=agent_specs)
        logger.info(f"ReAct: plan created with {len(plan.tasks)} tasks")
        sb.react.save_plan(plan.model_dump(mode="json"))

    # 3. Iteratively decide next step (avoid recursion).
    replan_count = 0

    while True:
        action = _decide_next_step(
            sb=sb,
            plan=plan,
            agent_ids=agent_ids,
        )

        # If next step is clear, return.
        if action != "reflect":
            sb.react.save_plan(plan.model_dump(mode="json"))
            return sb.patch()

        # Reflection required.
        logger.info("ReAct: entering reflection stage")
        last_result = _extract_result_summary(sb)

        reflection = await reflect(
            goal=goal,
            plan=plan,
            llm=llm,
            last_result_summary=last_result,
        )

        logger.info(f"ReAct: reflection completed - next_action={reflection.next_action}")

        # Handle reflection outcome.
        next_action = decide_next_action(plan=plan, reflection=reflection)

        if next_action == "end":
            # End (success or failure).
            status = ExecutionStatus.COMPLETED if reflection.is_complete() else ExecutionStatus.FAILED
            plan.status = status
            plan.stage = ProcessStage.REFLECTING
            logger.info(f"ReAct: flow ended - {status}")
            sb.react.save_plan(plan.model_dump(mode="json"))
            sb.react.save_reflection(reflection.model_dump(mode="json"))
            sb.routing.clear_active()
            return sb.patch()

        if next_action == "planner":
            # Replan.
            replan_count += 1
            if replan_count > MAX_REPLAN_DEPTH:
                logger.error(f"ReAct: replan limit exceeded ({MAX_REPLAN_DEPTH}); forced stop")
                plan.status = ExecutionStatus.FAILED
                plan.stage = ProcessStage.REFLECTING
                sb.react.save_plan(plan.model_dump(mode="json"))
                sb.routing.clear_active()
                return sb.patch()

            logger.info(f"ReAct: replan ({replan_count}/{MAX_REPLAN_DEPTH})...")
            plan = await replan(
                plan=plan,
                reflection_summary=reflection.summary,
                llm=llm,
                available_agents=agent_specs,
            )
            # Continue loop based on new plan.
            continue

        if next_action == "executor":
            # Continue or retry.
            if reflection.should_retry():
                current_task = plan.get_current_task()
                if current_task:
                    current_task.status = ExecutionStatus.PENDING
                    current_task.error = None
                    logger.info(f"ReAct: retry task {current_task.id}")
            # Continue loop.
            continue

        # Unknown state: end.
        logger.warning("ReAct: unknown state, ending flow")
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
    Decide next step based on plan state.

    Returns:
    - "execute": execute agent
    - "finalize": end
    - "reflect": reflection required
    """
    # Check running task.
    current_task = plan.get_current_task()
    if current_task and current_task.status == ExecutionStatus.RUNNING:
        # Task running; check if it just completed (from agent return).
        routing = sb.routing.snapshot()
        if routing.active_agent is None:
            # Agent finished; check execution result.
            last_status = routing.last_status
            last_error = routing.last_error
            last_failure_kind = routing.last_failure_kind
            error_retry_count = sb.react.snapshot().error_retry_count

            if last_status == ExecutionStatus.COMPLETED:
                result_summary = _extract_result_summary(sb)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: task {current_task.id} completed successfully")
                error_retry_count = 0
                sb.react.set_error_retry(error_retry_count)

            elif last_status == ExecutionStatus.ABORTED:
                logger.info(f"ReAct: task {current_task.id} aborted by user")
                current_task.status = ExecutionStatus.ABORTED
                current_task.error = "Aborted by user"
                plan.status = ExecutionStatus.ABORTED
                plan.stage = ProcessStage.REFLECTING
                sb.routing.clear_active()
                sb.react.set_error_retry(0)
                return "finalize"

            elif last_status == ExecutionStatus.FAILED and last_failure_kind == FailureKind.SYSTEM:
                # System error: fast retry.
                error_retry_count += 1
                if error_retry_count < MAX_ERROR_RETRIES:
                    logger.warning(
                        f"ReAct: task {current_task.id} system error "
                        f"({error_retry_count}/{MAX_ERROR_RETRIES}); fast retry: {last_error}"
                    )
                    current_task.status = ExecutionStatus.PENDING
                    # Validate agent_id.
                    agent_id = current_task.assigned_agent
                    if agent_id not in agent_ids:
                        logger.error(f"ReAct: Agent {agent_id} not in available list")
                        current_task.mark_failed(f"Agent {agent_id} not found")
                        return "reflect"
                    sb.routing.activate(agent_id)
                    sb.memory.append_task_instruction(
                        task_id=current_task.id,
                        description=current_task.description,
                    )
                    sb.react.set_error_retry(error_retry_count)
                    return "execute"
                else:
                    logger.error(
                        f"ReAct: task {current_task.id} system error; failed after {MAX_ERROR_RETRIES} retries"
                    )
                    current_task.mark_failed(
                        f"System error (retried {MAX_ERROR_RETRIES} times): {last_error}"
                    )
                    plan.status = ExecutionStatus.FAILED
                    plan.stage = ProcessStage.REFLECTING
                    sb.routing.clear_active()
                    sb.react.set_error_retry(0)
                    return "finalize"

            elif last_status == ExecutionStatus.FAILED:
                current_task.mark_failed(last_error or "Business failure")
                logger.warning(f"ReAct: task {current_task.id} business failure: {last_error}")
                error_retry_count = 0
                sb.react.set_error_retry(error_retry_count)

            else:
                result_summary = _extract_result_summary(sb)
                current_task.mark_completed(result_summary)
                logger.info(f"ReAct: task {current_task.id} completed (status: {last_status})")
                error_retry_count = 0
                sb.react.set_error_retry(error_retry_count)

        else:
            # Task still running (defensive guard).
            return "execute"

    # Check if all tasks completed.
    if plan.is_all_completed():
        logger.info("ReAct: all tasks completed; ending flow")
        plan.status = ExecutionStatus.COMPLETED
        plan.stage = ProcessStage.EXECUTING
        sb.routing.clear_active()
        return "finalize"

    # Check pending tasks (only if no failures).
    if not plan.has_failed_task():
        next_task = plan.get_next_task()
        if next_task:
            next_task.mark_running()
            plan.current_task_id = next_task.id
            plan.stage = ProcessStage.EXECUTING

            # Validate agent_id.
            agent_id = next_task.assigned_agent
            if agent_id not in agent_ids:
                logger.error(f"ReAct: Agent {agent_id} not in available list: {agent_ids}")
                next_task.mark_failed(f"Agent {agent_id} not found")
                return "reflect"

            logger.info(f"ReAct: execute task {next_task.id} - {next_task.description[:50]}...")
            logger.info(f"Assigned to: {agent_id}")
            sb.routing.activate(agent_id)
            sb.memory.append_task_instruction(
                task_id=next_task.id,
                description=next_task.description,
            )
            return "execute"
    else:
        logger.info("ReAct: task failure detected; skipping remaining tasks, entering reflection stage")

    # Reflection required.
    plan.stage = ProcessStage.REFLECTING
    return "reflect"


def _extract_goal(sb: StateBuilder) -> str | None:
    """Extract user goal from messages."""
    return sb.memory.latest_user_text()


def _extract_result_summary(sb: StateBuilder) -> str:
    """Extract the latest execution summary from state."""
    for msg in reversed(sb.memory.snapshot()):
        if msg.role == "assistant":
            return msg.content or ""

    deliverable_keys = sb.deliverables.snapshot().keys
    if deliverable_keys:
        return f"Deliverables: {deliverable_keys}"

    return "No execution result"
