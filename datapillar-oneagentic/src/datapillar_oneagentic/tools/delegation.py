"""
委派工具

创建 Agent 间的委派（delegation）工具。
当 Agent 调用委派工具时，返回 Command 控制流程跳转。

设计原则：
- 委派工具由框架根据 spec.can_delegate_to 自动创建
- Agent 不需要手动创建委派工具
- 委派决策由 LLM 自主判断
"""

from __future__ import annotations

import logging
from typing import Annotated

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.tools import BaseTool, tool
from langgraph.prebuilt import InjectedState
from langgraph.types import Command

logger = logging.getLogger(__name__)


def create_delegation_tool(
    *,
    target_agent_id: str,
    target_agent_name: str = "",
    description: str | None = None,
) -> BaseTool:
    """
    创建委派工具

    当 LLM 决定将任务委派给其他 Agent 时调用。
    返回 Command 控制 LangGraph 流程跳转。

    参数：
    - target_agent_id: 目标 Agent ID
    - target_agent_name: 目标 Agent 名称（用于描述）
    - description: 工具描述（给 LLM 看）

    返回：
    - BaseTool: 委派工具
    """
    tool_name = f"delegate_to_{target_agent_id}"
    display_name = target_agent_name or target_agent_id
    tool_description = description or f"将任务委派给 {display_name} 处理"

    @tool(tool_name, description=tool_description)
    def delegation_tool(
        task_description: Annotated[str, "详细描述需要委派的任务，包含所有相关上下文"],
        state: Annotated[dict, InjectedState],
    ) -> Command:
        """执行委派"""
        # 从 state 获取 tool_call_id
        messages = state.get("messages", [])
        tool_call_id = _extract_tool_call_id(messages, tool_name)
        user_message = _extract_last_user_message(messages)
        assistant_message = _extract_last_tool_call_message(messages, tool_name)

        if user_message and user_message not in task_description:
            task_description = (
                f"{task_description}\n\n## 用户原始输入\n{user_message}"
            )

        # 创建确认消息
        tool_message = ToolMessage(
            content=f"已委派给 {display_name}",
            name=tool_name,
            tool_call_id=tool_call_id or "unknown",
        )

        logger.info(f"🔄 委派: → {target_agent_id}, 任务: {task_description[:100]}...")

        update_messages = []
        if assistant_message is not None:
            update_messages.append(assistant_message)
        update_messages.append(tool_message)

        # 返回 Command 跳转到目标 Agent
        # 注意：不使用 graph=Command.PARENT，因为我们的节点不是子图
        return Command(
            goto=target_agent_id,
            update={
                "messages": update_messages,
                "active_agent": target_agent_id,
                "assigned_task": task_description,
            },
        )

    return delegation_tool


def _extract_tool_call_id(messages: list, tool_name: str) -> str | None:
    """从消息中提取 tool_call_id"""
    for msg in reversed(messages):
        if hasattr(msg, "tool_calls") and msg.tool_calls:
            for tc in msg.tool_calls:
                if tc.get("name") == tool_name:
                    return tc.get("id")
    return None


def _extract_last_user_message(messages: list) -> str | None:
    """提取最后一条用户输入，用于补全委派任务上下文"""
    for msg in reversed(messages):
        if isinstance(msg, HumanMessage):
            content = getattr(msg, "content", "")
            if content:
                return str(content)
    return None


def _extract_last_tool_call_message(messages: list, tool_name: str) -> AIMessage | None:
    """提取包含指定工具调用的最后一条 AIMessage"""
    for msg in reversed(messages):
        if not isinstance(msg, AIMessage):
            continue
        tool_calls = getattr(msg, "tool_calls", None) or []
        for tc in tool_calls:
            name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", None)
            if name == tool_name:
                return msg
    return None


def create_delegation_tools(
    can_delegate_to: list[str],
    agent_names: dict[str, str] | None = None,
) -> list[BaseTool]:
    """
    批量创建委派工具

    参数：
    - can_delegate_to: 可委派的目标 Agent ID 列表
    - agent_names: Agent ID → 名称的映射（可选）

    返回：
    - 委派工具列表
    """
    agent_names = agent_names or {}
    tools = []

    for agent_id in can_delegate_to:
        tool = create_delegation_tool(
            target_agent_id=agent_id,
            target_agent_name=agent_names.get(agent_id, ""),
        )
        tools.append(tool)

    return tools
