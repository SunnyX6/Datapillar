"""
OneAgentic 框架核心功能测试

测试覆盖：
1. DelegationSignal 委派信号机制
2. AgentContext 执行上下文
3. AgentExecutor 执行器
4. AgentRegistry 注册表
5. 工具调用流程
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langgraph.types import Command
from pydantic import BaseModel, Field

from src.modules.oneagentic.core.agent import AgentRegistry, agent
from src.modules.oneagentic.core.context import AgentContext, DelegationSignal
from src.modules.oneagentic.core.types import AgentResult, Clarification
from src.modules.oneagentic.runtime.executor import AgentExecutor, clear_executor_cache

# ==================== 测试用 Schema ====================


class MockOutput(BaseModel):
    """测试用输出"""

    answer: str = Field(..., description="回答")
    confidence: float = Field(default=1.0, description="置信度")


# ==================== Fixtures ====================


@pytest.fixture(autouse=True)
def clear_registry():
    """每个测试前清空 Registry 和 Executor 缓存"""
    AgentRegistry.clear()
    clear_executor_cache()
    yield
    AgentRegistry.clear()
    clear_executor_cache()


@pytest.fixture
def mock_llm():
    """Mock LLM"""
    llm = AsyncMock()
    response = MagicMock()
    response.content = "测试响应"
    response.tool_calls = []
    llm.ainvoke.return_value = response
    llm.bind_tools.return_value = llm
    llm.with_structured_output.return_value = llm
    return llm


@pytest.fixture
def mock_spec():
    """Mock AgentSpec"""
    spec = MagicMock()
    spec.id = "test_agent"
    spec.name = "测试Agent"
    spec.max_iterations = 5
    spec.deliverable_schema = MockOutput
    spec.deliverable_key = "output"
    spec.temperature = 0.0
    spec.knowledge_domains = []
    spec.tools = []
    spec.can_delegate_to = []
    return spec


# ==================== DelegationSignal 测试 ====================


class TestDelegationSignal:
    """测试委派信号机制"""

    def test_delegation_signal_creation(self):
        """测试创建委派信号"""
        command = Command(goto="target_agent", update={"key": "value"})
        signal = DelegationSignal(command)

        assert signal.command == command
        assert signal.command.goto == "target_agent"
        assert "Delegation to target_agent" in str(signal)

    def test_delegation_signal_is_exception(self):
        """测试委派信号是异常类型"""
        command = Command(goto="other_agent")
        signal = DelegationSignal(command)

        assert isinstance(signal, Exception)

        # 测试可以被捕获
        try:
            raise signal
        except DelegationSignal as caught:
            assert caught.command.goto == "other_agent"


# ==================== AgentContext 测试 ====================


class TestAgentContext:
    """测试 AgentContext"""

    def test_context_creation(self):
        """测试创建上下文"""
        ctx = AgentContext(
            session_id="session_001",
            query="测试查询",
        )

        assert ctx.session_id == "session_001"
        assert ctx.query == "测试查询"
        assert ctx._tools == []
        assert ctx._memory is None

    def test_context_with_private_fields(self, mock_spec, mock_llm):
        """测试带私有字段的上下文"""
        ctx = AgentContext(
            session_id="session_001",
            query="测试查询",
            _spec=mock_spec,
            _llm=mock_llm,
            _tools=[],
            _state={"key": "value"},
        )

        # 公开字段可访问
        assert ctx.session_id == "session_001"
        assert ctx.query == "测试查询"

        # 私有字段存在但按约定不应使用
        assert ctx._spec == mock_spec
        assert ctx._llm == mock_llm
        assert ctx._state == {"key": "value"}

    def test_build_messages_basic(self, mock_spec):
        """测试构建消息（基本）"""
        ctx = AgentContext(
            session_id="session_001",
            query="用户问题",
            _spec=mock_spec,
        )

        messages = ctx.build_messages("你是测试助手")

        # 应该有系统消息和用户消息
        assert len(messages) >= 2
        assert messages[0].content == "你是测试助手"
        assert messages[-1].content == "用户问题"

    def test_build_messages_with_memory(self, mock_spec):
        """测试构建消息（带记忆）"""
        # 创建 mock 记忆
        mock_memory = MagicMock()
        mock_memory.to_prompt.return_value = "历史对话记录"

        ctx = AgentContext(
            session_id="session_001",
            query="用户问题",
            _spec=mock_spec,
            _memory=mock_memory,
        )

        messages = ctx.build_messages("你是测试助手")

        # 应该包含记忆内容
        all_content = " ".join([m.content for m in messages])
        assert "历史对话记录" in all_content

    @pytest.mark.asyncio
    async def test_invoke_tools_no_tools(self, mock_spec, mock_llm):
        """测试工具调用（无工具）"""
        ctx = AgentContext(
            session_id="session_001",
            query="测试",
            _spec=mock_spec,
            _llm=mock_llm,
            _tools=[],
        )

        messages = ctx.build_messages("系统提示")
        result = await ctx.invoke_tools(messages)

        # 应该直接调用 LLM
        mock_llm.ainvoke.assert_called_once()
        assert len(result) > len(messages) - 1  # 添加了响应

    @pytest.mark.asyncio
    async def test_invoke_tools_with_delegation(self, mock_spec):
        """测试工具调用触发委派"""
        # Mock LLM 返回工具调用
        response_with_tool = MagicMock()
        response_with_tool.tool_calls = [{"name": "delegate_to_other", "args": {}}]
        response_with_tool.content = ""

        # 创建 mock LLM（非 AsyncMock，因为 bind_tools 是同步方法）
        mock_llm = MagicMock()

        # bind_tools 返回的对象需要有 ainvoke 方法
        mock_llm_with_tools = MagicMock()
        mock_llm_with_tools.ainvoke = AsyncMock(return_value=response_with_tool)
        mock_llm.bind_tools.return_value = mock_llm_with_tools

        # Mock ToolNode 返回委派命令
        delegation_command = Command(goto="other_agent")

        with patch("src.modules.oneagentic.core.context.ToolNode") as mock_tool_node:
            mock_tool_node.return_value.ainvoke = AsyncMock(return_value=[delegation_command])

            ctx = AgentContext(
                session_id="session_001",
                query="测试",
                _spec=mock_spec,
                _llm=mock_llm,
                _tools=[MagicMock()],  # 有工具
            )

            messages = ctx.build_messages("系统提示")

            # 应该抛出 DelegationSignal
            with pytest.raises(DelegationSignal) as exc_info:
                await ctx.invoke_tools(messages)

            assert exc_info.value.command.goto == "other_agent"

    def test_clarify(self):
        """测试澄清请求"""
        ctx = AgentContext(
            session_id="session_001",
            query="测试",
        )

        clarification = ctx.clarify(
            message="需要更多信息",
            questions=["问题1", "问题2"],
            options=[{"label": "选项1"}],
        )

        assert isinstance(clarification, Clarification)
        assert clarification.message == "需要更多信息"
        assert len(clarification.questions) == 2
        assert len(clarification.options) == 1


# ==================== AgentExecutor 测试 ====================


class TestAgentExecutor:
    """测试 AgentExecutor"""

    def test_executor_creation(self):
        """测试创建执行器"""

        @agent(
            id="exec_test_agent",
            name="执行测试Agent",
            tools=[],
            deliverable_schema=MockOutput,
            deliverable_key="output",
        )
        class ExecTestAgent:
            SYSTEM_PROMPT = "测试"

            async def run(self, ctx: AgentContext):
                return MockOutput(answer="测试回答")

        spec = AgentRegistry.get("exec_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            assert executor.spec == spec
            assert executor.business_tools == []
            assert executor.delegation_tools == []

    @pytest.mark.asyncio
    async def test_executor_returns_agent_result(self):
        """测试执行器返回 AgentResult"""

        @agent(
            id="result_test_agent",
            name="结果测试Agent",
            tools=[],
            deliverable_schema=MockOutput,
            deliverable_key="output",
        )
        class ResultTestAgent:
            SYSTEM_PROMPT = "测试"

            async def run(self, ctx: AgentContext):
                return MockOutput(answer="成功回答", confidence=0.95)

        spec = AgentRegistry.get("result_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="测试查询",
                session_id="test_session",
            )

            assert isinstance(result, AgentResult)
            assert result.status == "completed"
            assert result.deliverable.answer == "成功回答"

    @pytest.mark.asyncio
    async def test_executor_handles_clarification(self):
        """测试执行器处理澄清请求"""

        @agent(
            id="clarify_test_agent",
            name="澄清测试Agent",
            tools=[],
        )
        class ClarifyTestAgent:
            SYSTEM_PROMPT = "测试"

            async def run(self, ctx: AgentContext):
                return ctx.clarify("需要更多信息", ["请详细说明"])

        spec = AgentRegistry.get("clarify_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="测试查询",
                session_id="test_session",
            )

            assert isinstance(result, AgentResult)
            assert result.status == "needs_clarification"

    @pytest.mark.asyncio
    async def test_executor_handles_delegation_signal(self):
        """测试执行器处理委派信号"""

        @agent(
            id="delegate_test_agent",
            name="委派测试Agent",
            tools=[],
            can_delegate_to=["other_agent"],
        )
        class DelegateTestAgent:
            SYSTEM_PROMPT = "测试"

            async def run(self, ctx: AgentContext):
                # 模拟抛出委派信号
                raise DelegationSignal(Command(goto="other_agent"))

        spec = AgentRegistry.get("delegate_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="测试查询",
                session_id="test_session",
            )

            # 应该返回 Command
            assert isinstance(result, Command)
            assert result.goto == "other_agent"

    @pytest.mark.asyncio
    async def test_executor_handles_none_result(self):
        """测试执行器处理 None 结果"""

        @agent(
            id="none_test_agent",
            name="空结果测试Agent",
            tools=[],
        )
        class NoneTestAgent:
            SYSTEM_PROMPT = "测试"

            async def run(self, ctx: AgentContext):
                return None

        spec = AgentRegistry.get("none_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="测试查询",
                session_id="test_session",
            )

            assert isinstance(result, AgentResult)
            assert result.status == "failed"

    @pytest.mark.asyncio
    async def test_executor_handles_empty_query(self):
        """测试执行器处理空查询"""

        @agent(
            id="empty_query_agent",
            name="空查询测试Agent",
            tools=[],
        )
        class EmptyQueryAgent:
            async def run(self, ctx: AgentContext):
                return MockOutput(answer="不应该执行到这里")

        spec = AgentRegistry.get("empty_query_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="",  # 空查询
                session_id="test_session",
            )

            assert isinstance(result, AgentResult)
            assert result.status == "failed"
            assert "query" in result.error.lower()


# ==================== AgentRegistry 测试 ====================


class TestAgentRegistry:
    """测试 AgentRegistry"""

    def test_register_and_get(self):
        """测试注册和获取"""

        @agent(
            id="registry_test_agent",
            name="注册测试Agent",
        )
        class RegistryTestAgent:
            async def run(self, ctx: AgentContext):
                pass

        spec = AgentRegistry.get("registry_test_agent")

        assert spec is not None
        assert spec.id == "registry_test_agent"
        assert spec.name == "注册测试Agent"

    def test_list_ids(self):
        """测试列出所有 ID"""

        @agent(id="list_test_1", name="列表测试1")
        class ListTest1:
            async def run(self, ctx):
                pass

        @agent(id="list_test_2", name="列表测试2")
        class ListTest2:
            async def run(self, ctx):
                pass

        ids = AgentRegistry.list_ids()

        assert "list_test_1" in ids
        assert "list_test_2" in ids

    def test_clear(self):
        """测试清空注册表"""

        @agent(id="clear_test", name="清空测试")
        class ClearTest:
            async def run(self, ctx):
                pass

        assert AgentRegistry.get("clear_test") is not None

        AgentRegistry.clear()

        assert AgentRegistry.get("clear_test") is None

    def test_duplicate_id_overwrites_with_warning(self):
        """测试重复 ID 会覆盖并打印警告"""

        @agent(id="dup_test", name="重复测试1")
        class DupTest1:
            async def run(self, ctx):
                pass

        # 第二次注册会覆盖第一次
        @agent(id="dup_test", name="重复测试2")
        class DupTest2:
            async def run(self, ctx):
                pass

        spec = AgentRegistry.get("dup_test")
        # 验证被覆盖为第二个
        assert spec.name == "重复测试2"


# ==================== 集成测试 ====================


class TestIntegration:
    """集成测试"""

    @pytest.mark.asyncio
    async def test_full_workflow_without_delegation(self):
        """测试完整工作流（无委派）"""

        @agent(
            id="workflow_agent",
            name="工作流Agent",
            tools=[],
            deliverable_schema=MockOutput,
            deliverable_key="output",
        )
        class WorkflowAgent:
            SYSTEM_PROMPT = "你是测试助手"

            async def run(self, ctx: AgentContext):
                # 模拟正常流程
                return MockOutput(answer="工作流完成", confidence=0.9)

        spec = AgentRegistry.get("workflow_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            result = await executor.execute(
                query="执行完整工作流",
                session_id="integration_test",
            )

            assert result.status == "completed"
            assert result.deliverable.answer == "工作流完成"
            assert result.deliverable_type == "output"
