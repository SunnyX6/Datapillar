"""
Datapillar OneAgentic MapReduce 模式示例

运行命令：
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
    """回显文本。

    Args:
        text: 输入文本。

    Returns:
        回显结果。
    """
    return f"echo:{text}"


@agent(
    id="worker_a",
    name="分析者",
    deliverable_schema=KeyPointsOutput,
    tools=[echo],
    description="提取任务的关键信息与要点",
)
class WorkerAgentA:
    SYSTEM_PROMPT = """你是分析者。
使用 echo 工具提炼用户输入的关键信息并给出要点。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"points": [{"category": "类别", "capabilities": ["能力1", "能力2"]}]}
"""

    async def run(self, ctx: AgentContext) -> KeyPointsOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="worker_b",
    name="总结者",
    deliverable_schema=ConclusionOutput,
    tools=[echo],
    description="根据输入输出总结性结论",
)
class WorkerAgentB:
    SYSTEM_PROMPT = """你是总结者。
使用 echo 工具输出简短结论。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"conclusion": "你的结论"}
"""

    async def run(self, ctx: AgentContext) -> ConclusionOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="reducer",
    name="汇总者",
    deliverable_schema=SummaryOutput,
    description="汇总多路结果并输出最终答案",
)
class ReducerAgent:
    SYSTEM_PROMPT = """你是汇总者。
汇总多路结果并给出最终答案。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"points": [{"category": "类别", "capabilities": ["能力1"]}], "conclusion": "你的结论"}
"""

    async def run(self, ctx: AgentContext) -> SummaryOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        return await ctx.get_structured_output(messages)


def _render_event(event: dict) -> None:
    event_type = event.get("event")
    data = event.get("data", {})
    if event_type == "agent.start":
        agent_info = event.get("agent", {})
        print(f"\n🤖 [{agent_info.get('name')}] 开始工作...")
    elif event_type == "agent.thinking":
        message = data.get("message", {})
        thinking = message.get("content", "")
        if thinking:
            agent_info = event.get("agent", {})
            print(f"\n🧠 [{agent_info.get('id')}] 思考中...")
            print(f"   {thinking[:200]}..." if len(thinking) > 200 else f"   {thinking}")
    elif event_type == "tool.call":
        tool_info = data.get("tool", {})
        print(f"   🔧 调用: {tool_info.get('name')}")
    elif event_type == "tool.result":
        tool_info = data.get("tool", {})
        result = str(tool_info.get("output", ""))
        if len(result) > 100:
            result = result[:100] + "..."
        print(f"   📋 结果: {result}")
    elif event_type == "agent.end":
        deliverable = data.get("deliverable")
        if deliverable is not None:
            print("   ✅ 完成")
            print(f"   📦 交付物: {json.dumps(deliverable, ensure_ascii=False)}")
    elif event_type == "agent.interrupt":
        interrupt_payload = data.get("interrupt", {}).get("payload")
        print(f"\n❓ 需要用户输入: {interrupt_payload}")
    elif event_type == "agent.failed":
        error = data.get("error", {})
        print(f"\n❌ 错误: {error.get('detail') or error.get('message')}")


def create_mapreduce_team(config: DatapillarConfig) -> Datapillar:
    team = Datapillar(
        config=config,
        namespace="demo_mapreduce",
        name="MapReduce 团队示例",
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
            "请先配置 LLM：\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "可选：export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "可选：export DATAPILLAR_LLM_ENABLE_THINKING=\"false\"\n"
            f"支持 provider: {supported}"
        )
    team = create_mapreduce_team(config)

    print("=" * 60)
    print("🧩 MapReduce 模式示例已就绪")
    print(f"   模型: {config.llm.model}")
    print("   成员: 分析者 + 总结者 -> 汇总者")
    print("=" * 60)

    query = "请将 Datapillar 的核心能力拆成两部分：要点和结论。"
    print(f"\n📝 用户需求: {query}\n")
    print("-" * 60)

    async for event in team.stream(query=query, session_id="s_demo_mapreduce"):
        _render_event(event)

    print("\n" + "=" * 60)
    print("✨ 演示完成")


if __name__ == "__main__":
    asyncio.run(main())
