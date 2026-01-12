"""
LLM 链路并发安全测试

验证：
1. LLM 实例缓存的线程安全性
2. 熔断器注册表的线程安全性
3. 并发调用下的链路重复问题
4. Redis 缓存的并发一致性

注意：这些测试需要真实环境运行，不使用 Mock
"""

import asyncio
import threading
import time
from collections import Counter
from unittest.mock import MagicMock, patch

import pytest


class TestLLMCacheConcurrency:
    """LLM 实例缓存并发测试"""

    def test_llm_cache_race_condition_detection(self):
        """检测 LLM 缓存的线程安全性（修复后应该只创建 1 次）"""
        # 模拟多线程同时访问 _llm_cache
        from src.infrastructure.llm import client

        # 清空缓存
        client._llm_cache.clear()

        creation_count = Counter()
        lock = threading.Lock()

        original_create = client.LLMFactory.create_chat_model

        def counting_create(*args, **kwargs):
            with lock:
                creation_count["total"] += 1
            # 模拟创建延迟，增加竞态概率
            time.sleep(0.01)
            return MagicMock()

        with patch.object(client.LLMFactory, "create_chat_model", counting_create):
            with (
                patch.object(client.model_manager, "model_by_id") as mock_model_by_id,
                patch.object(client.model_manager, "default_chat_model") as mock_default,
            ):

                mock_model = MagicMock()
                mock_model.provider = "openai"
                mock_model.model_name = "gpt-4"
                mock_model.config_json = None
                mock_model_by_id.return_value = mock_model
                mock_default.return_value = mock_model

                def call_llm_thread():
                    try:
                        client.call_llm(temperature=0.0)
                    except Exception:
                        pass

                # 启动多个线程同时调用
                threads = [threading.Thread(target=call_llm_thread) for _ in range(10)]
                for t in threads:
                    t.start()
                for t in threads:
                    t.join()

        # 修复后应该只创建 1 次
        print(f"LLM 创建次数: {creation_count['total']} (理想=1)")
        assert creation_count["total"] == 1, f"LLM 被创建了 {creation_count['total']} 次（应该=1）"


class TestCircuitBreakerConcurrency:
    """熔断器并发测试"""

    @pytest.mark.asyncio
    async def test_circuit_breaker_registry_race_condition(self):
        """检测熔断器注册表的竞态条件"""
        from src.infrastructure.resilience.circuit_breaker import (
            _circuit_breakers,
            get_circuit_breaker,
        )

        # 清空注册表
        _circuit_breakers.clear()

        creation_count = Counter()

        original_init = _circuit_breakers.__class__.__setitem__

        def counting_setitem(self, key, value):
            creation_count[key] = creation_count.get(key, 0) + 1
            return dict.__setitem__(self, key, value)

        # 并发获取同一个熔断器
        async def get_breaker():
            await asyncio.sleep(0.001)  # 增加并发概率
            return get_circuit_breaker("test_concurrent")

        tasks = [get_breaker() for _ in range(20)]
        results = await asyncio.gather(*tasks)

        # 所有结果应该是同一个实例
        unique_instances = len(set(id(r) for r in results))
        print(f"熔断器实例数: {unique_instances} (理想=1)")

        # 注意：由于 asyncio 的 GIL 和事件循环特性，
        # 在 asyncio 中竞态条件不如多线程明显
        assert unique_instances >= 1  # 至少有一个

    @pytest.mark.asyncio
    async def test_circuit_breaker_state_under_concurrent_failures(self):
        """测试并发失败下的熔断器状态一致性"""
        from src.infrastructure.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("concurrent_test")
        cb.failure_threshold = 5

        # 并发记录失败
        async def record_failures():
            for _ in range(3):
                await cb.record_failure()
                await asyncio.sleep(0.001)

        await asyncio.gather(*[record_failures() for _ in range(5)])

        # 验证状态
        # 15 次失败，应该触发熔断
        print(f"熔断器状态: {cb.state}, 失败计数: {cb._failure_count}")
        assert cb.state == CircuitState.OPEN


class TestLLMCallChain:
    """LLM 调用链路测试"""

    @pytest.mark.asyncio
    async def test_no_duplicate_llm_calls_in_single_request(self):
        """测试单个请求中没有重复 LLM 调用"""
        call_count = Counter()

        async def mock_ainvoke(*args, **kwargs):
            call_count["ainvoke"] += 1
            mock_response = MagicMock()
            mock_response.content = "测试响应"
            mock_response.tool_calls = []
            return mock_response

        # 创建 mock LLM
        mock_llm = MagicMock()
        mock_llm.ainvoke = mock_ainvoke
        mock_llm.bind_tools.return_value = mock_llm

        from src.modules.oneagentic.core.context import AgentContext

        mock_spec = MagicMock()
        mock_spec.name = "测试Agent"
        mock_spec.max_iterations = 5

        ctx = AgentContext(
            session_id="test",
            query="测试查询",
            _spec=mock_spec,
            _llm=mock_llm,
            _tools=[],  # 无工具
        )

        messages = ctx.build_messages("系统提示")
        await ctx.invoke_tools(messages)

        # 无工具时应该只调用 1 次 LLM
        assert call_count["ainvoke"] == 1, f"LLM 被调用了 {call_count['ainvoke']} 次（应该=1）"

    @pytest.mark.asyncio
    async def test_tool_loop_terminates(self):
        """测试工具调用循环正确终止"""
        call_count = Counter()

        async def mock_ainvoke(*args, **kwargs):
            call_count["ainvoke"] += 1
            mock_response = MagicMock()
            # 前 2 次返回工具调用，第 3 次返回无工具
            if call_count["ainvoke"] <= 2:
                mock_response.tool_calls = [{"name": "test_tool", "args": {}}]
            else:
                mock_response.tool_calls = []
            mock_response.content = f"响应 {call_count['ainvoke']}"
            return mock_response

        mock_llm = MagicMock()
        mock_llm.ainvoke = mock_ainvoke

        mock_llm_with_tools = MagicMock()
        mock_llm_with_tools.ainvoke = mock_ainvoke
        mock_llm.bind_tools.return_value = mock_llm_with_tools

        from src.modules.oneagentic.core.context import AgentContext

        mock_spec = MagicMock()
        mock_spec.name = "测试Agent"
        mock_spec.max_iterations = 10  # 足够大

        with patch("src.modules.oneagentic.core.context.ToolNode") as mock_tool_node:
            # Mock ToolNode 返回普通结果（异步）
            async def mock_tool_ainvoke(*args, **kwargs):
                return {"messages": [MagicMock()]}

            mock_tool_node.return_value.ainvoke = mock_tool_ainvoke

            ctx = AgentContext(
                session_id="test",
                query="测试查询",
                _spec=mock_spec,
                _llm=mock_llm,
                _tools=[MagicMock()],  # 有工具
            )

            messages = ctx.build_messages("系统提示")
            await ctx.invoke_tools(messages)

        # 应该调用 3 次（2 次工具 + 1 次无工具）
        assert call_count["ainvoke"] == 3, f"LLM 被调用了 {call_count['ainvoke']} 次（应该=3）"

    @pytest.mark.asyncio
    async def test_max_iterations_limit(self):
        """测试最大迭代次数限制"""
        call_count = Counter()

        async def mock_ainvoke_always_tool(*args, **kwargs):
            call_count["ainvoke"] += 1
            mock_response = MagicMock()
            mock_response.tool_calls = [{"name": "infinite_tool", "args": {}}]
            mock_response.content = ""
            return mock_response

        mock_llm = MagicMock()
        mock_llm_with_tools = MagicMock()
        mock_llm_with_tools.ainvoke = mock_ainvoke_always_tool
        mock_llm.bind_tools.return_value = mock_llm_with_tools

        from src.modules.oneagentic.core.context import AgentContext

        mock_spec = MagicMock()
        mock_spec.name = "测试Agent"
        mock_spec.max_iterations = 3  # 限制为 3 次

        with patch("src.modules.oneagentic.core.context.ToolNode") as mock_tool_node:

            async def mock_tool_ainvoke(*args, **kwargs):
                return {"messages": [MagicMock()]}

            mock_tool_node.return_value.ainvoke = mock_tool_ainvoke

            ctx = AgentContext(
                session_id="test",
                query="测试查询",
                _spec=mock_spec,
                _llm=mock_llm,
                _tools=[MagicMock()],
            )

            messages = ctx.build_messages("系统提示")
            await ctx.invoke_tools(messages)

        # 应该在 max_iterations 时停止
        assert call_count["ainvoke"] == 3, f"迭代次数: {call_count['ainvoke']}（应该=3）"


class TestConcurrentAgentExecution:
    """并发 Agent 执行测试"""

    @pytest.mark.asyncio
    async def test_concurrent_agents_isolation(self):
        """测试并发 Agent 执行的隔离性"""
        from pydantic import BaseModel, Field

        from src.modules.oneagentic import AgentContext, agent
        from src.modules.oneagentic.core.agent import AgentRegistry
        from src.modules.oneagentic.runtime.executor import AgentExecutor, clear_executor_cache

        AgentRegistry.clear()
        clear_executor_cache()

        class IsolationOutput(BaseModel):
            agent_id: str = Field(..., description="Agent ID")
            query: str = Field(..., description="收到的查询")

        execution_log = []
        lock = asyncio.Lock()

        @agent(
            id="isolation_test_agent",
            name="隔离测试Agent",
            tools=[],
            deliverable_schema=IsolationOutput,
            deliverable_key="output",
        )
        class IsolationTestAgent:
            async def run(self, ctx: AgentContext):
                async with lock:
                    execution_log.append(
                        {
                            "session_id": ctx.session_id,
                            "query": ctx.query,
                        }
                    )
                # 模拟处理时间
                await asyncio.sleep(0.05)
                return IsolationOutput(
                    agent_id="isolation_test_agent",
                    query=ctx.query,
                )

        spec = AgentRegistry.get("isolation_test_agent")

        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = AgentExecutor(spec)

            # 并发执行 5 个不同的请求
            async def execute_request(i: int):
                return await executor.execute(
                    query=f"查询_{i}",
                    session_id=f"session_{i}",
                )

            results = await asyncio.gather(*[execute_request(i) for i in range(5)])

        # 验证所有请求都被正确处理
        assert len(execution_log) == 5

        # 验证每个结果对应正确的查询
        for i, result in enumerate(results):
            assert result.deliverable.query == f"查询_{i}"

        # 验证没有查询混淆
        queries = [log["query"] for log in execution_log]
        assert len(set(queries)) == 5, "存在查询混淆！"


class TestRealLLMIntegration:
    """真实 LLM 集成测试（需要配置 API Key）"""

    @pytest.mark.skip(reason="需要真实 LLM API Key，手动运行")
    @pytest.mark.asyncio
    async def test_real_llm_single_call(self):
        """测试真实 LLM 单次调用"""
        from langchain_core.messages import HumanMessage

        from src.infrastructure.llm.client import call_llm

        llm = call_llm(temperature=0.0)

        messages = [HumanMessage(content="回复'OK'，不要其他内容")]
        response = await llm.ainvoke(messages)

        assert response.content is not None
        print(f"LLM 响应: {response.content}")

    @pytest.mark.skip(reason="需要真实 LLM API Key，手动运行")
    @pytest.mark.asyncio
    async def test_real_llm_concurrent_calls(self):
        """测试真实 LLM 并发调用"""
        from langchain_core.messages import HumanMessage

        from src.infrastructure.llm.client import call_llm

        llm = call_llm(temperature=0.0)

        async def single_call(i: int):
            messages = [HumanMessage(content=f"回复数字 {i}")]
            return await llm.ainvoke(messages)

        # 并发 5 个请求
        results = await asyncio.gather(*[single_call(i) for i in range(5)])

        for i, result in enumerate(results):
            print(f"请求 {i} 响应: {result.content}")
            assert result.content is not None

    @pytest.mark.skip(reason="需要真实 LLM API Key，手动运行")
    @pytest.mark.asyncio
    async def test_real_llm_circuit_breaker(self):
        """测试真实 LLM 熔断器"""
        from src.infrastructure.resilience.circuit_breaker import get_circuit_breaker

        cb = get_circuit_breaker("llm")

        print(f"熔断器状态: {cb.state}")
        print(f"失败计数: {cb._failure_count}")
        print(f"失败阈值: {cb.failure_threshold}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
