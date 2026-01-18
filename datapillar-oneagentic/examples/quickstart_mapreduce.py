"""
Datapillar OneAgentic MapReduce 模式示例

运行命令：
    uv run python examples/quickstart_mapreduce.py
"""

from __future__ import annotations

import asyncio
import os

from pydantic import BaseModel

from datapillar_oneagentic import (
    AgentContext,
    Datapillar,
    DatapillarConfig,
    Process,
    agent,
    tool,
)


# ============================================================================
# LLM 配置
# ============================================================================
LLM_PROVIDER = "glm"
LLM_API_KEY = os.environ.get("GLM_API_KEY")
LLM_BASE_URL = os.environ.get("GLM_BASE_URL")
LLM_MODEL = os.environ.get("GLM_MODEL")
LLM_ENABLE_THINKING = os.environ.get("GLM_ENABLE_THINKING", "false").lower() in {
    "1",
    "true",
    "yes",
}

if not LLM_API_KEY or not LLM_MODEL:
    raise RuntimeError("请设置 GLM_API_KEY 和 GLM_MODEL（可选 GLM_BASE_URL/GLM_ENABLE_THINKING）")


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
    id="worker_a",
    name="分析者",
    deliverable_schema=TextOutput,
    tools=[echo],
    description="提取任务的关键信息与要点",
)
class WorkerAgentA:
    SYSTEM_PROMPT = """你是分析者。
使用 echo 工具提炼用户输入的关键信息并给出结论。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"text": "你的结果"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="worker_b",
    name="总结者",
    deliverable_schema=TextOutput,
    tools=[echo],
    description="根据输入输出总结性结论",
)
class WorkerAgentB:
    SYSTEM_PROMPT = """你是总结者。
使用 echo 工具输出简短结论。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"text": "你的结果"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


@agent(
    id="reducer",
    name="汇总者",
    deliverable_schema=TextOutput,
    description="汇总多路结果并输出最终答案",
)
class ReducerAgent:
    SYSTEM_PROMPT = """你是汇总者。
汇总多路结果并给出最终答案。

## 输出要求
只能输出 JSON（单个对象），不得输出解释或 Markdown：
{"text": "你的结果"}
"""

    async def run(self, ctx: AgentContext) -> TextOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        return await ctx.get_structured_output(messages)


def _render_event(event: dict) -> None:
    event_type = event.get("event")
    if event_type == "agent.start":
        agent_info = event.get("agent", {})
        print(f"\n🤖 [{agent_info.get('name')}] 开始工作...")
    elif event_type == "agent.thinking":
        message = event.get("message", {})
        thinking = message.get("content", "")
        if thinking:
            agent_info = event.get("agent", {})
            print(f"\n🧠 [{agent_info.get('id')}] 思考中...")
            print(f"   {thinking[:200]}..." if len(thinking) > 200 else f"   {thinking}")
    elif event_type == "tool.start":
        tool_info = event.get("tool", {})
        print(f"   🔧 调用: {tool_info.get('name')}")
    elif event_type == "tool.end":
        tool_info = event.get("tool", {})
        result = str(tool_info.get("output", ""))
        if len(result) > 100:
            result = result[:100] + "..."
        print(f"   📋 结果: {result}")
    elif event_type == "agent.end":
        print("   ✅ 完成")
    elif event_type == "agent.interrupt":
        interrupt_payload = event.get("interrupt", {}).get("payload")
        print(f"\n❓ 需要用户输入: {interrupt_payload}")
    elif event_type == "result":
        print(f"\n{'=' * 60}")
        print("📦 最终结果:")
        deliverables = event.get("result", {}).get("deliverable", {})
        for key, value in deliverables.items():
            print(f"\n[{key}]")
            if isinstance(value, dict):
                for k, v in value.items():
                    print(f"  {k}: {v}")
            else:
                print(f"  {value}")
    elif event_type == "error":
        error = event.get("error", {})
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
    llm_config = {
        "provider": LLM_PROVIDER,
        "api_key": LLM_API_KEY,
        "model": LLM_MODEL,
        "enable_thinking": LLM_ENABLE_THINKING,
        "timeout_seconds": 120,
        "retry": {"max_retries": 2},
    }
    if LLM_BASE_URL:
        llm_config["base_url"] = LLM_BASE_URL

    config = DatapillarConfig(llm=llm_config)
    team = create_mapreduce_team(config)

    print("=" * 60)
    print("🧩 MapReduce 模式示例已就绪")
    print(f"   模型: {LLM_MODEL}")
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
