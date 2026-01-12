"""
Planner - 规划器

职责：
- 接收用户目标
- 分解为可执行的任务列表
- 分配给合适的 Agent

使用示例：
```python
from datapillar_oneagentic.react.planner import create_plan

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

from langchain_core.messages import HumanMessage, SystemMessage

from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.react.schemas import Plan, PlannerOutput

logger = logging.getLogger(__name__)


PLANNER_SYSTEM_PROMPT = """你是一个智能规划器，负责将用户目标分解为可执行的任务。

## 你的职责
1. 理解用户目标
2. 分析需要哪些步骤来完成目标
3. 将步骤分配给合适的 Agent
4. 确定任务之间的依赖关系

## 可用的 Agent
{agent_list}

## 规划原则
1. 任务粒度适中：不要太细（一个 API 调用），也不要太粗（整个需求）
2. 依赖关系明确：如果任务 B 需要任务 A 的结果，必须声明依赖
3. 分配合理：根据 Agent 的能力选择最合适的 Agent
4. 顺序合理：考虑任务的逻辑顺序

## 输出格式
- understanding: 你对用户目标的理解
- tasks: 任务列表，每个任务包含：
  - description: 任务描述
  - assigned_agent: 分配给哪个 Agent（使用 agent_id）
  - depends_on: 依赖的任务序号列表（从 1 开始，如 ["1", "2"] 表示依赖第 1 和第 2 个任务）

## 示例
用户目标："帮我分析销售数据并生成报告"

输出：
{{
  "understanding": "用户想要分析销售数据并生成报告，需要先收集数据，然后分析，最后生成报告",
  "tasks": [
    {{"description": "收集和整理销售数据，确认数据范围和指标", "assigned_agent": "analyst", "depends_on": []}},
    {{"description": "对销售数据进行统计分析，发现趋势和异常", "assigned_agent": "analyst", "depends_on": ["1"]}},
    {{"description": "根据分析结果生成可视化报告", "assigned_agent": "reporter", "depends_on": ["2"]}}
  ]
}}
"""


def _format_agent_list(agents: list[AgentSpec]) -> str:
    """格式化 Agent 列表"""
    lines = []
    for agent in agents:
        lines.append(f"- **{agent.id}** ({agent.name}): {agent.description or '无描述'}")
    return "\n".join(lines)


async def create_plan(
    *,
    goal: str,
    llm: Any,
    available_agents: list[AgentSpec] | None = None,
) -> Plan:
    """
    创建计划

    Args:
        goal: 用户目标
        llm: LLM 实例
        available_agents: 可用的 Agent 列表（默认从 Registry 获取）

    Returns:
        Plan: 生成的计划
    """
    # 获取可用 Agent
    if available_agents is None:
        available_agents = [AgentRegistry.get(aid) for aid in AgentRegistry.list_ids()]
        available_agents = [a for a in available_agents if a is not None]

    # 过滤掉入口 Agent
    available_agents = [a for a in available_agents if not getattr(a, 'is_entry', False)]

    if not available_agents:
        logger.warning("没有可用的 Agent，无法创建计划")
        return Plan(goal=goal, status="failed")

    # 构建 prompt
    agent_list = _format_agent_list(available_agents)
    system_prompt = PLANNER_SYSTEM_PROMPT.format(agent_list=agent_list)

    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=f"用户目标：{goal}"),
    ]

    # 调用 LLM
    logger.info(f"Planner 开始规划: {goal[:100]}...")

    structured_llm = llm.with_structured_output(PlannerOutput)
    output: PlannerOutput = await structured_llm.ainvoke(messages)

    # 构建 Plan
    plan = Plan(goal=goal, status="executing")

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
    available_agents: list[AgentSpec] | None = None,
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
    # 获取可用 Agent
    if available_agents is None:
        available_agents = [AgentRegistry.get(aid) for aid in AgentRegistry.list_ids()]
        available_agents = [a for a in available_agents if a is not None]

    available_agents = [a for a in available_agents if not getattr(a, 'is_entry', False)]

    # 构建 prompt
    agent_list = _format_agent_list(available_agents)
    system_prompt = PLANNER_SYSTEM_PROMPT.format(agent_list=agent_list)

    # 添加原计划和反思信息
    context = f"""用户目标：{plan.goal}

## 原计划执行情况
{plan.to_prompt()}

## 反思结果
{reflection_summary}

## 请重新规划
根据反思结果，重新规划任务。可以：
- 调整任务顺序
- 增加/删除任务
- 更换 Agent
"""

    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=context),
    ]

    logger.info(f"Planner 重新规划: {plan.goal[:100]}...")

    structured_llm = llm.with_structured_output(PlannerOutput)
    output: PlannerOutput = await structured_llm.ainvoke(messages)

    # 构建新 Plan
    new_plan = Plan(
        goal=plan.goal,
        status="executing",
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
