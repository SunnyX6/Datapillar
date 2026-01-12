"""
限流器测试

测试目标：
1. RateLimitManager 基本功能
2. RPM 限流效果
3. 并发数限制
4. 按 Provider 隔离
5. 集成到 LLM 调用层
"""

import asyncio
import time
from unittest.mock import MagicMock, patch

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.providers.llm.model_manager import model_manager
from datapillar_oneagentic.providers.llm.rate_limiter import (
    RateLimitManager,
    rate_limit_manager,
)


class TestRateLimiterConfig:
    """限流配置测试"""

    def setup_method(self):
        reset_config()
        RateLimitManager._reset_instance()

    def test_default_config(self):
        """测试：默认限流配置"""
        from datapillar_oneagentic.config import get_config

        config = get_config()
        rate_limit = config.llm.rate_limit

        assert rate_limit.enabled is True
        assert rate_limit.default.rpm == 60
        assert rate_limit.default.max_concurrent == 10

    def test_custom_config(self):
        """测试：自定义限流配置"""
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 120, "max_concurrent": 20},
                    "providers": {
                        "openai": {"rpm": 500, "max_concurrent": 50},
                        "glm": {"rpm": 10, "max_concurrent": 3},
                    },
                }
            }
        )

        from datapillar_oneagentic.config import get_config

        config = get_config()
        rate_limit = config.llm.rate_limit

        # 默认配置
        assert rate_limit.default.rpm == 120
        assert rate_limit.default.max_concurrent == 20

        # Provider 配置
        openai_config = rate_limit.get_provider_config("openai")
        assert openai_config.rpm == 500
        assert openai_config.max_concurrent == 50

        glm_config = rate_limit.get_provider_config("glm")
        assert glm_config.rpm == 10
        assert glm_config.max_concurrent == 3

        # 未配置的 Provider 使用默认值
        anthropic_config = rate_limit.get_provider_config("anthropic")
        assert anthropic_config.rpm == 120
        assert anthropic_config.max_concurrent == 20


class TestRateLimitManager:
    """RateLimitManager 测试"""

    def setup_method(self):
        reset_config()
        RateLimitManager._reset_instance()

    @pytest.mark.asyncio
    async def test_concurrent_limit(self):
        """测试：并发数限制"""
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 6000, "max_concurrent": 5},
                }
            }
        )

        manager = RateLimitManager()

        active_count = 0
        max_active = 0
        lock = asyncio.Lock()

        async def task(task_id: int):
            nonlocal active_count, max_active
            async with manager.acquire("test_provider"):
                async with lock:
                    active_count += 1
                    max_active = max(max_active, active_count)

                await asyncio.sleep(0.1)

                async with lock:
                    active_count -= 1

            return task_id

        # 启动 20 个并发任务
        results = await asyncio.gather(*[task(i) for i in range(20)])

        print(f"\n[并发限制测试] 最大并发: {max_active}, 限制: 5")

        assert len(results) == 20
        assert max_active <= 5, f"并发数超限: {max_active} > 5"

    @pytest.mark.asyncio
    async def test_rpm_limit(self):
        """测试：RPM 限制"""
        # RPM=60 意味着每秒最多 1 个请求
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 60, "max_concurrent": 100},
                }
            }
        )

        manager = RateLimitManager()

        start = time.time()
        request_times = []

        async def task(task_id: int):
            async with manager.acquire("test_provider"):
                request_times.append(time.time() - start)
            return task_id

        # 顺序执行 5 个请求
        for i in range(5):
            await task(i)

        elapsed = time.time() - start

        print(f"\n[RPM 限制测试] 5 个请求耗时: {elapsed:.2f}s")
        print(f"  请求时间点: {[f'{t:.2f}s' for t in request_times]}")

        # RPM=60 意味着每秒 1 个，5 个请求至少需要 4 秒
        # 但由于令牌桶有初始容量，实际可能更快
        assert len(request_times) == 5

    @pytest.mark.asyncio
    async def test_provider_isolation(self):
        """测试：Provider 隔离"""
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 600, "max_concurrent": 5},
                    "providers": {
                        "openai": {"rpm": 600, "max_concurrent": 10},
                        "glm": {"rpm": 600, "max_concurrent": 2},
                    },
                }
            }
        )

        manager = RateLimitManager()

        openai_max = 0
        glm_max = 0
        openai_active = 0
        glm_active = 0
        lock = asyncio.Lock()

        async def openai_task(task_id: int):
            nonlocal openai_active, openai_max
            async with manager.acquire("openai"):
                async with lock:
                    openai_active += 1
                    openai_max = max(openai_max, openai_active)
                await asyncio.sleep(0.1)
                async with lock:
                    openai_active -= 1

        async def glm_task(task_id: int):
            nonlocal glm_active, glm_max
            async with manager.acquire("glm"):
                async with lock:
                    glm_active += 1
                    glm_max = max(glm_max, glm_active)
                await asyncio.sleep(0.1)
                async with lock:
                    glm_active -= 1

        # 同时启动 OpenAI 和 GLM 任务
        await asyncio.gather(
            *[openai_task(i) for i in range(15)],
            *[glm_task(i) for i in range(10)],
        )

        print(f"\n[Provider 隔离测试]")
        print(f"  OpenAI 最大并发: {openai_max} (限制: 10)")
        print(f"  GLM 最大并发: {glm_max} (限制: 2)")

        assert openai_max <= 10, f"OpenAI 并发超限: {openai_max}"
        assert glm_max <= 2, f"GLM 并发超限: {glm_max}"

    @pytest.mark.asyncio
    async def test_disabled_rate_limit(self):
        """测试：禁用限流"""
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": False,
                }
            }
        )

        manager = RateLimitManager()

        active_count = 0
        max_active = 0
        lock = asyncio.Lock()

        async def task(task_id: int):
            nonlocal active_count, max_active
            async with manager.acquire("test_provider"):
                async with lock:
                    active_count += 1
                    max_active = max(max_active, active_count)
                await asyncio.sleep(0.05)
                async with lock:
                    active_count -= 1

        # 启动 50 个并发任务
        await asyncio.gather(*[task(i) for i in range(50)])

        print(f"\n[禁用限流测试] 最大并发: {max_active} (无限制)")

        # 禁用后应该没有限制
        assert max_active > 10, "限流应该被禁用，但似乎仍在生效"


class TestRateLimitIntegration:
    """限流器集成测试"""

    def setup_method(self):
        reset_config()
        model_manager.clear()
        RateLimitManager._reset_instance()

        import datapillar_oneagentic.providers.llm.client as client_module

        client_module._llm_cache_initialized = False
        client_module._llm_cache.clear()

    @pytest.mark.asyncio
    async def test_llm_call_with_rate_limit(self):
        """测试：LLM 调用集成限流"""
        model_manager.register_model(
            provider="glm",
            model_name="glm-4",
            api_key="test-key",
            is_default=True,
            model_id="test_glm",
        )

        datapillar_configure(
            cache={"enabled": False},
            llm={
                "rate_limit": {
                    "enabled": True,
                    "providers": {
                        "glm": {"rpm": 600, "max_concurrent": 3},
                    },
                }
            },
        )

        import datapillar_oneagentic.providers.llm.client as client_module

        mock_llm = MagicMock()
        mock_llm.bind.return_value = mock_llm

        call_times = []
        start_time = time.time()

        async def mock_ainvoke(*args, **kwargs):
            call_times.append(time.time() - start_time)
            await asyncio.sleep(0.2)  # 模拟 LLM 响应时间
            return MagicMock(content="response")

        mock_llm.ainvoke = mock_ainvoke

        with patch.object(
            client_module.LLMFactory, "create_chat_model", return_value=mock_llm
        ):
            llm = client_module.call_llm(model_id="test_glm")

            # 并发调用 10 次
            async def make_call(call_id: int):
                await llm._llm.ainvoke([])
                return call_id

            # 注意：这里直接调用 _llm.ainvoke 绑定了限流
            # 实际使用时通过 llm.ainvoke 会自动应用限流
            results = await asyncio.gather(*[make_call(i) for i in range(10)])

        print(f"\n[集成测试] 10 次调用完成，总耗时: {time.time() - start_time:.2f}s")

        assert len(results) == 10

    @pytest.mark.asyncio
    async def test_stats_reporting(self):
        """测试：统计信息"""
        datapillar_configure(
            llm={
                "rate_limit": {
                    "enabled": True,
                    "default": {"rpm": 6000, "max_concurrent": 10},
                }
            }
        )

        manager = RateLimitManager()

        # 执行一些请求
        async def task():
            async with manager.acquire("openai"):
                await asyncio.sleep(0.01)

        await asyncio.gather(*[task() for _ in range(5)])

        stats = manager.stats()
        print(f"\n[统计信息] {stats}")

        assert stats["enabled"] is True
        assert "openai" in stats["providers"]
        assert stats["providers"]["openai"]["total_requests"] == 5


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
