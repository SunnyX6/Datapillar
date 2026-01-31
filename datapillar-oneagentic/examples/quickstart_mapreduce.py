# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Datapillar OneAgentic MapReduce mode example.

Run:
    uv run python examples/quickstart_mapreduce.py
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


class CapabilityPoint(BaseModel):
    category: str
    capabilities: list[str]


class KeyPointsOutput(BaseModel):
    points: list[CapabilityPoint]


class ConclusionOutput(BaseModel):
    conclusion: str


class SummaryOutput(BaseModel):
    points: list[CapabilityPoint]
    conclusion: str


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
    id="worker_a",
    name="Analyzer",
    deliverable_schema=KeyPointsOutput,
    tools=[echo],
    description="Extract key points from the task",
)
class WorkerAgentA:
    SYSTEM_PROMPT = """You are an analyzer.
Use the echo tool to extract key points from the user input.

## Output requirements
Return JSON only (single object), no explanations or Markdown.
Always match the schema, even if the input is empty or irrelevant.
If nothing can be extracted, return:
{"points": []}
Example with content:
{"points": [{"category": "Category", "capabilities": ["Capability 1", "Capability 2"]}]}
"""

    async def run(self, ctx: AgentContext) -> KeyPointsOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="worker_b",
    name="Summarizer",
    deliverable_schema=ConclusionOutput,
    tools=[echo],
    description="Produce a concise conclusion from inputs",
)
class WorkerAgentB:
    SYSTEM_PROMPT = """You are a summarizer.
Use the echo tool to output a short conclusion.

## Output requirements
Return JSON only (single object), no explanations or Markdown.
Always match the schema, even if the input is empty or irrelevant.
If no conclusion can be produced, return:
{"conclusion": ""}
Example with content:
{"conclusion": "Your conclusion"}
"""

    async def run(self, ctx: AgentContext) -> ConclusionOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="reducer",
    name="Reducer",
    deliverable_schema=SummaryOutput,
    description="Aggregate multiple results into a final answer",
)
class ReducerAgent:
    SYSTEM_PROMPT = """You are the reducer.
Aggregate results and provide the final answer.

## Output requirements
Return JSON only (single object), no explanations or Markdown.
Always match the schema, even if inputs are empty.
If inputs are empty, return:
{"points": [], "conclusion": ""}
Example with content:
{"points": [{"category": "Category", "capabilities": ["Capability 1"]}], "conclusion": "Your conclusion"}
"""

    async def run(self, ctx: AgentContext) -> SummaryOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
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


def create_mapreduce_team(config: DatapillarConfig) -> Datapillar:
    team = Datapillar(
        config=config,
        namespace="demo_mapreduce",
        name="MapReduce Team Example",
        agents=[WorkerAgentA, WorkerAgentB, ReducerAgent],
        process=Process.MAPREDUCE,
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
    config.llm.rate_limit.default.max_concurrent = 1
    team = create_mapreduce_team(config)

    print("=" * 60)
    print("MapReduce mode example is ready")
    print(f"  Model: {config.llm.model}")
    print("  Members: Analyzer + Summarizer -> Reducer")
    print("=" * 60)

    query = "Split Datapillar's core capabilities into key points and a conclusion."
    print(f"\nUser request: {query}\n")
    print("-" * 60)

    async for event in team.stream(query=query, session_id="s_demo_mapreduce"):
        _render_event(event)

    print("\n" + "=" * 60)
    print("Demo completed")


if __name__ == "__main__":
    asyncio.run(main())
