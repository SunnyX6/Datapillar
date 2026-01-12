"""
框架功能点测试

使用真实 GLM API 验证框架核心功能。
"""

import asyncio
import pytest
from pydantic import BaseModel, Field

# === 框架导入 ===
from datapillar_oneagentic import (
    # 配置
    datapillar_configure,
    # 装饰器
    agent,
    tool,
    # 核心类
    Datapillar,
    AgentContext,
    Process,
    Clarification,
    # 事件
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    ToolCalledEvent,
    # Token/Usage
    TiktokenCounter,
    get_token_counter,
    extract_usage,
    estimate_usage,
    estimate_cost_usd,
    parse_pricing,
    NormalizedTokenUsage,
)

# === GLM 配置 ===
GLM_API_KEY = "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7"
GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
GLM_MODEL = "glm-4.7"


# === 测试用交付物 Schema ===
class AnalysisOutput(BaseModel):
    """分析结果"""
    summary: str = Field(description="分析摘要")
    confidence: float = Field(description="置信度 0-1")


class CalculationOutput(BaseModel):
    """计算结果"""
    result: str = Field(description="计算结果")
    steps: list[str] = Field(description="计算步骤")


# === 测试用工具 ===
@tool
def add_numbers(a: int, b: int) -> str:
    """
    两数相加

    Args:
        a: 第一个数
        b: 第二个数

    Returns:
        计算结果字符串
    """
    return f"{a} + {b} = {a + b}"


@tool
def multiply_numbers(a: int, b: int) -> str:
    """
    两数相乘

    Args:
        a: 第一个数
        b: 第二个数

    Returns:
        计算结果字符串
    """
    return f"{a} * {b} = {a * b}"


# === 测试用 Agent ===
@agent(
    id="calculator",
    name="计算器",
    description="执行数学计算",
    tools=["add_numbers", "multiply_numbers"],
    deliverable_schema=CalculationOutput,
    deliverable_key="calculation",
)
class CalculatorAgent:
    SYSTEM_PROMPT = """你是一个数学计算助手。
使用提供的工具完成计算任务。
返回计算结果和步骤。"""

    async def run(self, ctx: AgentContext) -> CalculationOutput | Clarification:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


@agent(
    id="analyzer",
    name="分析师",
    description="分析问题并给出建议",
    deliverable_schema=AnalysisOutput,
    deliverable_key="analysis",
)
class AnalyzerAgent:
    SYSTEM_PROMPT = """你是一个问题分析师。
分析用户的问题并给出简洁的分析摘要。
评估你的置信度（0-1）。"""

    async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        return await ctx.get_output(messages)


# === 事件记录 ===
events_log: list[str] = []


@event_bus.on(AgentStartedEvent)
def on_agent_started(source, event: AgentStartedEvent):
    events_log.append(f"STARTED: {event.agent_name}")


@event_bus.on(AgentCompletedEvent)
def on_agent_completed(source, event: AgentCompletedEvent):
    events_log.append(f"COMPLETED: {event.agent_name}")


@event_bus.on(ToolCalledEvent)
def on_tool_called(source, event: ToolCalledEvent):
    events_log.append(f"TOOL: {event.tool_name}")


# === 测试用例 ===

class TestTokenCounter:
    """Token 计数器测试"""

    def test_tiktoken_counter(self):
        """测试 tiktoken 计数器"""
        counter = TiktokenCounter(model="gpt-4o")

        # 测试文本计数
        text = "Hello, world! 你好，世界！"
        tokens = counter.count(text)

        assert tokens > 0
        print(f"文本 token 数: {tokens}")

    def test_get_token_counter(self):
        """测试获取默认计数器"""
        # 需要先配置
        datapillar_configure(
            llm={
                "api_key": GLM_API_KEY,
                "base_url": GLM_BASE_URL,
                "model": GLM_MODEL,
            }
        )

        counter = get_token_counter()
        assert counter is not None

        tokens = counter.count("测试文本")
        assert tokens > 0
        print(f"默认计数器 token 数: {tokens}")

    def test_count_messages(self):
        """测试消息列表计数"""
        counter = TiktokenCounter()

        messages = [
            {"role": "system", "content": "你是助手"},
            {"role": "user", "content": "你好"},
            {"role": "assistant", "content": "你好！有什么可以帮你的？"},
        ]

        tokens = counter.count_messages(messages)
        assert tokens > 0
        print(f"消息列表 token 数: {tokens}")


class TestUsageTracker:
    """Usage 追踪测试"""

    def test_extract_usage_from_dict(self):
        """测试从 dict 提取 usage"""
        # 模拟 OpenAI 格式
        openai_response = {
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150,
                "prompt_tokens_details": {
                    "cached_tokens": 30
                }
            }
        }

        usage = extract_usage(openai_response)

        assert usage is not None
        assert usage.prompt_tokens == 100
        assert usage.completion_tokens == 50
        assert usage.total_tokens == 150
        assert usage.cached_tokens == 30
        assert usage.estimated is False

        print(f"OpenAI usage: {usage}")

    def test_extract_usage_anthropic_format(self):
        """测试 Anthropic 格式"""
        anthropic_response = {
            "usage": {
                "input_tokens": 80,
                "output_tokens": 40,
                "cache_creation_input_tokens": 20,
                "cache_read_input_tokens": 10,
            }
        }

        usage = extract_usage(anthropic_response)

        assert usage is not None
        assert usage.prompt_tokens == 80
        assert usage.completion_tokens == 40
        assert usage.cache_creation_tokens == 20
        assert usage.cache_read_tokens == 10
        assert usage.estimated is False

        print(f"Anthropic usage: {usage}")

    def test_estimate_usage(self):
        """测试估算 usage"""
        from langchain_core.messages import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content="你是助手"),
            HumanMessage(content="计算 1+1"),
        ]

        usage = estimate_usage(
            prompt_messages=messages,
            completion_text="1+1=2"
        )

        assert usage is not None
        assert usage.estimated is True
        assert usage.prompt_tokens > 0
        assert usage.completion_tokens > 0

        print(f"估算 usage: {usage}")
        print(f"estimated 标记: {usage.estimated}")

    def test_estimate_cost_with_cache(self):
        """测试带缓存的费用计算"""
        # 模拟有缓存命中的 usage
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
            cached_tokens=300,  # 30% 缓存命中
        )

        pricing = parse_pricing({
            "prompt_usd_per_1k_tokens": "0.01",
            "completion_usd_per_1k_tokens": "0.03",
        })

        cost = estimate_cost_usd(usage=usage, pricing=pricing)

        assert cost is not None
        assert cost.total_cost_usd > 0
        assert cost.savings_from_cache_usd > 0

        print(f"总费用: ${cost.total_cost_usd}")
        print(f"缓存节省: ${cost.savings_from_cache_usd}")


class TestRealLLMUsage:
    """真实 LLM 调用的 Usage 测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """配置 GLM"""
        datapillar_configure(
            llm={
                "api_key": GLM_API_KEY,
                "base_url": GLM_BASE_URL,
                "model": GLM_MODEL,
            }
        )

    @pytest.mark.asyncio
    async def test_real_usage_extraction(self):
        """测试真实 LLM 调用的 usage 提取"""
        from datapillar_oneagentic.providers.llm import call_llm
        from langchain_core.messages import HumanMessage

        llm = call_llm()

        messages = [HumanMessage(content="说一个笑话")]
        response = await llm.ainvoke(messages)

        print(f"响应内容: {response.content[:100]}...")

        # 提取 usage
        usage = extract_usage(response)

        if usage:
            print(f"真实 usage: {usage}")
            print(f"是否估算: {usage.estimated}")
            print(f"prompt_tokens: {usage.prompt_tokens}")
            print(f"completion_tokens: {usage.completion_tokens}")
            print(f"total_tokens: {usage.total_tokens}")

            # 计算费用（GLM-4.7 定价）
            pricing = parse_pricing({
                "prompt_usd_per_1k_tokens": "0.007",
                "completion_usd_per_1k_tokens": "0.007",
            })
            cost = estimate_cost_usd(usage=usage, pricing=pricing)
            if cost:
                print(f"预估费用: ${cost.total_cost_usd}")
        else:
            print("未能提取 usage，可能需要检查 GLM 响应格式")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
