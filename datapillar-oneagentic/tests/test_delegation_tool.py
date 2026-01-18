from __future__ import annotations

from langchain_core.messages import AIMessage, ToolMessage
from langgraph.types import Command

from datapillar_oneagentic.tools.delegation import create_delegation_tool


def test_delegation_tool_builds_command_with_tool_call_id() -> None:
    tool = create_delegation_tool(target_agent_id="worker", target_agent_name="Worker")
    state = {
        "messages": [
            AIMessage(
                content="",
                tool_calls=[{"name": tool.name, "id": "call_1", "args": {}}],
            )
        ]
    }

    cmd = tool.func(task_description="处理任务", state=state)

    assert isinstance(cmd, Command)
    assert cmd.goto == "worker"
    assert cmd.update["active_agent"] == "worker"
    assert cmd.update["assigned_task"] == "处理任务"
    assert isinstance(cmd.update["messages"][0], AIMessage)
    assert isinstance(cmd.update["messages"][1], ToolMessage)
    assert cmd.update["messages"][1].tool_call_id == "call_1"


def test_delegation_tool_defaults_tool_call_id() -> None:
    tool = create_delegation_tool(target_agent_id="worker", target_agent_name="Worker")
    state = {"messages": [AIMessage(content="")]}

    cmd = tool.func(task_description="处理任务", state=state)
    assert isinstance(cmd.update["messages"][0], ToolMessage)
    assert cmd.update["messages"][0].tool_call_id == "unknown"
