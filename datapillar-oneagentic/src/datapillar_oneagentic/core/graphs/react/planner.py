"""
Planner - 规划器

职责：
- 接收用户目标
- 分解为可执行的任务列表
- 分配给合适的 Agent

使用示例：
```python
from datapillar_oneagentic.core.graphs.react.planner import create_plan

plan = await create_plan(
    goal="帮我分析销售数据并生成报告",
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
    解析 Planner 输出（严格模式）
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


def _build_planner_system_prompt(agent_list: str) -> str:
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
    """格式化 Agent 列表"""
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
    创建计划

    Args:
        goal: 用户目标
        llm: LLM 实例
        available_agents: 可用的 Agent 列表

    Returns:
        Plan: 生成的计划
    """
    if not available_agents:
        logger.warning("没有可用的 Agent，无法创建计划")
        return Plan(goal=goal, status=ExecutionStatus.FAILED, stage=ProcessStage.PLANNING)

    # 构建 prompt
    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_planner_system_prompt(agent_list)

    messages = ContextBuilder.build_react_planner_messages(
        system_prompt=system_prompt,
        goal=goal,
    )

    # 调用 LLM
    logger.info(f"Planner 开始规划: {goal[:100]}...")

    structured_llm = llm.with_structured_output(
        PlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # 解析结果（带 fallback）
    output = _parse_planner_output(result)

    # 构建 Plan
    plan = Plan(goal=goal, status=ExecutionStatus.RUNNING, stage=ProcessStage.EXECUTING)

    for task_output in output.tasks:
        # 转换依赖关系（从序号到 task_id）
        depends_on = [f"t{int(d)}" for d in task_output.depends_on if d.isdigit()]

        plan.add_task(
            description=task_output.description,
            assigned_agent=task_output.assigned_agent,
            depends_on=depends_on,
        )

    logger.info(f"Planner 完成规划: {len(plan.tasks)} 个任务")

    return plan


async def replan(
    *,
    plan: Plan,
    reflection_summary: str,
    llm: Any,
    available_agents: list[AgentSpec],
) -> Plan:
    """
    重新规划（根据反思结果调整计划）

    Args:
        plan: 原计划
        reflection_summary: 反思总结
        llm: LLM 实例
        available_agents: 可用的 Agent 列表

    Returns:
        Plan: 新计划
    """
    # 构建 prompt
    agent_list = _format_agent_list(available_agents)
    system_prompt = _build_planner_system_prompt(agent_list)

    # 添加原计划和反思信息
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

    messages = ContextBuilder.build_react_replan_messages(
        system_prompt=system_prompt,
        context=context,
    )

    logger.info(f"Planner 重新规划: {plan.goal[:100]}...")

    structured_llm = llm.with_structured_output(
        PlannerOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    # 解析结果（带 fallback）
    output = _parse_planner_output(result)

    # 构建新 Plan
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

    logger.info(f"Planner 重新规划完成: {len(new_plan.tasks)} 个任务")

    return new_plan
