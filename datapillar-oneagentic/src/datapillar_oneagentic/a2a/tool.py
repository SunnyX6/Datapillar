"""
A2A 委派工具

为 Agent 创建调用远程 A2A Agent 的工具。
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

from datapillar_oneagentic.a2a.client import A2AClient, A2AResult, TaskState
from datapillar_oneagentic.a2a.config import A2AConfig

logger = logging.getLogger(__name__)


class A2ADelegateInput(BaseModel):
    """A2A 委派工具输入"""

    task: str = Field(description="要委派给远程 Agent 的任务描述")
    context: str = Field(default="", description="额外的上下文信息")


def create_a2a_tool(config: A2AConfig, name: str | None = None) -> StructuredTool:
    """
    创建 A2A 委派工具

    参数：
    - config: A2A 配置
    - name: 工具名称（默认从 AgentCard 获取）

    返回：
    - LangChain StructuredTool

    使用示例：
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent-card.json",
    )
    tool = create_a2a_tool(config, name="call_data_analyst")

    # Agent 可以使用这个工具调用远程 Agent
    result = await tool.ainvoke({"task": "分析销售数据"})
    ```
    """

    async def delegate_to_a2a(task: str, context: str = "") -> str:
        """委派任务到远程 A2A Agent"""
        async with A2AClient(config) as client:
            # 获取 AgentCard
            try:
                card = await client.fetch_agent_card()
                logger.info(f"委派任务到: {card.name}")
            except Exception as e:
                if config.fail_fast:
                    raise
                return f"无法连接到远程 Agent: {e}"

            # 构建完整任务描述
            full_task = task
            if context:
                full_task = f"{task}\n\n上下文信息：\n{context}"

            # 发送任务
            result = await client.send_task(full_task)

            # 处理结果
            if result.status == TaskState.COMPLETED:
                return result.result

            if result.status == TaskState.FAILED:
                return f"远程 Agent 执行失败: {result.error}"

            if result.status == TaskState.INPUT_REQUIRED:
                return f"远程 Agent 需要更多信息: {result.result}"

            return f"远程 Agent 状态: {result.status}, 结果: {result.result}"

    # 工具名称
    tool_name = name or f"delegate_to_{config.endpoint.split('/')[-1].replace('.', '_')}"

    return StructuredTool.from_function(
        func=delegate_to_a2a,
        coroutine=delegate_to_a2a,
        name=tool_name,
        description=f"委派任务到远程 A2A Agent ({config.endpoint})",
        args_schema=A2ADelegateInput,
    )


async def create_a2a_tools_from_configs(
    configs: list[A2AConfig],
) -> list[StructuredTool]:
    """
    从配置列表批量创建 A2A 工具

    参数：
    - configs: A2A 配置列表

    返回：
    - 工具列表
    """
    tools = []

    for config in configs:
        try:
            # 获取 AgentCard 以确定工具名称
            async with A2AClient(config) as client:
                card = await client.fetch_agent_card()
                tool_name = f"delegate_to_{card.name.lower().replace(' ', '_')}"
                tool = create_a2a_tool(config, name=tool_name)
                tools.append(tool)
                logger.info(f"创建 A2A 工具: {tool_name} -> {config.endpoint}")

        except Exception as e:
            if config.fail_fast:
                raise
            logger.warning(f"跳过不可用的 A2A Agent: {config.endpoint}, 错误: {e}")

    return tools
