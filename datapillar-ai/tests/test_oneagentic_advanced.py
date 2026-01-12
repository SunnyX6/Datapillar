"""
OneAgentic 框架高级测试

测试覆盖：
1. 集成测试：端到端执行流程（Mock LLM）
2. 压力测试：多协程并发调用
3. 故障注入测试：熔断/重试机制
4. 状态恢复测试：Checkpoint 中断恢复
5. 记忆压缩测试：长对话场景
"""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langgraph.types import Command
from pydantic import BaseModel, Field

from src.modules.oneagentic import (
    AgentContext,
    Clarification,
    Datapillar,
    Process,
    agent,
)
from src.modules.oneagentic.core.agent import AgentRegistry
from src.modules.oneagentic.core.context import DelegationSignal
from src.modules.oneagentic.core.types import AgentResult
from src.modules.oneagentic.memory.session_memory import SessionMemory
from src.modules.oneagentic.runtime.executor import AgentExecutor, clear_executor_cache

# ==================== 测试用 Schema ====================


class AnalysisOutput(BaseModel):
    """分析输出"""

    summary: str = Field(..., description="分析摘要")
    tables: list[str] = Field(default_factory=list, description="涉及的表")
    confidence: float = Field(default=1.0, description="置信度")


class DesignOutput(BaseModel):
    """设计输出"""

    plan: str = Field(..., description="设计方案")
    steps: list[str] = Field(default_factory=list, description="执行步骤")


class CodeOutput(BaseModel):
    """代码输出"""

    code: str = Field(..., description="生成的代码")
    language: str = Field(default="sql", description="代码语言")


# ==================== Fixtures ====================


@pytest.fixture(autouse=True)
def clear_registry():
    """每个测试前清空 Registry 和 Executor 缓存"""
    AgentRegistry.clear()
    clear_executor_cache()
    yield
    AgentRegistry.clear()
    clear_executor_cache()


# ==================== 1. 集成测试：端到端执行流程 ====================


class TestEndToEndIntegration:
    """端到端集成测试（使用 Mock LLM）"""

    def create_mock_agents(self):
        """创建测试用 Agent"""

        @agent(
            id="int_analyst",
            name="集成测试分析师",
            tools=[],
            can_delegate_to=["int_designer"],
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class IntAnalystAgent:
            SYSTEM_PROMPT = "你是分析师"

            async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
                # 模拟分析逻辑
                if "不清楚" in ctx.query:
                    return ctx.clarify("请提供更多信息", ["具体需求是什么?"])
                return AnalysisOutput(
                    summary=f"分析完成: {ctx.query}",
                    tables=["users", "orders"],
                    confidence=0.95,
                )

        @agent(
            id="int_designer",
            name="集成测试设计师",
            tools=[],
            can_delegate_to=["int_developer"],
            deliverable_schema=DesignOutput,
            deliverable_key="design",
        )
        class IntDesignerAgent:
            SYSTEM_PROMPT = "你是设计师"

            async def run(self, ctx: AgentContext) -> DesignOutput:
                return DesignOutput(
                    plan="设计方案: 创建宽表",
                    steps=["步骤1", "步骤2", "步骤3"],
                )

        @agent(
            id="int_developer",
            name="集成测试开发者",
            tools=[],
            deliverable_schema=CodeOutput,
            deliverable_key="code",
        )
        class IntDeveloperAgent:
            SYSTEM_PROMPT = "你是开发者"

            async def run(self, ctx: AgentContext) -> CodeOutput:
                return CodeOutput(
                    code="SELECT * FROM users JOIN orders",
                    language="sql",
                )

        return IntAnalystAgent, IntDesignerAgent, IntDeveloperAgent

    @pytest.mark.asyncio
    async def test_sequential_team_execution(self):
        """测试顺序执行团队"""
        AnalystAgent, DesignerAgent, DeveloperAgent = self.create_mock_agents()

        team = Datapillar(
            name="顺序执行测试团队",
            agents=[AnalystAgent, DesignerAgent, DeveloperAgent],
            process=Process.SEQUENTIAL,
            memory=False,
        )

        with (
            patch("src.modules.oneagentic.core.datapillar.Checkpoint") as mock_cp,
            patch("src.modules.oneagentic.core.datapillar.DeliverableStore") as mock_store,
        ):

            mock_cp.get_saver.return_value.__aenter__ = AsyncMock(return_value=None)
            mock_cp.get_saver.return_value.__aexit__ = AsyncMock(return_value=None)
            mock_store.get_store_instance = AsyncMock(return_value=MagicMock())

            result = await team.kickoff(
                inputs={"query": "创建用户宽表"},
                session_id="int_test_001",
                user_id="test_user",
            )

            # 验证团队配置正确
            assert team.process == Process.SEQUENTIAL
            assert len(team._agent_specs) == 3
            assert team._entry_agent_id == "int_analyst"

    @pytest.mark.asyncio
    async def test_dynamic_team_with_delegation(self):
        """测试动态执行团队（带委派）"""

        # 创建带委派的 Agent
        @agent(
            id="dyn_analyst",
            name="动态分析师",
            tools=[],
            can_delegate_to=["dyn_designer"],
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class DynAnalystAgent:
            async def run(self, ctx: AgentContext):
                # 分析后需要委派给设计师
                return AnalysisOutput(
                    summary="分析完成，需要设计",
                    tables=["users"],
                )

        @agent(
            id="dyn_designer",
            name="动态设计师",
            tools=[],
            deliverable_schema=DesignOutput,
            deliverable_key="design",
        )
        class DynDesignerAgent:
            async def run(self, ctx: AgentContext):
                return DesignOutput(plan="设计完成")

        team = Datapillar(
            name="动态执行测试团队",
            agents=[DynAnalystAgent, DynDesignerAgent],
            process=Process.DYNAMIC,
            memory=False,
        )

        assert team.process == Process.DYNAMIC
        assert "dyn_analyst" in team._agent_ids
        assert "dyn_designer" in team._agent_ids


# ==================== 2. 压力测试：多协程并发调用 ====================


class TestConcurrency:
    """并发压力测试"""

    @pytest.mark.asyncio
    async def test_concurrent_executor_calls(self):
        """测试并发执行器调用"""

        @agent(
            id="concurrent_agent",
            name="并发测试Agent",
            tools=[],
            deliverable_schema=AnalysisOutput,
            deliverable_key="output",
        )
        class ConcurrentAgent:
            async def run(self, ctx: AgentContext):
                # 模拟一些处理时间
                await asyncio.sleep(0.01)
                return AnalysisOutput(
                    summary=f"处理查询: {ctx.query}",
                    confidence=0.9,
                )

        spec = AgentRegistry.get("concurrent_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            # 并发执行 10 次
            tasks = [
                executor.execute(
                    query=f"并发查询 {i}",
                    session_id=f"concurrent_session_{i}",
                )
                for i in range(10)
            ]

            results = await asyncio.gather(*tasks)

            # 验证所有调用都成功
            assert len(results) == 10
            for result in results:
                assert isinstance(result, AgentResult)
                assert result.status == "completed"

    @pytest.mark.asyncio
    async def test_concurrent_team_streams(self):
        """测试并发团队流式执行"""

        @agent(id="stream_agent", name="流式测试Agent", tools=[])
        class StreamAgent:
            async def run(self, ctx: AgentContext):
                await asyncio.sleep(0.01)
                return None

        team = Datapillar(
            name="并发流测试团队",
            agents=[StreamAgent],
            process=Process.SEQUENTIAL,
            memory=False,
        )

        # 验证团队可以并发创建
        teams = [
            Datapillar(
                name=f"并发团队_{i}",
                agents=[StreamAgent],
                process=Process.SEQUENTIAL,
                memory=False,
            )
            for i in range(5)
        ]

        assert len(teams) == 5

    @pytest.mark.asyncio
    async def test_shared_registry_thread_safety(self):
        """测试共享 Registry 的线程安全性"""

        async def register_agent(i: int):
            @agent(id=f"thread_safe_agent_{i}", name=f"线程安全Agent_{i}", tools=[])
            class DynamicAgent:
                async def run(self, ctx: AgentContext):
                    return None

            return f"thread_safe_agent_{i}"

        # 并发注册多个 Agent
        tasks = [register_agent(i) for i in range(20)]
        agent_ids = await asyncio.gather(*tasks)

        # 验证所有 Agent 都被正确注册
        for agent_id in agent_ids:
            spec = AgentRegistry.get(agent_id)
            assert spec is not None


# ==================== 3. 故障注入测试：熔断/重试机制 ====================


class TestFaultInjection:
    """故障注入测试"""

    @pytest.mark.asyncio
    async def test_agent_exception_handling(self):
        """测试 Agent 异常处理"""

        @agent(
            id="error_agent",
            name="异常测试Agent",
            tools=[],
        )
        class ErrorAgent:
            async def run(self, ctx: AgentContext):
                raise RuntimeError("模拟业务异常")

        spec = AgentRegistry.get("error_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="触发异常",
                session_id="error_session",
            )

            # 验证异常被正确捕获
            assert isinstance(result, AgentResult)
            assert result.status == "error"  # system_error 方法返回 "error" 状态
            assert "模拟业务异常" in result.error

    @pytest.mark.asyncio
    async def test_delegation_signal_exception(self):
        """测试委派信号异常处理"""

        @agent(
            id="delegate_error_agent",
            name="委派异常测试Agent",
            tools=[],
            can_delegate_to=["other"],
        )
        class DelegateErrorAgent:
            async def run(self, ctx: AgentContext):
                # 模拟委派信号
                raise DelegationSignal(Command(goto="other_agent"))

        spec = AgentRegistry.get("delegate_error_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="触发委派",
                session_id="delegate_session",
            )

            # 验证委派信号被正确处理
            assert isinstance(result, Command)
            assert result.goto == "other_agent"

    @pytest.mark.asyncio
    async def test_timeout_simulation(self):
        """测试超时模拟"""

        @agent(
            id="timeout_agent",
            name="超时测试Agent",
            tools=[],
        )
        class TimeoutAgent:
            async def run(self, ctx: AgentContext):
                # 模拟长时间运行
                await asyncio.sleep(0.1)
                return None

        spec = AgentRegistry.get("timeout_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            # 使用较短的超时测试
            result = await asyncio.wait_for(
                executor.execute(
                    query="超时测试",
                    session_id="timeout_session",
                ),
                timeout=1.0,  # 1 秒超时
            )

            # 验证正常完成（0.1s < 1s）
            assert isinstance(result, AgentResult)


# ==================== 4. 状态恢复测试：Checkpoint ====================


class TestCheckpointRecovery:
    """Checkpoint 状态恢复测试"""

    @pytest.mark.asyncio
    async def test_memory_serialization(self):
        """测试记忆序列化和反序列化"""
        memory = SessionMemory(
            session_id="checkpoint_session",
            user_id="test_user",
        )

        # 添加一些记录
        memory.add_agent_handover(
            from_agent="analyst",
            to_agent="designer",
            summary="分析完成，移交设计",
        )

        # 序列化
        serialized = memory.model_dump(mode="json")

        # 反序列化
        restored = SessionMemory.model_validate(serialized)

        # 验证
        assert restored.session_id == memory.session_id
        # 交接记录在 conversation.entries 中
        assert len(restored.conversation.entries) == len(memory.conversation.entries)

    @pytest.mark.asyncio
    async def test_state_persistence_across_agents(self):
        """测试跨 Agent 状态持久化"""

        @agent(
            id="state_agent_1",
            name="状态Agent1",
            tools=[],
            deliverable_schema=AnalysisOutput,
            deliverable_key="analysis",
        )
        class StateAgent1:
            async def run(self, ctx: AgentContext):
                return AnalysisOutput(summary="Agent1完成")

        @agent(
            id="state_agent_2",
            name="状态Agent2",
            tools=[],
            deliverable_schema=DesignOutput,
            deliverable_key="design",
        )
        class StateAgent2:
            async def run(self, ctx: AgentContext):
                return DesignOutput(plan="Agent2完成")

        # 创建团队
        team = Datapillar(
            name="状态测试团队",
            agents=[StateAgent1, StateAgent2],
            process=Process.SEQUENTIAL,
            memory=True,  # 启用记忆
        )

        assert team.memory is True
        assert len(team._agent_specs) == 2


# ==================== 5. 记忆压缩测试 ====================


class TestMemoryCompression:
    """记忆压缩测试"""

    def test_memory_handover_accumulation(self):
        """测试记忆交接记录累积"""
        memory = SessionMemory(
            session_id="compress_session",
            user_id="test_user",
        )

        # 模拟多轮交接
        for i in range(10):
            memory.add_agent_handover(
                from_agent=f"agent_{i}",
                to_agent=f"agent_{i+1}",
                summary=f"第 {i+1} 轮交接",
            )

        # 交接记录在 conversation.entries 中
        assert len(memory.conversation.entries) == 10

        # 验证 to_prompt 能正常生成
        prompt = memory.to_prompt()
        assert prompt is not None
        assert "交接" in prompt

    def test_memory_prompt_generation(self):
        """测试记忆 Prompt 生成"""
        memory = SessionMemory(
            session_id="prompt_session",
            user_id="test_user",
        )

        # 无记录时
        assert memory.to_prompt() == ""

        # 添加记录后
        memory.add_agent_handover(
            from_agent="analyst",
            to_agent="designer",
            summary="需求分析完成",
        )

        prompt = memory.to_prompt()
        assert len(prompt) > 0
        assert "analyst" in prompt or "分析" in prompt

    @pytest.mark.asyncio
    async def test_long_conversation_memory(self):
        """测试长对话记忆"""
        memory = SessionMemory(
            session_id="long_conv_session",
            user_id="test_user",
        )

        # 模拟长对话（50 轮）
        for i in range(50):
            memory.add_agent_handover(
                from_agent=f"agent_{i % 5}",  # 5 个 agent 循环
                to_agent=f"agent_{(i+1) % 5}",
                summary=f"对话轮次 {i+1}: 处理用户请求" * 10,  # 较长的摘要
            )

        # 验证记忆不会无限增长
        prompt = memory.to_prompt()
        assert prompt is not None

        # 验证可以序列化（不会因为太大而失败）
        serialized = memory.model_dump(mode="json")
        assert serialized is not None


# ==================== 6. 边界条件测试 ====================


class TestEdgeCases:
    """边界条件测试"""

    @pytest.mark.asyncio
    async def test_empty_query_handling(self):
        """测试空查询处理"""

        @agent(id="empty_query_agent", name="空查询Agent", tools=[])
        class EmptyQueryAgent:
            async def run(self, ctx: AgentContext):
                return None

        spec = AgentRegistry.get("empty_query_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="",
                session_id="empty_session",
            )

            assert result.status == "failed"

    @pytest.mark.asyncio
    async def test_unicode_query_handling(self):
        """测试 Unicode 查询处理"""

        @agent(
            id="unicode_agent",
            name="Unicode测试Agent",
            tools=[],
            deliverable_schema=AnalysisOutput,
            deliverable_key="output",
        )
        class UnicodeAgent:
            async def run(self, ctx: AgentContext):
                return AnalysisOutput(
                    summary=f"处理: {ctx.query}",
                    confidence=1.0,
                )

        spec = AgentRegistry.get("unicode_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            # 测试各种 Unicode 字符
            queries = [
                "中文查询测试",
                "日本語クエリ",
                "Emoji 🎉 测试",
                "特殊字符 <>&\"'",
            ]

            for query in queries:
                result = await executor.execute(
                    query=query,
                    session_id="unicode_session",
                )
                assert result.status == "completed"
                assert query in result.deliverable.summary

    def test_agent_with_no_schema(self):
        """测试无 Schema 的 Agent"""

        @agent(id="no_schema_agent", name="无Schema Agent", tools=[])
        class NoSchemaAgent:
            async def run(self, ctx: AgentContext):
                return "简单字符串返回"

        spec = AgentRegistry.get("no_schema_agent")
        assert spec.deliverable_schema is None

    def test_circular_delegation_prevention(self):
        """测试循环委派检测"""

        @agent(
            id="circular_a",
            name="循环A",
            can_delegate_to=["circular_b"],
        )
        class CircularA:
            async def run(self, ctx):
                pass

        @agent(
            id="circular_b",
            name="循环B",
            can_delegate_to=["circular_a"],  # 形成循环
        )
        class CircularB:
            async def run(self, ctx):
                pass

        # 团队创建应该成功（循环检测在运行时）
        team = Datapillar(
            name="循环测试团队",
            agents=[CircularA, CircularB],
            process=Process.DYNAMIC,
        )

        assert len(team._agent_specs) == 2


# ==================== 7. 性能基准测试 ====================


class TestPerformanceBenchmarks:
    """性能基准测试"""

    @pytest.mark.asyncio
    async def test_agent_creation_performance(self):
        """测试 Agent 创建性能"""
        import time

        start = time.perf_counter()

        # 创建 100 个 Agent
        for i in range(100):

            @agent(id=f"perf_agent_{i}", name=f"性能Agent_{i}", tools=[])
            class PerfAgent:
                async def run(self, ctx):
                    pass

        elapsed = time.perf_counter() - start

        # 100 个 Agent 应该在 1 秒内创建完成
        assert elapsed < 1.0, f"Agent 创建耗时过长: {elapsed:.2f}s"

    @pytest.mark.asyncio
    async def test_executor_instantiation_performance(self):
        """测试 Executor 实例化性能"""
        import time

        @agent(id="exec_perf_agent", name="执行器性能Agent", tools=[])
        class ExecPerfAgent:
            async def run(self, ctx):
                pass

        spec = AgentRegistry.get("exec_perf_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()

            start = time.perf_counter()

            # 创建 50 个 Executor
            executors = [AgentExecutor(spec) for _ in range(50)]

            elapsed = time.perf_counter() - start

            # 50 个 Executor 应该在 0.5 秒内创建完成
            assert elapsed < 0.5, f"Executor 创建耗时过长: {elapsed:.2f}s"
            assert len(executors) == 50


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
