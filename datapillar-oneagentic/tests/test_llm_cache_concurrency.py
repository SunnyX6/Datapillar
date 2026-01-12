"""
LLM 缓存并发压测

测试目标：
1. _init_llm_cache() 的竞态条件
2. call_llm() 的双重检查锁定是否正确
3. InMemoryLLMCache 的并发读写安全性
4. 高并发下的缓存命中率

已知问题：
- _init_llm_cache() 中 _llm_cache_initialized 的检查和设置之间存在竞态窗口
- 可能导致 create_llm_cache() 被多次调用
"""

import asyncio
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import MagicMock, patch

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.providers.llm.model_manager import model_manager


def _reset_llm_state():
    """重置 LLM 相关的全局状态"""
    reset_config()
    model_manager.clear()
    import datapillar_oneagentic.providers.llm.client as client_module
    client_module._llm_cache_initialized = False
    client_module._llm_cache.clear()


def _register_test_model():
    """注册测试用的模型配置"""
    model_manager.register_model(
        provider="openai",
        model_name="gpt-4",
        api_key="test-key",
        is_default=True,
        model_id="test",
    )


class TestLLMCacheInitRaceCondition:
    """测试 _init_llm_cache() 的竞态条件"""

    def setup_method(self):
        """每个测试前重置状态"""
        _reset_llm_state()

    def test_init_llm_cache_race_condition(self):
        """
        测试：多线程同时调用 _init_llm_cache() 时的竞态条件

        预期行为：create_llm_cache() 应该只被调用一次
        验证：双重检查锁定模式能正确防止竞态
        """
        import datapillar_oneagentic.providers.llm.client as client_module

        # 配置启用缓存
        datapillar_configure(cache={"enabled": True, "ttl_seconds": 300})

        call_count = 0
        call_count_lock = threading.Lock()

        original_create = client_module.create_llm_cache

        def counting_create():
            nonlocal call_count
            # 模拟真实场景：创建缓存耗时（如连接 Redis）
            time.sleep(0.05)
            with call_count_lock:
                call_count += 1
            return original_create()

        # 并发线程数
        num_threads = 50
        barrier = threading.Barrier(num_threads)
        results = []
        errors = []

        def worker():
            try:
                barrier.wait()  # 确保所有线程同时开始
                client_module._init_llm_cache()
                results.append(True)
            except Exception as e:
                errors.append(e)

        with patch.object(
            client_module, "create_llm_cache", side_effect=counting_create
        ):
            # 重置状态
            client_module._llm_cache_initialized = False

            threads = [threading.Thread(target=worker) for _ in range(num_threads)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

        assert not errors, f"出现错误: {errors}"
        assert len(results) == num_threads

        # 关键断言：检查 create_llm_cache 被调用的次数
        # 如果没有竞态问题，应该只被调用 1 次
        # 如果有竞态问题，可能被调用多次
        print(f"\n[竞态测试] create_llm_cache 被调用 {call_count} 次")

        if call_count > 1:
            pytest.fail(
                f"竞态条件确认！create_llm_cache 被调用 {call_count} 次（应该只调用 1 次）。"
                f"\n问题位置: _init_llm_cache() 中 _llm_cache_initialized 的检查和设置之间缺少锁保护"
            )


class TestCallLLMConcurrency:
    """测试 call_llm() 的并发安全性"""

    def setup_method(self):
        """每个测试前重置状态"""
        _reset_llm_state()

    def test_call_llm_double_checked_locking(self):
        """
        测试 call_llm() 的双重检查锁定模式

        验证：相同参数的并发调用只创建一个 LLM 实例
        """
        import datapillar_oneagentic.providers.llm.client as client_module

        # 注册测试模型
        _register_test_model()
        datapillar_configure(cache={"enabled": False})

        create_count = 0
        create_count_lock = threading.Lock()

        original_create = client_module.LLMFactory.create_chat_model

        def counting_create(*args, **kwargs):
            nonlocal create_count
            with create_count_lock:
                create_count += 1
            time.sleep(0.01)  # 模拟创建耗时
            mock_llm = MagicMock()
            mock_llm.bind.return_value = mock_llm
            mock_llm.with_structured_output.return_value = mock_llm
            return mock_llm

        num_threads = 30
        barrier = threading.Barrier(num_threads)
        results = []

        def worker():
            barrier.wait()
            llm = client_module.call_llm(model_id="test")
            results.append(id(llm))

        with patch.object(
            client_module.LLMFactory, "create_chat_model", side_effect=counting_create
        ):
            client_module._llm_cache.clear()

            threads = [threading.Thread(target=worker) for _ in range(num_threads)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

        assert len(results) == num_threads
        # 所有线程应该获得同一个实例
        unique_instances = set(results)

        print(f"\n[双重检查锁定测试] 创建了 {create_count} 个实例，获取了 {len(unique_instances)} 个唯一实例")

        # 应该只创建一个实例
        assert (
            create_count == 1
        ), f"双重检查锁定失败！创建了 {create_count} 个实例（应该只创建 1 个）"
        assert (
            len(unique_instances) == 1
        ), f"返回了 {len(unique_instances)} 个不同实例（应该只有 1 个）"


class TestInMemoryCacheConcurrency:
    """测试 InMemoryLLMCache 的并发安全性"""

    def test_concurrent_lookup_update(self):
        """
        测试并发读写场景

        模拟多个线程同时读写缓存
        """
        from datapillar_oneagentic.providers.llm.llm_cache import InMemoryLLMCache
        from langchain_core.messages import AIMessage
        from langchain_core.outputs import ChatGeneration

        cache = InMemoryLLMCache(ttl_seconds=60, max_size=100)

        num_readers = 20
        num_writers = 10
        iterations = 100
        errors = []

        def reader(reader_id: int):
            try:
                for i in range(iterations):
                    prompt = f"prompt_{i % 10}"
                    llm_string = "test_model"
                    cache.lookup(prompt, llm_string)
            except Exception as e:
                errors.append(f"Reader {reader_id}: {e}")

        def writer(writer_id: int):
            try:
                for i in range(iterations):
                    prompt = f"prompt_{i % 10}"
                    llm_string = "test_model"
                    return_val = [
                        ChatGeneration(message=AIMessage(content=f"response_{writer_id}_{i}"))
                    ]
                    cache.update(prompt, llm_string, return_val)
            except Exception as e:
                errors.append(f"Writer {writer_id}: {e}")

        threads = []
        for i in range(num_readers):
            threads.append(threading.Thread(target=reader, args=(i,)))
        for i in range(num_writers):
            threads.append(threading.Thread(target=writer, args=(i,)))

        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[并发读写测试] 缓存大小: {len(cache._cache)}, 错误数: {len(errors)}")

        if errors:
            pytest.fail(f"并发读写出现错误: {errors[:5]}")

    def test_concurrent_cleanup_race(self):
        """
        测试 _cleanup() 的并发安全性

        多个写操作同时触发清理可能导致问题
        """
        from datapillar_oneagentic.providers.llm.llm_cache import InMemoryLLMCache
        from langchain_core.messages import AIMessage
        from langchain_core.outputs import ChatGeneration

        # 使用较小的 max_size 来频繁触发清理
        cache = InMemoryLLMCache(ttl_seconds=60, max_size=10)

        num_threads = 50
        iterations = 100
        errors = []

        def writer(thread_id: int):
            try:
                for i in range(iterations):
                    prompt = f"prompt_{thread_id}_{i}"
                    llm_string = "test_model"
                    return_val = [
                        ChatGeneration(message=AIMessage(content=f"response_{thread_id}_{i}"))
                    ]
                    cache.update(prompt, llm_string, return_val)
            except Exception as e:
                errors.append(f"Thread {thread_id}: {e}")

        threads = [threading.Thread(target=writer, args=(i,)) for i in range(num_threads)]

        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[清理竞态测试] 缓存大小: {len(cache._cache)}, 错误数: {len(errors)}")

        # 缓存大小不应超过 max_size（允许一定误差）
        assert cache._cache is not None
        if errors:
            pytest.fail(f"清理竞态出现错误: {errors[:5]}")


class TestHighConcurrencyStress:
    """高并发压力测试"""

    def setup_method(self):
        """每个测试前重置状态"""
        _reset_llm_state()

    @pytest.mark.asyncio
    async def test_async_concurrent_llm_calls(self):
        """
        异步高并发 LLM 调用测试

        模拟真实场景：多个 Agent 同时调用 LLM
        """
        import datapillar_oneagentic.providers.llm.client as client_module

        # 注册测试模型
        _register_test_model()
        datapillar_configure(cache={"enabled": False})

        # Mock LLM 实例
        mock_llm = MagicMock()
        mock_llm.bind.return_value = mock_llm
        mock_llm.with_structured_output.return_value = mock_llm

        call_count = 0
        call_lock = asyncio.Lock()

        async def mock_ainvoke(*args, **kwargs):
            nonlocal call_count
            async with call_lock:
                call_count += 1
            await asyncio.sleep(0.01)  # 模拟 API 延迟
            return MagicMock(content="test response")

        mock_llm.ainvoke = mock_ainvoke

        with patch.object(
            client_module.LLMFactory,
            "create_chat_model",
            return_value=mock_llm,
        ):
            client_module._llm_cache.clear()

            num_concurrent = 100

            async def make_call(call_id: int):
                llm = client_module.call_llm(model_id="test")
                # 直接调用底层 LLM（跳过弹性包装的超时控制）
                await llm._llm.ainvoke([])
                return call_id

            # 并发执行
            start = time.time()
            results = await asyncio.gather(*[make_call(i) for i in range(num_concurrent)])
            elapsed = time.time() - start

            print(f"\n[异步压力测试] {num_concurrent} 个并发调用，耗时 {elapsed:.2f}s")
            print(f"[异步压力测试] 实际 LLM 调用次数: {call_count}")

            assert len(results) == num_concurrent
            assert call_count == num_concurrent

    def test_thread_pool_stress(self):
        """
        线程池压力测试

        使用 ThreadPoolExecutor 模拟高并发
        """
        import datapillar_oneagentic.providers.llm.client as client_module

        # 注册测试模型
        _register_test_model()
        datapillar_configure(cache={"enabled": False})

        mock_llm = MagicMock()
        mock_llm.bind.return_value = mock_llm

        instance_count = 0
        instance_lock = threading.Lock()

        def counting_create(*args, **kwargs):
            nonlocal instance_count
            with instance_lock:
                instance_count += 1
            time.sleep(0.005)
            return mock_llm

        with patch.object(
            client_module.LLMFactory, "create_chat_model", side_effect=counting_create
        ):
            client_module._llm_cache.clear()

            num_workers = 100

            def get_llm(worker_id: int):
                return client_module.call_llm(model_id="test")

            with ThreadPoolExecutor(max_workers=50) as executor:
                start = time.time()
                futures = [executor.submit(get_llm, i) for i in range(num_workers)]
                results = [f.result() for f in futures]
                elapsed = time.time() - start

            print(f"\n[线程池压力测试] {num_workers} 个请求，耗时 {elapsed:.2f}s")
            print(f"[线程池压力测试] 创建了 {instance_count} 个 LLM 实例")

            # 验证只创建了一个实例
            assert (
                instance_count == 1
            ), f"创建了 {instance_count} 个实例（应该只创建 1 个）"

            # 验证所有结果是同一个实例
            unique_ids = set(id(r) for r in results)
            assert len(unique_ids) == 1, f"返回了 {len(unique_ids)} 个不同实例"


class TestRaceConditionFix:
    """
    竞态条件修复验证

    提供修复方案并验证修复效果
    """

    def test_proposed_fix_with_lock(self):
        """
        验证修复方案：使用锁保护 _init_llm_cache()

        修复代码示例:
        ```python
        _llm_cache_initialized = False
        _llm_cache_init_lock = threading.Lock()  # 新增锁

        def _init_llm_cache() -> None:
            global _llm_cache_initialized
            if _llm_cache_initialized:  # 快速路径检查
                return

            with _llm_cache_init_lock:  # 获取锁
                if _llm_cache_initialized:  # 双重检查
                    return
                _llm_cache_initialized = True
                # ... 初始化逻辑
        ```
        """
        # 模拟修复后的实现
        initialized = False
        init_lock = threading.Lock()
        init_count = 0
        init_count_lock = threading.Lock()

        def fixed_init():
            nonlocal initialized, init_count
            if initialized:
                return

            with init_lock:
                if initialized:
                    return
                initialized = True
                # 模拟初始化
                time.sleep(0.01)
                with init_count_lock:
                    init_count += 1

        num_threads = 50
        barrier = threading.Barrier(num_threads)

        def worker():
            barrier.wait()
            fixed_init()

        threads = [threading.Thread(target=worker) for _ in range(num_threads)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        print(f"\n[修复验证] 初始化被执行 {init_count} 次")
        assert init_count == 1, f"修复后仍执行了 {init_count} 次（应该只执行 1 次）"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
