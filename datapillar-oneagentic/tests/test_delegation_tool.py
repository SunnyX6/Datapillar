from __future__ import annotations

from langgraph.types import Command

from datapillar_oneagentic.messages import Message, Messages, ToolCall
from datapillar_oneagentic.messages.adapters.langchain import to_langchain
from datapillar_oneagentic.tools.delegation import create_delegation_tool


def test_call_id() -> None:
    tool = create_delegation_tool(target_agent_id="worker", target_agent_name="Worker")
    state = {
        "messages": to_langchain(
            Messages(
                [
                    Message.assistant(
                        "",
                        tool_calls=[ToolCall(id="call_1", name=tool.name, args={})],
                    )
                ]
            )
        )
    }

    cmd = tool.func(task_description="handle task", state=state)

    assert isinstance(cmd, Command)
    assert cmd.goto == "worker"
    assert cmd.update["active_agent"] == "worker"
    assert cmd.update["assigned_task"] == "handle task"
    assert cmd.update["messages"][0].tool_call_id == "call_1"


def test_call_id2() -> None:
    tool = create_delegation_tool(target_agent_id="worker", target_agent_name="Worker")
    state = {"messages": to_langchain(Messages([Message.assistant("")]))}

    cmd = tool.func(task_description="handle task", state=state)
    assert cmd.update["messages"][0].tool_call_id == "unknown"
