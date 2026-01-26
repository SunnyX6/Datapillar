"""
Datapillar OneAgentic ReAct mode example.

Run:
    uv run python examples/quickstart_react.py
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
    id="react_worker",
    name="Worker",
    deliverable_schema=TextOutput,
    tools=[echo],
    description="Execute tasks in the ReAct plan",
)
class ReactWorkerAgent:
    SYSTEM_PROMPT = """You are the worker.
Use the echo tool to process the task and respond.

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
        agent_info = event.get("agent", {})
        print(f"\n[{agent_info.get('name')}] started...")
    elif event_type == "agent.thinking":
        message = data.get("message", {})
        thinking = message.get("content", "")
        if thinking:
            agent_info = event.get("agent", {})
            print(f"\n[{agent_info.get('id')}] thinking...")
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


def create_react_team(config: DatapillarConfig) -> Datapillar:
    team = Datapillar(
        config=config,
        namespace="demo_react",
        name="ReAct Team Example",
        agents=[ReactWorkerAgent],
        process=Process.REACT,
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
    team = create_react_team(config)

    print("=" * 60)
    print("ReAct mode example is ready")
    print(f"  Model: {config.llm.model}")
    print("  Member: Worker (controller handles planning/reflection)")
    print("=" * 60)

    query = "Plan and output a one-sentence summary of Datapillar's core value."
    print(f"\nUser request: {query}\n")
    print("-" * 60)

    async for event in team.stream(query=query, session_id="s_demo_react"):
        _render_event(event)

    print("\n" + "=" * 60)
    print("Demo completed")


if __name__ == "__main__":
    asyncio.run(main())
