# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Datapillar OneAgentic hierarchical mode example.

Run:
    uv run python examples/quickstart_hierarchical.py
"""

from __future__ import annotations

import asyncio
import json

from pydantic import BaseModel

from datapillar_oneagentic import (
    AgentContext,
    Datapillar,
    DatapillarConfig,
    Process,
    agent,
    tool,
)
from datapillar_oneagentic.providers.llm import Provider


class TextOutput(BaseModel):
    text: str


@tool
def echo(text: str) -> str:
    """Echo text.

    Args:
        text: input text

    Returns:
        echoed result
    """
    return f"echo:{text}"


@agent(
    id="manager",
    name="Manager",
    deliverable_schema=TextOutput,
    description="Delegate tasks and summarize results",
)
class ManagerAgent:
    SYSTEM_PROMPT = """You are the manager.

Requirements:
1. If there is no worker output, you must call delegate_to_worker.
2. Once worker output is available, return the final result.

## Output requirements
Return JSON only (single object), no explanations or Markdown:
{"text": "Your summary"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        worker = await ctx.get_deliverable("worker")
        if worker:
            messages = (
                ctx.messages()
                .system(f"{self.SYSTEM_PROMPT}\nWorker output: {worker.get('text', '')}")
                .user(ctx.query)
            )
            return await ctx.get_structured_output(messages)

        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        await ctx.invoke_tools(messages)
        return TextOutput(text="delegated")


@agent(
    id="worker",
    name="Worker",
    deliverable_schema=TextOutput,
    tools=[echo],
    description="Execute tasks and return results",
)
class WorkerAgent:
    SYSTEM_PROMPT = """You are the worker.
Use the echo tool to handle user requests and respond.

## Output requirements
Return JSON only (single object), no explanations or Markdown:
{"text": "Your result"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


def _render_event(event: dict) -> None:
    event_type = event.get("event")
    data = event.get("data", {})
    if event_type == "agent.start":
        agent = event.get("agent", {})
        print(f"\n[{agent.get('name')}] started...")
    elif event_type == "agent.thinking":
        message = data.get("message", {})
        thinking = message.get("content", "")
        if thinking:
            agent = event.get("agent", {})
            print(f"\n[{agent.get('id')}] thinking...")
            print(f"  {thinking[:200]}..." if len(thinking) > 200 else f"  {thinking}")
    elif event_type == "tool.call":
        tool_info = data.get("tool", {})
        print(f"  Tool call: {tool_info.get('name')}")
    elif event_type == "tool.result":
        tool_info = data.get("tool", {})
        result = str(tool_info.get("output", ""))
        if len(result) > 100:
            result = result[:100] + "..."
        print(f"  Tool result: {result}")
    elif event_type == "agent.end":
        deliverable = data.get("deliverable")
        if deliverable is not None:
            print("  Completed")
            print(f"  Deliverable: {json.dumps(deliverable, ensure_ascii=False)}")
    elif event_type == "agent.interrupt":
        interrupt_payload = data.get("interrupt", {}).get("payload")
        print(f"\nUser input required: {interrupt_payload}")
    elif event_type == "agent.failed":
        error = data.get("error", {})
        print(f"\nError: {error.get('detail') or error.get('message')}")


def create_hierarchical_team(config: DatapillarConfig) -> Datapillar:
    team = Datapillar(
        config=config,
        namespace="demo_hier",
        name="Hierarchical Team Example",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.HIERARCHICAL,
        enable_share_context=True,
        verbose=True,
    )
    return team


async def main() -> None:
    config = DatapillarConfig()
    if not config.llm.is_configured():
        supported = ", ".join(Provider.list_supported())
        raise RuntimeError(
            "Please configure LLM first:\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "Optional: export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "Optional: export DATAPILLAR_LLM_ENABLE_THINKING=\"false\"\n"
            f"Supported providers: {supported}"
        )
    team = create_hierarchical_team(config)

    print("=" * 60)
    print("Hierarchical mode example is ready")
    print(f"  Model: {config.llm.model}")
    print("  Members: Manager -> Worker (manager delegates)")
    print("=" * 60)

    query = (
        "Summarize the following: Datapillar provides task orchestration, "
        "metric management, and access control, with a focus on observability and cost governance."
    )
    print(f"\nUser request: {query}\n")
    print("-" * 60)

    async for event in team.stream(query=query, session_id="s_demo_hier"):
        _render_event(event)

    print("\n" + "=" * 60)
    print("Demo completed")


if __name__ == "__main__":
    asyncio.run(main())
