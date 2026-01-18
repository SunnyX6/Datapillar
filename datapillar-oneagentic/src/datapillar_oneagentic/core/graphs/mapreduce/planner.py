"""
MapReduce Planner

职责：
- 接收用户目标
- 生成可并行执行的任务列表
- 为每个任务分配 Agent
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlan,
    MapReducePlannerOutput,
    MapReduceTask,
)
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


def _parse_planner_output(result: Any) -> MapReducePlannerOutput:
    """
    解析 Planner 输出（带 fallback）

    优先使用 LangChain 解析结果，失败时用内部解析器兜底。
    """
    if isinstance(result, MapReducePlannerOutput):
        return result

    if isinstance(result, dict):
        parsed = result.get("parsed")
        if isinstance(parsed, MapReducePlannerOutput):
            return parsed
        if isinstance(parsed, dict):
            return MapReducePlannerOutput.model_validate(parsed)

        raw = result.get("raw")
        if raw:
            content = getattr(raw, "content", None)
            if content:
                return parse_structured_output(content, MapReducePlannerOutput)

    raise ValueError(f"无法解析 MapReduce Planner 输出: {type(result)}")


MAPREDUCE_PLANNER_SYSTEM_PROMPT = """你是 MapReduce 任务规划器，负责把用户目标拆解为可并行执行的任务。

## 你的职责
1. 理解用户目标
2. 拆解为相互独立的任务（必须可并行执行）
3. 为每个任务分配最合适的 Agent
4. 给出每个任务的具体输入指令

## 可用的 Agent
{agent_list}

## 规划原则
1. 任务必须独立：不能有依赖关系
2. 任务粒度适中：不要过细或过粗
3. 输入具体明确：保证单个 Agent 可独立执行
4. 只使用可用 Agent（agent_id）

## 输出格式
- understanding: 你对用户目标的理解
- tasks: 任务列表，每个任务包含：
  - description: 任务描述
  - agent_id: 分配给哪个 Agent（使用 agent_id）
  - input: 交给该 Agent 的具体指令

## 示例
用户目标："分析销售数据并给出结论"

输出：
{{
  "understanding": "用户希望并行分析销售数据的多个维度，再汇总结论",
  "tasks": [
    {{"description": "分析销量趋势与异常", "agent_id": "analyst", "input": "分析销量趋势与异常，输出关键发现"}},
    {{"description": "分析客单价与客群结构", "agent_id": "analyst", "input": "分析客单价变化与客群结构，输出关键洞察"}},
    {{"description": "整理结论要点", "agent_id": "reporter", "input": "基于分析结果给出结论要点模板"}}
  ]
}}
"""


def _format_agent_list(agents: list[AgentSpec]) -> str:
    """格式化 Agent 列表"""
    lines = []
    for agent in agents:
        lines.append(f"- **{agent.id}** ({agent.name}): {agent.description or '无描述'}")
    return "\n".join(lines)


async def create_mapreduce_plan(
    *,
    goal: str,
    llm: Any,
    available_agents: list[AgentSpec],
    experience_context: str | None = None,
) -> MapReducePlan:
    """
    创建 MapReduce 计划

    Args:
        goal: 用户目标
        llm: LLM 实例
        available_agents: 可用的 Agent 列表（不含 reducer）
        experience_context: 经验上下文（可选）

    Returns:
        MapReducePlan
    """
    if not available_agents:
        raise ValueError("MapReduce Planner 没有可用 Agent")

    agent_list = _format_agent_list(available_agents)
    system_prompt = MAPREDUCE_PLANNER_SYSTEM_PROMPT.format(agent_list=agent_list)

    messages = [SystemMessage(content=system_prompt)]
    if experience_context:
        messages.append(SystemMessage(content=experience_context))
    messages.append(HumanMessage(content=f"用户目标：{goal}"))

    logger.info(f"MapReduce Planner 开始规划: {goal[:100]}...")

    structured_llm = llm.with_structured_output(
        MapReducePlannerOutput,
        method="json_mode",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)

    output = _parse_planner_output(result)
    if not output.tasks:
        raise ValueError("MapReduce Planner 输出为空任务列表")

    available_ids = {agent.id for agent in available_agents}
    tasks: list[MapReduceTask] = []
    for idx, task_output in enumerate(output.tasks, 1):
        agent_id = task_output.agent_id.strip()
        if agent_id not in available_ids:
            raise ValueError(f"MapReduce Planner 分配了无效 Agent: {agent_id}")
        task_input = task_output.input.strip()
        if not task_input:
            raise ValueError(f"MapReduce Planner 任务 {idx} 的 input 为空")

        tasks.append(
            MapReduceTask(
                id=f"t{idx}",
                description=task_output.description,
                agent_id=agent_id,
                input=task_input,
            )
        )

    logger.info(f"MapReduce Planner 完成规划: {len(tasks)} 个任务")
    return MapReducePlan(goal=goal, understanding=output.understanding, tasks=tasks)
