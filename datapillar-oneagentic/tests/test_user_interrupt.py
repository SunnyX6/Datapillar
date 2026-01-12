"""
用户主动打断 Agent 并恢复的端到端测试

测试场景：
1. 用户发起请求，Agent 开始执行
2. 用户中途发送新消息（打断正在执行的 Agent）
3. 用户说"继续"
4. Agent 应该能从 Checkpointer 恢复状态，理解上下文并继续执行

核心验证点：
- Checkpointer 正确保存执行状态
- 同一 session_id 的多次调用共享状态
- Agent 能从记忆中理解"继续"的语义
"""

import asyncio
import pytest
from pydantic import BaseModel, Field

from datapillar_oneagentic import (
    Datapillar,
    DatapillarResult,
    AgentContext,
    Process,
    Clarification,
    agent,
    datapillar_configure,
)
from datapillar_oneagentic.config import reset_config
from datapillar_oneagentic.core.agent import AgentRegistry
from datapillar_oneagentic.tools.registry import ToolRegistry
from datapillar_oneagentic.memory.session_memory import SessionMemory
from datapillar_oneagentic.storage import MemoryCheckpointer


# GLM 配置
GLM_API_KEY = "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7"
GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
GLM_MODEL = "glm-4.7"


class AnalysisOutput(BaseModel):
    """分析输出"""
    summary: str = Field(description="分析摘要")
    status: str = Field(description="状态：started/in_progress/completed")
    progress: int = Field(ge=0, le=100, description="进度百分比")


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


class TestUserInterruptAgent:
    """用户主动打断 Agent 测试"""

    @pytest.mark.asyncio
    async def test_session_state_preserved_across_calls(self):
        """测试同一会话的多次调用，状态通过 Checkpointer 保留"""

        @agent(
            id="stateful_analyst",
            name="状态分析师",
            description="记住之前的对话",
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class StatefulAnalystAgent:
            SYSTEM_PROMPT = """你是一个智能数据分析助手。

请仔细阅读对话历史，理解用户的完整意图：
1. 如果这是新任务，开始分析并报告进度
2. 如果用户说"继续"、"好的"、"是的"，这是对之前任务的确认，继续执行
3. 如果用户说"停"、"暂停"，暂停当前任务

基于对话历史，输出当前状态（JSON）：
{
  "summary": "<根据上下文的分析摘要>",
  "status": "<started|in_progress|completed>",
  "progress": <0-100的进度>
}"""

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        # 使用 MemoryCheckpointer 保存状态
        checkpointer = MemoryCheckpointer()

        team = Datapillar(
            name="状态测试团队",
            agents=[StatefulAnalystAgent],
            process=Process.SEQUENTIAL,
            checkpointer=checkpointer,
        )

        session_id = "interrupt_test_001"

        # === 第一次调用：用户发起任务 ===
        print("\n===== 第一次调用：发起任务 =====")
        result1 = await team.kickoff(
            inputs={"query": "帮我分析销售数据，创建销售报表"},
            session_id=session_id,
        )

        print(f"成功: {result1.success}")
        if result1.success:
            analysis1 = result1.deliverables.get("analysis", {})
            print(f"摘要: {analysis1.get('summary', '')[:100]}...")
            print(f"状态: {analysis1.get('status')}")
            print(f"进度: {analysis1.get('progress')}%")

        assert result1.success is True

        # === 第二次调用：用户打断，说"继续" ===
        print("\n===== 第二次调用：用户说'继续' =====")
        result2 = await team.kickoff(
            inputs={"query": "继续"},
            session_id=session_id,  # 同一个 session_id
        )

        print(f"成功: {result2.success}")
        if result2.success:
            analysis2 = result2.deliverables.get("analysis", {})
            print(f"摘要: {analysis2.get('summary', '')[:100]}...")
            print(f"状态: {analysis2.get('status')}")
            print(f"进度: {analysis2.get('progress')}%")

            # 验证 Agent 理解了"继续"的上下文
            summary = analysis2.get("summary", "").lower()
            # Agent 应该提到之前的任务（销售数据/报表）
            assert any(keyword in summary for keyword in ["销售", "报表", "继续", "分析"]), \
                f"Agent 应该理解'继续'的上下文，摘要: {analysis2.get('summary')}"

        assert result2.success is True
        print("\n✓ Agent 正确理解了'继续'的上下文")

    @pytest.mark.asyncio
    async def test_memory_context_across_multiple_calls(self):
        """测试多次调用中记忆上下文的传递"""

        call_history = []

        @agent(
            id="memory_analyst",
            name="记忆分析师",
            description="测试记忆传递",
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class MemoryAnalystAgent:
            SYSTEM_PROMPT = """你是一个智能助手，会记住之前的对话。

仔细阅读对话历史，理解完整上下文：
- 如果用户说"继续"，说明用户想继续之前的任务
- 不要重新询问已经讨论过的问题

输出格式（JSON）：
{
  "summary": "<基于完整上下文的摘要>",
  "status": "in_progress",
  "progress": 50
}"""

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                # 记录每次调用的记忆状态
                if ctx.memory:
                    call_history.append({
                        "query": ctx.query,
                        "memory_entries": len(ctx.memory.conversation.entries),
                        "memory_prompt": ctx.memory.to_prompt()[:200],
                    })

                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        checkpointer = MemoryCheckpointer()

        team = Datapillar(
            name="记忆测试团队",
            agents=[MemoryAnalystAgent],
            process=Process.SEQUENTIAL,
            checkpointer=checkpointer,
            enable_memory=True,
        )

        session_id = "memory_test_001"

        # 第一次调用
        print("\n===== 第一次调用 =====")
        await team.kickoff(
            inputs={"query": "创建用户画像宽表，包含用户基础信息"},
            session_id=session_id,
        )

        # 第二次调用
        print("\n===== 第二次调用 =====")
        await team.kickoff(
            inputs={"query": "继续"},
            session_id=session_id,
        )

        # 第三次调用
        print("\n===== 第三次调用 =====")
        await team.kickoff(
            inputs={"query": "好的，继续执行"},
            session_id=session_id,
        )

        # 验证调用历史
        print("\n===== 调用历史 =====")
        for i, call in enumerate(call_history):
            print(f"\n调用 {i + 1}:")
            print(f"  Query: {call['query']}")
            print(f"  记忆条目数: {call['memory_entries']}")
            print(f"  记忆摘要: {call['memory_prompt'][:100]}...")

        # 验证记忆在多次调用中积累
        if len(call_history) >= 2:
            # 第二次调用应该有更多记忆
            assert call_history[1]["memory_entries"] > 0, "第二次调用应该有记忆"
            print("\n✓ 记忆在多次调用中正确传递")


class TestResumeAfterInterrupt:
    """测试打断后恢复"""

    @pytest.mark.asyncio
    async def test_agent_continues_without_re_asking(self):
        """验证 Agent 收到'继续'后不会重新询问已回答的问题"""

        @agent(
            id="smart_agent",
            name="智能代理",
            description="理解上下文",
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class SmartAgent:
            SYSTEM_PROMPT = """你是一个智能助手。

重要规则：
1. 仔细阅读对话历史
2. 如果用户说"继续"，这表示用户同意之前的提议，直接继续执行
3. 不要重复询问已经讨论过的问题
4. 基于之前的讨论继续工作

输出格式（JSON）：
{
  "summary": "<你的工作摘要，说明你理解了上下文>",
  "status": "in_progress",
  "progress": 60
}"""

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        checkpointer = MemoryCheckpointer()

        team = Datapillar(
            name="智能团队",
            agents=[SmartAgent],
            process=Process.SEQUENTIAL,
            checkpointer=checkpointer,
            enable_memory=True,
        )

        session_id = "smart_test_001"

        # 第一次调用：发起任务
        print("\n===== 发起任务 =====")
        result1 = await team.kickoff(
            inputs={"query": "帮我设计一个数据仓库架构，需要支持实时分析"},
            session_id=session_id,
        )
        print(f"第一次结果: {result1.deliverables.get('analysis', {}).get('summary', '')[:80]}...")

        # 模拟用户打断
        print("\n===== 用户打断：'先等一下' =====")
        result2 = await team.kickoff(
            inputs={"query": "先等一下"},
            session_id=session_id,
        )
        print(f"打断后结果: {result2.deliverables.get('analysis', {}).get('summary', '')[:80]}...")

        # 用户恢复
        print("\n===== 用户恢复：'继续' =====")
        result3 = await team.kickoff(
            inputs={"query": "继续"},
            session_id=session_id,
        )

        analysis3 = result3.deliverables.get("analysis", {})
        print(f"恢复后结果: {analysis3.get('summary', '')[:100]}...")

        # 验证 Agent 理解了上下文
        assert result3.success is True
        summary = analysis3.get("summary", "").lower()
        # 应该提到之前的任务内容
        assert any(keyword in summary for keyword in ["数据", "仓库", "架构", "分析", "继续"]), \
            f"Agent 应该理解之前的任务上下文: {analysis3.get('summary')}"

        print("\n✓ Agent 在用户说'继续'后正确恢复执行，没有重新询问")


class TestCheckpointerStateRecovery:
    """测试 Checkpointer 状态恢复"""

    @pytest.mark.asyncio
    async def test_state_recovery_from_checkpointer(self):
        """验证从 Checkpointer 恢复状态"""

        @agent(
            id="recovery_agent",
            name="恢复代理",
            description="测试状态恢复",
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class RecoveryAgent:
            SYSTEM_PROMPT = """你是一个数据分析助手。

阅读对话历史，理解任务进度：
- 如果是新任务，开始执行
- 如果用户说"继续"，继续之前的任务

输出格式（JSON）：
{
  "summary": "<任务摘要>",
  "status": "<started|in_progress|completed>",
  "progress": <进度0-100>
}"""

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                messages = ctx.build_messages(self.SYSTEM_PROMPT)
                return await ctx.get_output(messages)

        # 使用共享的 Checkpointer
        checkpointer = MemoryCheckpointer()

        team = Datapillar(
            name="恢复测试团队",
            agents=[RecoveryAgent],
            process=Process.SEQUENTIAL,
            checkpointer=checkpointer,
            enable_memory=True,
        )

        session_id = "recovery_test_001"

        # 第一次调用
        print("\n===== 第一次调用：创建任务 =====")
        result1 = await team.kickoff(
            inputs={"query": "创建销售分析报表，需要按地区和产品分类统计"},
            session_id=session_id,
        )

        analysis1 = result1.deliverables.get("analysis", {})
        print(f"状态: {analysis1.get('status')}, 进度: {analysis1.get('progress')}%")
        print(f"摘要: {analysis1.get('summary', '')[:80]}...")

        # 模拟一段时间后恢复（同一个 session_id）
        print("\n===== 用户稍后恢复：'继续刚才的任务' =====")
        result2 = await team.kickoff(
            inputs={"query": "继续刚才的任务"},
            session_id=session_id,
        )

        analysis2 = result2.deliverables.get("analysis", {})
        print(f"状态: {analysis2.get('status')}, 进度: {analysis2.get('progress')}%")
        print(f"摘要: {analysis2.get('summary', '')[:100]}...")

        # 验证 Agent 记住了之前的任务
        assert result2.success is True
        summary = analysis2.get("summary", "").lower()
        assert any(keyword in summary for keyword in ["销售", "报表", "地区", "产品", "继续", "分析"]), \
            f"Agent 应该记住之前的任务: {analysis2.get('summary')}"

        print("\n✓ Checkpointer 正确恢复了会话状态")
        print("✓ Agent 理解了'继续刚才的任务'的语义")
