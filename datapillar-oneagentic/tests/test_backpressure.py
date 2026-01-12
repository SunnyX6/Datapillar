"""
背压/积压测试

测试场景：模拟 LLM 响应慢时，大量请求积压的情况

验证：
1. 并发请求是否正确隔离
2. 资源消耗是否可控
3. 是否需要限流机制
"""

import asyncio
import time
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.providers.llm.model_manager import model_manager


class TestBackpressure:
    """背压/积压测试"""

    def setup_method(self):
        """重置状态"""
        reset_config()
        model_manager.clear()

        from datapillar_oneagentic.providers.llm.rate_limiter import RateLimitManager

        RateLimitManager._reset_instance()

        # 注册测试模型
        model_manager.register_model(
            provider="openai",
            model_name="gpt-4",
            api_key="test-key",
            is_default=True,
            model_id="test",
        )
        datapillar_configure(cache={"enabled": False})

    @pytest.mark.asyncio
    async def test_slow_llm_concurrent_calls(self):
        """
        测试：慢 LLM 下的并发调用

        模拟 GLM 等慢模型（响应时间 2 秒）
        验证：限流机制是否正确控制并发数
        """
        import datapillar_oneagentic.providers.llm.client as client_module
        from datapillar_oneagentic.providers.llm.rate_limiter import RateLimitManager

        # 配置限流：最多 10 个并发
        RateLimitManager._reset_instance()
        datapillar_configure(
            cache={"enabled": False},
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 6000, "max_concurrent": 10},
                }
            },
        )

        # 统计
        active_calls = 0
        max_active_calls = 0
        call_lock = asyncio.Lock()

        async def slow_llm_invoke(*args, **kwargs):
            nonlocal active_calls, max_active_calls
            async with call_lock:
                active_calls += 1
                max_active_calls = max(max_active_calls, active_calls)

            # 模拟慢 LLM（2 秒响应）
            await asyncio.sleep(0.5)

            async with call_lock:
                active_calls -= 1

            return MagicMock(content="response")

        # Mock LLM
        mock_llm = MagicMock()
        mock_llm.bind.return_value = mock_llm
        mock_llm.ainvoke = slow_llm_invoke

        with patch.object(
            client_module.LLMFactory, "create_chat_model", return_value=mock_llm
        ):
            client_module._llm_cache.clear()

            num_concurrent = 50  # 模拟 50 个并发请求

            async def make_llm_call(call_id: int):
                llm = client_module.call_llm(model_id="test")
                # 通过 ResilientChatModel.ainvoke 调用，会自动应用限流
                await llm.ainvoke([])
                return call_id

            start = time.time()
            results = await asyncio.gather(*[make_llm_call(i) for i in range(num_concurrent)])
            elapsed = time.time() - start

        print(f"\n[慢 LLM 并发测试 - 有限流]")
        print(f"  并发请求数: {num_concurrent}")
        print(f"  限制并发数: 10")
        print(f"  最大同时活跃调用: {max_active_calls}")
        print(f"  总耗时: {elapsed:.2f}s")
        print(f"  完成请求数: {len(results)}")

        # 验证
        assert len(results) == num_concurrent, "有请求丢失"

        # 限流后，最大并发应该 <= 10
        assert max_active_calls <= 10, f"并发数超限: {max_active_calls} > 10"
        print(f"\n  ✓ 限流生效：并发数被控制在 {max_active_calls} 个")

    @pytest.mark.asyncio
    async def test_session_isolation_under_load(self):
        """
        测试：高负载下的会话隔离

        验证：不同 session_id 的请求不会混乱
        """
        from datapillar_oneagentic.sse import StreamManager

        manager = StreamManager(buffer_size=100)

        # 模拟 Orchestrator
        class MockOrchestrator:
            def __init__(self, session_id: str, delay: float):
                self.session_id = session_id
                self.delay = delay

            async def stream(self, **kwargs):
                actual_session = kwargs.get("session_id")
                # 验证 session_id 一致性
                assert actual_session == self.session_id, f"Session 混乱: 期望 {self.session_id}，实际 {actual_session}"

                for i in range(5):
                    await asyncio.sleep(self.delay)
                    yield {"event": "msg", "session": self.session_id, "seq": i}

        # 并发启动多个会话
        num_sessions = 20
        delays = [0.1 + i * 0.01 for i in range(num_sessions)]  # 不同延迟

        async def start_session(session_num: int):
            session_id = f"session_{session_num}"
            user_id = f"user_{session_num}"

            await manager.chat(
                orchestrator=MockOrchestrator(session_id, delays[session_num]),
                query=f"query_{session_num}",
                session_id=session_id,
                user_id=user_id,
            )
            return session_id

        # 并发启动所有会话
        sessions = await asyncio.gather(*[start_session(i) for i in range(num_sessions)])

        print(f"\n[会话隔离测试]")
        print(f"  并发会话数: {num_sessions}")
        print(f"  启动的会话: {len(sessions)}")

        # 等待所有会话完成
        await asyncio.sleep(max(delays) * 6)

        # 验证没有异常
        assert len(sessions) == num_sessions, "有会话启动失败"

    @pytest.mark.asyncio
    async def test_memory_growth_under_backlog(self):
        """
        测试：积压时的内存增长

        验证：大量等待中的请求是否导致内存问题
        """
        import tracemalloc

        tracemalloc.start()

        # 获取初始内存
        current, peak = tracemalloc.get_traced_memory()
        initial_memory = current

        # 模拟大量协程积压
        num_tasks = 200
        slow_delay = 0.5  # 500ms 延迟

        async def slow_task(task_id: int):
            # 模拟每个任务持有一些数据
            data = {"task_id": task_id, "payload": "x" * 1000}
            await asyncio.sleep(slow_delay)
            return data

        start = time.time()
        results = await asyncio.gather(*[slow_task(i) for i in range(num_tasks)])
        elapsed = time.time() - start

        # 获取峰值内存
        current, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        memory_growth_mb = (peak - initial_memory) / 1024 / 1024

        print(f"\n[内存增长测试]")
        print(f"  并发任务数: {num_tasks}")
        print(f"  初始内存: {initial_memory / 1024 / 1024:.2f} MB")
        print(f"  峰值内存: {peak / 1024 / 1024:.2f} MB")
        print(f"  内存增长: {memory_growth_mb:.2f} MB")
        print(f"  总耗时: {elapsed:.2f}s")

        # 验证
        assert len(results) == num_tasks

        # 警告：如果内存增长过大
        if memory_growth_mb > 50:
            print(f"\n  ⚠️ 警告: 内存增长 {memory_growth_mb:.2f} MB，可能需要背压机制")


class TestRecommendations:
    """建议的解决方案验证"""

    @pytest.mark.asyncio
    async def test_semaphore_limits_concurrency(self):
        """
        验证：Semaphore 可以限制并发数

        这是推荐的解决方案
        """
        max_concurrent = 10  # 最大并发数
        semaphore = asyncio.Semaphore(max_concurrent)

        active_count = 0
        max_active = 0
        lock = asyncio.Lock()

        async def limited_task(task_id: int):
            nonlocal active_count, max_active
            async with semaphore:
                async with lock:
                    active_count += 1
                    max_active = max(max_active, active_count)

                # 模拟慢操作
                await asyncio.sleep(0.1)

                async with lock:
                    active_count -= 1

            return task_id

        num_tasks = 100
        results = await asyncio.gather(*[limited_task(i) for i in range(num_tasks)])

        print(f"\n[Semaphore 限流测试]")
        print(f"  总任务数: {num_tasks}")
        print(f"  限制并发: {max_concurrent}")
        print(f"  实际最大并发: {max_active}")

        assert len(results) == num_tasks
        assert max_active <= max_concurrent, f"并发数超限: {max_active} > {max_concurrent}"

        print(f"\n  ✓ Semaphore 有效限制了并发数")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
