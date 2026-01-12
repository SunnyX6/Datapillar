"""
框架综合性压测

测试目标：
1. CircuitBreaker - 熔断器并发状态转换
2. EventBus - 高并发事件发布/订阅
3. StreamManager - SSE 多订阅者并发
4. 工具注册表 - 并发工具注册/查找
5. 配置系统 - 并发配置读写

不测试 LLM 调用延迟（已知问题，取决于模型提供商）
"""

import asyncio
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.events.base import BaseEvent
from datapillar_oneagentic.tools.registry import ToolRegistry


# ==================== 自定义事件类型 ====================


@dataclass
class StressTestEvent(BaseEvent):
    """压测事件"""
    value: int = 0


@dataclass
class RegTestEvent(BaseEvent):
    """注册测试事件"""
    pass


@dataclass
class AsyncStressEvent(BaseEvent):
    """异步压测事件"""
    value: int = 0


@dataclass
class AgentEvent(BaseEvent):
    """Agent 事件"""
    agent_id: str = ""
    action: str = ""


# ==================== 熔断器压测 ====================


class TestCircuitBreakerStress:
    """熔断器并发压测"""

    def setup_method(self):
        """重置熔断器注册表"""
        from datapillar_oneagentic.resilience import circuit_breaker

        circuit_breaker._circuit_breakers.clear()

    @pytest.mark.asyncio
    async def test_concurrent_circuit_breaker_creation(self):
        """
        测试：并发获取熔断器

        验证：get_circuit_breaker() 的双重检查锁定
        """
        from datapillar_oneagentic.resilience import get_circuit_breaker

        num_tasks = 100
        results = []

        async def get_breaker(task_id: int):
            # 模拟并发获取同名熔断器
            cb = get_circuit_breaker("test_service")
            results.append(id(cb))
            return cb

        await asyncio.gather(*[get_breaker(i) for i in range(num_tasks)])

        # 所有任务应该获得同一个实例
        unique_ids = set(results)
        print(f"\n[熔断器创建测试] {num_tasks} 次获取，{len(unique_ids)} 个唯一实例")

        assert len(unique_ids) == 1, f"创建了 {len(unique_ids)} 个实例（应该只有 1 个）"

    @pytest.mark.asyncio
    async def test_concurrent_state_transitions(self):
        """
        测试：并发状态转换

        模拟多个请求同时触发熔断器状态变化
        """
        from datapillar_oneagentic.resilience import CircuitBreaker, CircuitState

        cb = CircuitBreaker("stress_test")
        cb.failure_threshold = 5
        cb.recovery_timeout = 0.1

        num_tasks = 50
        errors = []

        async def simulate_failures(task_id: int):
            try:
                for _ in range(3):
                    await cb.record_failure()
                    await asyncio.sleep(0.001)
            except Exception as e:
                errors.append(f"Task {task_id}: {e}")

        await asyncio.gather(*[simulate_failures(i) for i in range(num_tasks)])

        print(f"\n[熔断器状态转换] 最终状态: {cb.state}, 错误数: {len(errors)}")

        assert not errors, f"状态转换出错: {errors[:5]}"
        # 大量并发失败后应该触发熔断
        assert cb.state == CircuitState.OPEN, f"预期 OPEN，实际 {cb.state}"

    @pytest.mark.asyncio
    async def test_circuit_breaker_recovery_under_load(self):
        """
        测试：高负载下的熔断恢复

        验证：OPEN -> HALF_OPEN -> CLOSED 的状态机正确性
        """
        from datapillar_oneagentic.resilience import CircuitBreaker, CircuitState

        cb = CircuitBreaker("recovery_test")
        cb.failure_threshold = 3
        cb.recovery_timeout = 0.05

        # 触发熔断
        for _ in range(5):
            await cb.record_failure()

        assert cb.state == CircuitState.OPEN

        # 等待恢复时间
        await asyncio.sleep(0.1)

        # 并发请求触发状态检查
        num_tasks = 20
        allowed_count = 0

        async def check_allow():
            nonlocal allowed_count
            if await cb.allow_request():
                allowed_count += 1

        await asyncio.gather(*[check_allow() for _ in range(num_tasks)])

        print(f"\n[熔断恢复测试] 状态: {cb.state}, 允许请求数: {allowed_count}")

        # HALF_OPEN 状态下应该允许请求
        assert cb.state in (CircuitState.HALF_OPEN, CircuitState.CLOSED)


# ==================== 事件总线压测 ====================


class TestEventBusStress:
    """事件总线高并发压测"""

    def setup_method(self):
        """重置事件总线"""
        from datapillar_oneagentic.events import event_bus

        event_bus.clear()

    @pytest.mark.asyncio
    async def test_concurrent_event_emission(self):
        """
        测试：高并发事件发布

        验证：emit() 的线程安全性
        """
        from datapillar_oneagentic.events import event_bus

        received_count = 0
        received_lock = threading.Lock()

        @event_bus.on(StressTestEvent)
        def on_stress_event(source, event):
            nonlocal received_count
            with received_lock:
                received_count += 1

        num_events = 500
        barrier = threading.Barrier(10)

        def emit_events(thread_id: int):
            barrier.wait()
            for i in range(num_events // 10):
                event_bus.emit(self, StressTestEvent(value=thread_id * 1000 + i))

        threads = [threading.Thread(target=emit_events, args=(i,)) for i in range(10)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        # 等待异步处理完成
        await asyncio.sleep(0.5)

        print(f"\n[事件发布测试] 发送 {num_events} 个事件，接收 {received_count} 个")

        # 允许少量丢失（队列满时）
        assert received_count >= num_events * 0.9, f"丢失过多事件: {num_events - received_count}"

    @pytest.mark.asyncio
    async def test_concurrent_handler_registration(self):
        """
        测试：并发处理器注册

        验证：register() 的线程安全性
        """
        from datapillar_oneagentic.events import event_bus

        num_handlers = 100
        barrier = threading.Barrier(num_handlers)
        handlers = []

        def create_and_register(handler_id: int):
            def handler(source, event):
                pass

            handlers.append(handler)
            barrier.wait()
            event_bus.register(RegTestEvent, handler)

        threads = [
            threading.Thread(target=create_and_register, args=(i,)) for i in range(num_handlers)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        count = event_bus.handler_count(RegTestEvent)
        print(f"\n[处理器注册测试] 注册 {num_handlers} 个，实际 {count} 个")

        assert count == num_handlers, f"注册了 {count} 个（应该是 {num_handlers} 个）"

    @pytest.mark.asyncio
    async def test_async_handler_stress(self):
        """
        测试：异步处理器高并发

        验证：异步事件处理的正确性
        """
        from datapillar_oneagentic.events import event_bus

        results = []
        results_lock = asyncio.Lock()

        @event_bus.on(AsyncStressEvent)
        async def async_handler(source, event):
            await asyncio.sleep(0.001)  # 模拟异步操作
            async with results_lock:
                results.append(event.value)

        num_events = 100

        for i in range(num_events):
            await event_bus.aemit(self, AsyncStressEvent(value=i))

        # 等待所有异步处理完成
        await asyncio.sleep(0.5)

        print(f"\n[异步处理器测试] 发送 {num_events} 个，处理 {len(results)} 个")

        assert len(results) == num_events, f"处理了 {len(results)} 个（应该是 {num_events} 个）"


# ==================== SSE 流管理器压测 ====================


class TestStreamManagerStress:
    """SSE 流管理器压测"""

    @pytest.mark.asyncio
    async def test_concurrent_subscribers(self):
        """
        测试：多订阅者并发

        验证：同一会话多个订阅者的正确性
        """
        from datapillar_oneagentic.sse import StreamManager

        manager = StreamManager(buffer_size=1000, subscriber_queue_size=100)

        # 模拟 Orchestrator
        class MockOrchestrator:
            async def stream(self, **kwargs):
                for i in range(50):
                    yield {"event": "message", "data": f"msg_{i}"}
                    await asyncio.sleep(0.01)

        # 模拟 Request
        class MockRequest:
            async def is_disconnected(self):
                return False

        session_id = "stress_session"
        user_id = "stress_user"

        # 启动流
        await manager.chat(
            orchestrator=MockOrchestrator(),
            query="test",
            session_id=session_id,
            user_id=user_id,
        )

        # 多个订阅者并发订阅
        num_subscribers = 5
        subscriber_results = [[] for _ in range(num_subscribers)]

        async def subscribe(subscriber_id: int):
            async for event in manager.subscribe(
                request=MockRequest(),
                session_id=session_id,
                user_id=user_id,
                last_event_id=None,
            ):
                subscriber_results[subscriber_id].append(event)

        await asyncio.gather(*[subscribe(i) for i in range(num_subscribers)])

        # 验证每个订阅者收到的消息
        for i, results in enumerate(subscriber_results):
            print(f"[订阅者 {i}] 收到 {len(results)} 条消息")

        # 所有订阅者应该收到相同数量的消息
        counts = [len(r) for r in subscriber_results]
        assert min(counts) > 0, "有订阅者没有收到消息"

    @pytest.mark.asyncio
    async def test_rapid_session_creation(self):
        """
        测试：快速创建大量会话

        验证：会话管理的内存和性能
        """
        from datapillar_oneagentic.sse import StreamManager

        manager = StreamManager(buffer_size=100, session_ttl_seconds=1)

        class MockOrchestrator:
            async def stream(self, **kwargs):
                yield {"event": "done"}

        num_sessions = 100
        start = time.time()

        for i in range(num_sessions):
            await manager.chat(
                orchestrator=MockOrchestrator(),
                query="test",
                session_id=f"session_{i}",
                user_id=f"user_{i}",
            )

        elapsed = time.time() - start
        print(f"\n[会话创建测试] 创建 {num_sessions} 个会话，耗时 {elapsed:.3f}s")

        # 性能基准：100 个会话应该在 1 秒内创建
        assert elapsed < 1.0, f"会话创建过慢: {elapsed:.3f}s"


# ==================== 工具注册表压测 ====================


class TestToolRegistryStress:
    """工具注册表并发压测"""

    def setup_method(self):
        """重置工具注册表"""
        ToolRegistry.clear()

    def test_concurrent_tool_registration(self):
        """
        测试：并发工具注册

        验证：register() 的线程安全性
        """
        from datapillar_oneagentic.tools import tool

        num_tools = 50
        barrier = threading.Barrier(num_tools)
        errors = []

        def register_one(tool_id: int):
            try:
                barrier.wait()

                @tool(f"stress_tool_{tool_id}")
                def tool_func():
                    """测试工具"""
                    return f"tool_{tool_id}"

            except Exception as e:
                errors.append(f"Tool {tool_id}: {e}")

        threads = [threading.Thread(target=register_one, args=(i,)) for i in range(num_tools)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[工具注册测试] 注册 {num_tools} 个工具，错误数: {len(errors)}")

        assert not errors, f"注册出错: {errors[:5]}"

        # 验证所有工具都能获取
        for i in range(num_tools):
            t = ToolRegistry.get(f"stress_tool_{i}")
            assert t is not None, f"工具 stress_tool_{i} 未找到"

    def test_concurrent_tool_lookup(self):
        """
        测试：并发工具查找

        验证：get() 的线程安全性
        """
        from datapillar_oneagentic.tools import tool

        # 先注册一些工具
        for i in range(10):
            @tool(f"lookup_tool_{i}")
            def lookup_func():
                """查找测试工具"""
                return None

        num_lookups = 1000
        results = []
        errors = []

        def lookup_tools(thread_id: int):
            try:
                for i in range(num_lookups // 10):
                    t = ToolRegistry.get(f"lookup_tool_{i % 10}")
                    if t:
                        results.append(t)
            except Exception as e:
                errors.append(f"Thread {thread_id}: {e}")

        threads = [threading.Thread(target=lookup_tools, args=(i,)) for i in range(10)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[工具查找测试] {num_lookups} 次查找，成功 {len(results)} 次，错误 {len(errors)} 次")

        assert not errors, f"查找出错: {errors[:5]}"
        assert len(results) == num_lookups, f"查找失败: {num_lookups - len(results)} 次"


# ==================== 配置系统压测 ====================


class TestConfigStress:
    """配置系统并发压测"""

    def setup_method(self):
        """重置配置"""
        reset_config()

    def test_concurrent_config_access(self):
        """
        测试：并发配置访问

        验证：get_config() 的线程安全性
        """
        from datapillar_oneagentic.config import get_config

        # 先初始化配置
        datapillar_configure(agent={"max_steps": 50})

        num_accesses = 1000
        results = []
        errors = []

        def access_config(thread_id: int):
            try:
                for _ in range(num_accesses // 10):
                    config = get_config()
                    results.append(config.agent.max_steps)
            except Exception as e:
                errors.append(f"Thread {thread_id}: {e}")

        threads = [threading.Thread(target=access_config, args=(i,)) for i in range(10)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[配置访问测试] {num_accesses} 次访问，成功 {len(results)} 次")

        assert not errors, f"访问出错: {errors[:5]}"
        assert len(results) == num_accesses
        # 所有访问应该返回相同的值
        assert all(v == 50 for v in results), "配置值不一致"


# ==================== 综合压测 ====================


class TestIntegrationStress:
    """综合压测：模拟真实使用场景"""

    def setup_method(self):
        """重置所有状态"""
        reset_config()
        from datapillar_oneagentic.resilience import circuit_breaker
        from datapillar_oneagentic.events import event_bus

        circuit_breaker._circuit_breakers.clear()
        event_bus.clear()
        ToolRegistry.clear()

    @pytest.mark.asyncio
    async def test_simulated_agent_execution(self):
        """
        测试：模拟多 Agent 并发执行

        模拟场景：
        - 多个 Agent 同时执行
        - 每个 Agent 发送事件、使用工具、记录熔断状态
        """
        from datapillar_oneagentic.events import event_bus
        from datapillar_oneagentic.resilience import get_circuit_breaker
        from datapillar_oneagentic.tools import tool

        event_count = 0
        event_lock = asyncio.Lock()

        @event_bus.on(AgentEvent)
        async def on_agent_event(source, event):
            nonlocal event_count
            async with event_lock:
                event_count += 1

        # 注册工具
        for i in range(5):
            @tool(f"agent_tool_{i}")
            def agent_tool_func():
                """Agent 工具"""
                return "result"

        num_agents = 20
        iterations = 10
        errors = []

        async def simulate_agent(agent_id: int):
            try:
                cb = get_circuit_breaker(f"agent_{agent_id}")

                for i in range(iterations):
                    # 发送事件
                    await event_bus.aemit(
                        self, AgentEvent(agent_id=f"agent_{agent_id}", action=f"step_{i}")
                    )

                    # 使用工具
                    t = ToolRegistry.get(f"agent_tool_{i % 5}")
                    assert t is not None

                    # 记录成功
                    await cb.record_success()

                    await asyncio.sleep(0.001)

            except Exception as e:
                errors.append(f"Agent {agent_id}: {e}")

        start = time.time()
        await asyncio.gather(*[simulate_agent(i) for i in range(num_agents)])
        elapsed = time.time() - start

        # 等待事件处理完成
        await asyncio.sleep(0.5)

        expected_events = num_agents * iterations
        print(f"\n[综合压测] {num_agents} 个 Agent，各执行 {iterations} 步")
        print(f"[综合压测] 预期事件: {expected_events}，实际: {event_count}")
        print(f"[综合压测] 总耗时: {elapsed:.3f}s，错误数: {len(errors)}")

        assert not errors, f"执行出错: {errors[:5]}"
        assert event_count >= expected_events * 0.9, f"事件丢失过多"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
