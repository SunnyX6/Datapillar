"""
Datapillar OneAgentic 动态模式示例

运行命令：
    uv run python examples/quickstart_dynamic.py
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
    """回显文本。

    Args:
        text: 输入文本。

    Returns:
        回显结果。
    """
    return f"echo:{text}"


@agent(
    id="manager",
    name="经理",
    deliverable_schema=TextOutput,
    description="判断任务是否需要委派",
)
class ManagerAgent:
    SYSTEM_PROMPT = """你是经理。

要求：
1. 必须调用 delegate_to_worker 委派任务给执行者。
2. 如果无法委派，输出最终结果。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"text": "无法委派的原因"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        await ctx.invoke_tools(messages)
        return TextOutput(text="delegated")


@agent(
    id="worker",
    name="执行者",
    deliverable_schema=TextOutput,
    tools=[echo],
    description="执行具体任务并返回结果",
)
class WorkerAgent:
    SYSTEM_PROMPT = """你是执行者。
使用 echo 工具处理用户请求并给出结果。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"text": "你的结果"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
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


def create_dynamic_team(config: DatapillarConfig) -> Datapillar:
    team = Datapillar(
        config=config,
        namespace="demo_dynamic",
        name="动态团队示例",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.DYNAMIC,
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
    team = create_dynamic_team(config)

    print("=" * 60)
    print("🧭 动态模式示例已就绪")
    print(f"   模型: {config.llm.model}")
    print("   成员: 经理 -> 执行者（动态委派）")
    print("=" * 60)

    query = "请生成一条简短的项目进展说明"
    print(f"\n📝 用户需求: {query}\n")
    print("-" * 60)

    async for event in team.stream(query=query, session_id="s_demo_dynamic"):
        _render_event(event)

    print("\n" + "=" * 60)
    print("✨ 演示完成")


if __name__ == "__main__":
    asyncio.run(main())
