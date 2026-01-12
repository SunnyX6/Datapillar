"""
端到端真实测试

使用真实 GLM API 验证框架完整功能：
1. 单 Agent 基本执行
2. 带工具调用的 Agent
3. 多 Agent 协作（SEQUENTIAL 流程）
4. 事件系统验证
5. LLM 调用层弹性机制
"""

import asyncio
import time

import pytest
from pydantic import BaseModel, Field

from datapillar_oneagentic import (
    Datapillar,
    DatapillarResult,
    AgentContext,
    Process,
    Clarification,
    agent,
    tool,
    datapillar_configure,
    get_config,
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    SessionStartedEvent,
    SessionCompletedEvent,
)
from datapillar_oneagentic.config import reset_config
from datapillar_oneagentic.core.agent import AgentRegistry
from datapillar_oneagentic.tools.registry import ToolRegistry
from datapillar_oneagentic.providers.llm import call_llm


GLM_API_KEY = "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7"
GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
GLM_MODEL = "glm-4.7"


class SimpleOutput(BaseModel):
    """简单输出"""

    answer: str = Field(description="回答")


class CalculationOutput(BaseModel):
    """计算输出"""

    result: int = Field(description="计算结果")
    explanation: str = Field(description="解释")


class AnalysisOutput(BaseModel):
    """分析输出"""

    summary: str = Field(description="分析摘要")
    confidence: float = Field(ge=0, le=1, description="置信度")


class DeveloperOutput(BaseModel):
    """开发者输出"""

    code: str = Field(description="代码")
    language: str = Field(description="编程语言")


@pytest.fixture(autouse=True)
def setup_and_teardown():
    """每个测试前后重置状态"""
    reset_config()
    AgentRegistry.clear()
    ToolRegistry.clear()

    datapillar_configure(
        llm={
            "api_key": GLM_API_KEY,
            "base_url": GLM_BASE_URL,
            "model": GLM_MODEL,
        },
        resilience={
            "max_retries": 2,
            "llm_timeout_seconds": 60,
        },
        agent={
            "max_steps": 10,
        },
    )

    yield

    reset_config()
    AgentRegistry.clear()
    ToolRegistry.clear()


class TestLLMCallLayer:
    """LLM 调用层端到端测试"""

    @pytest.mark.asyncio
    async def test_basic_llm_call(self):
        """测试基本 LLM 调用"""
        from langchain_core.messages import HumanMessage

        llm = call_llm()
        messages = [HumanMessage(content="请用一句话回答：1+1等于多少？")]

        response = await llm.ainvoke(messages)

        assert response is not None
        assert response.content is not None
        assert len(response.content) > 0
        print(f"LLM 响应: {response.content}")

    @pytest.mark.asyncio
    async def test_structured_output(self):
        """测试结构化输出"""
        from langchain_core.messages import HumanMessage

        llm = call_llm(output_schema=SimpleOutput)
        messages = [HumanMessage(content="请回答：中国的首都是哪里？")]

        response = await llm.ainvoke(messages)

        assert response is not None
        if isinstance(response, dict):
            parsed = response.get("parsed")
            assert parsed is not None
            assert isinstance(parsed, SimpleOutput)
            print(f"结构化输出: {parsed.answer}")
        else:
            assert isinstance(response, SimpleOutput)
            print(f"结构化输出: {response.answer}")


class TestSingleAgentE2E:
    """单 Agent 端到端测试"""

    @pytest.mark.asyncio
    async def test_simple_agent_execution(self):
        """测试简单 Agent 执行"""

        @agent(
            id="greeter",
            name="问候者",
            description="用中文问候用户",
            deliverable_schema=SimpleOutput,
            deliverable_key="greeting",
        )
        class GreeterAgent:
            SYSTEM_PROMPT = """你是一个友好的问候助手。用简短的中文回复用户，以 JSON 格式输出。

输出格式：
{
  "answer": "<问候语>"
}"""

            async def run(self, ctx: AgentContext) -> SimpleOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        team = Datapillar(
            name="问候团队",
            agents=[GreeterAgent],
            process=Process.SEQUENTIAL,
        )

        result = await team.kickoff(
            inputs={"query": "你好"},
            session_id="test_simple_001",
        )

        assert result is not None
        assert isinstance(result, DatapillarResult)
        assert result.success is True
        assert result.final_deliverable is not None

        print(f"问候结果: {result.get_deliverable('greeting')}")
        print(f"摘要: {result.summary}")

    @pytest.mark.asyncio
    async def test_agent_with_tools(self):
        """测试带工具的 Agent"""

        @tool
        def add(a: int, b: int) -> str:
            """两数相加

            Args:
                a: 第一个数
                b: 第二个数

            Returns:
                计算结果
            """
            return f"{a} + {b} = {a + b}"

        @tool
        def multiply(a: int, b: int) -> str:
            """两数相乘

            Args:
                a: 第一个数
                b: 第二个数

            Returns:
                计算结果
            """
            return f"{a} * {b} = {a * b}"

        @agent(
            id="calculator",
            name="计算器",
            description="执行数学计算",
            tools=["add", "multiply"],
            deliverable_schema=CalculationOutput,
            deliverable_key="calculation",
        )
        class CalculatorAgent:
            SYSTEM_PROMPT = """你是一个数学计算助手。
使用提供的工具完成计算任务，然后以 JSON 格式输出结果。

输出格式：
{
  "result": <计算结果，整数>,
  "explanation": "<解释说明>"
}"""

            async def run(self, ctx: AgentContext) -> CalculationOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                messages = await ctx.invoke_tools(messages)
                return await ctx.get_output(messages)

        team = Datapillar(
            name="计算团队",
            agents=[CalculatorAgent],
            process=Process.SEQUENTIAL,
        )

        result = await team.kickoff(
            inputs={"query": "请计算 5 + 3"},
            session_id="test_tools_001",
        )

        assert result is not None
        assert result.success is True
        assert "calculation" in result.deliverables

        calc = result.deliverables["calculation"]
        assert calc["result"] == 8
        print(f"计算结果: {calc['result']}")
        print(f"解释: {calc['explanation']}")


class TestMultiAgentE2E:
    """多 Agent 协作端到端测试"""

    @pytest.mark.asyncio
    async def test_sequential_agents(self):
        """测试 SEQUENTIAL 流程多 Agent 协作"""

        @agent(
            id="analyst",
            name="分析师",
            description="分析问题",
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class AnalystAgent:
            SYSTEM_PROMPT = """你是一个问题分析师。
分析用户的问题，以 JSON 格式输出分析结果。

输出格式：
{
  "summary": "<分析摘要>",
  "confidence": <置信度，0到1之间的小数>
}"""

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        @agent(
            id="developer",
            name="开发者",
            description="根据分析编写代码",
            deliverable_schema=DeveloperOutput,
            deliverable_key="code",
        )
        class DeveloperAgent:
            SYSTEM_PROMPT = """你是一个代码开发者。
根据用户需求，编写代码并以 JSON 格式输出。

输出格式：
{
  "code": "<完整代码>",
  "language": "<编程语言>"
}"""

            async def run(self, ctx: AgentContext) -> DeveloperOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        team = Datapillar(
            name="开发团队",
            agents=[AnalystAgent, DeveloperAgent],
            process=Process.SEQUENTIAL,
        )

        result = await team.kickoff(
            inputs={"query": "写一个 Python 函数计算斐波那契数列"},
            session_id="test_multi_001",
        )

        assert result is not None
        assert result.success is True
        assert "analysis" in result.deliverables
        assert "code" in result.deliverables

        analysis = result.deliverables["analysis"]
        code = result.deliverables["code"]

        print(f"分析摘要: {analysis['summary']}")
        print(f"置信度: {analysis['confidence']}")
        print(f"代码语言: {code['language']}")
        print(f"代码:\n{code['code']}")


class TestEventSystemE2E:
    """事件系统端到端测试"""

    @pytest.mark.asyncio
    async def test_events_are_emitted(self):
        """测试事件正确触发"""
        events_log = []

        @event_bus.on(AgentStartedEvent)
        def on_started(source, event):
            events_log.append(f"STARTED:{event.agent_name}")

        @event_bus.on(AgentCompletedEvent)
        def on_completed(source, event):
            events_log.append(f"COMPLETED:{event.agent_name}")

        # 使用简单 Agent（不带工具）测试事件
        @agent(
            id="simple_event_agent",
            name="简单事件Agent",
            deliverable_schema=SimpleOutput,
            deliverable_key="event_result",
        )
        class SimpleEventAgent:
            SYSTEM_PROMPT = """你是一个简单的助手。以 JSON 格式回复。

输出格式：
{
  "answer": "<简短回答>"
}"""

            async def run(self, ctx: AgentContext) -> SimpleOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        team = Datapillar(
            name="事件测试团队",
            agents=[SimpleEventAgent],
        )

        result = await team.kickoff(
            inputs={"query": "你好"},
            session_id="test_events_002",
        )

        await asyncio.sleep(0.5)

        assert result.success is True
        assert any("STARTED" in e for e in events_log)
        assert any("COMPLETED" in e for e in events_log)

        print(f"事件日志: {events_log}")


class TestResilienceE2E:
    """弹性机制端到端测试"""

    @pytest.mark.asyncio
    async def test_llm_timeout_handling(self):
        """测试 LLM 超时处理"""
        from langchain_core.messages import HumanMessage

        datapillar_configure(
            resilience={
                "llm_timeout_seconds": 30,
                "max_retries": 1,
            }
        )

        llm = call_llm()
        messages = [HumanMessage(content="简单回答：1+1=?")]

        start = time.time()
        response = await llm.ainvoke(messages)
        elapsed = time.time() - start

        assert response is not None
        assert elapsed < 30
        print(f"响应时间: {elapsed:.2f}s")
        print(f"响应内容: {response.content}")


class TestMemoryE2E:
    """记忆系统端到端测试"""

    @pytest.mark.asyncio
    async def test_session_memory(self):
        """测试会话记忆"""
        from datapillar_oneagentic.memory.session_memory import SessionMemory

        memory = SessionMemory(
            session_id="e2e_memory_test",
            user_id="test_user",
        )

        memory.add_user_message("帮我创建用户表")
        memory.add_agent_response("analyst", "好的，我来分析需求...")
        memory.pin_decision("使用 Iceberg 格式存储", "architect")
        memory.pin_constraint("必须支持增量更新")

        prompt = memory.to_prompt()

        assert "创建用户表" in prompt
        assert "分析需求" in prompt

        stats = memory.get_stats()
        assert stats["total_entries"] == 2
        assert stats["total_decisions"] == 1
        assert stats["total_constraints"] == 1

        print(f"记忆统计: {stats}")
        print(f"Token 估算: {memory.estimate_tokens()}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
