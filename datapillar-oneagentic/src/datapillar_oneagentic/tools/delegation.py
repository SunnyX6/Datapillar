"""
Delegation tools.

Create delegation tools between agents.
When an agent calls a delegation tool, it returns a Command to route the flow.

Design principles:
- Delegation tools are auto-created based on spec.can_delegate_to
- Agents do not create delegation tools manually
- Delegation is decided by the LLM
"""

from __future__ import annotations

import logging
from typing import Annotated

from langchain_core.tools import BaseTool, tool
from langgraph.prebuilt import InjectedState
from langgraph.types import Command

from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.utils.prompt_format import format_markdown

logger = logging.getLogger(__name__)


def create_delegation_tool(
    *,
    target_agent_id: str,
    target_agent_name: str = "",
    description: str | None = None,
) -> BaseTool:
    """
    Create a delegation tool.

    Called when the LLM decides to delegate the task to another agent.
    Returns a Command to control LangGraph flow routing.

    Args:
        target_agent_id: Target agent ID
        target_agent_name: Target agent name (for description)
        description: Tool description (for the LLM)

    Returns:
        BaseTool: Delegation tool
    """
    tool_name = f"delegate_to_{target_agent_id}"
    display_name = target_agent_name or target_agent_id
    tool_description = description or f"Delegate the task to {display_name}."

    @tool(tool_name, description=tool_description)
    def delegation_tool(
        task_description: Annotated[str, "Detailed task description with relevant context"],
        state: Annotated[dict, InjectedState],
    ) -> Command:
        """Execute delegation."""
        sb = StateBuilder(state)
        messages = sb.memory.raw_snapshot()
        tool_call_id = _extract_call_id(messages, tool_name)
        user_message = _extract_last_user(messages)
        if user_message and user_message not in task_description:
            user_block = format_markdown(
                title=None,
                sections=[("Original User Input", user_message)],
            )
            task_description = f"{task_description}\n\n{user_block}"

        # Create confirmation message.
        tool_message = Message.tool(
            content=f"Delegated to {display_name}",
            name=tool_name,
            tool_call_id=tool_call_id or "unknown",
        )

        logger.info(f"Delegation: {target_agent_id}, task: {task_description[:100]}...")

        update_messages = Messages([tool_message])
        # Tool messages must be written back to match tool_call_id.
        sb.memory.append_tool_messages(update_messages)
        sb.routing.activate(target_agent_id)
        sb.routing.assign_task(task_description)

        # Return a Command to jump to the target agent.
        # Note: do not use graph=Command.PARENT because nodes are not subgraphs.
        return Command(
            goto=target_agent_id,
            update=sb.patch(),
        )

    return delegation_tool


def _extract_call_id(messages: Messages, tool_name: str) -> str | None:
    """Extract tool_call_id from messages."""
    for msg in reversed(messages):
        for tc in _iter_tool_calls(msg):
            if tc.name == tool_name:
                return tc.id
    return None


def _extract_last_user(messages: Messages) -> str | None:
    """Return the last user input to enrich delegation context."""
    for msg in reversed(messages):
        if msg.role == "user" and msg.content:
            return msg.content
    return None


def _extract_last_tool(messages: Messages, tool_name: str) -> Message | None:
    """Return the last assistant message that called the tool."""
    for msg in reversed(messages):
        if msg.role != "assistant":
            continue
        for tc in _iter_tool_calls(msg):
            if tc.name == tool_name:
                return msg
    return None


def _iter_tool_calls(msg: Message) -> list:
    return msg.tool_calls or []


def create_delegation_tools(
    can_delegate_to: list[str],
    agent_names: dict[str, str] | None = None,
) -> list[BaseTool]:
    """
    Create delegation tools in batch.

    Args:
        can_delegate_to: Target agent IDs that can be delegated to
        agent_names: Optional mapping of agent ID to display name

    Returns:
        List of delegation tools
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
