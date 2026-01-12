"""
弹性机制单元测试

测试模块：
- datapillar_oneagentic.resilience.errors
- datapillar_oneagentic.resilience.retry
- datapillar_oneagentic.resilience.circuit_breaker
"""

import asyncio
import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config


class TestErrorClassifier:
    """ErrorClassifier 错误分类器测试"""

    def test_classify_timeout_error(self):
        """测试 TimeoutError 分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = TimeoutError("Connection timed out")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TIMEOUT
        assert action == RecoveryAction.RETRY

    def test_classify_connection_error(self):
        """测试 ConnectionError 分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = ConnectionError("Connection refused")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TIMEOUT
        assert action == RecoveryAction.RETRY

    def test_classify_rate_limit_error(self):
        """测试 429 限流错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Rate limit exceeded (429)")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TRANSIENT
        assert action == RecoveryAction.RETRY

    def test_classify_too_many_requests(self):
        """测试 too many requests 错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Too many requests")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TRANSIENT
        assert action == RecoveryAction.RETRY

    def test_classify_service_unavailable(self):
        """测试 503 服务不可用错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Service unavailable (503)")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TRANSIENT
        assert action == RecoveryAction.RETRY

    def test_classify_invalid_api_key(self):
        """测试 API Key 无效错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Invalid API key")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.AUTH_FAILURE
        assert action == RecoveryAction.FAIL_FAST

    def test_classify_unauthorized(self):
        """测试 401 未授权错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Unauthorized (401)")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.AUTH_FAILURE
        assert action == RecoveryAction.FAIL_FAST

    def test_classify_bad_request(self):
        """测试 400 错误请求分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Bad request (400)")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.AUTH_FAILURE
        assert action == RecoveryAction.FAIL_FAST

    def test_classify_not_found(self):
        """测试 404 资源不存在错误分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Resource not found (404)")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.AUTH_FAILURE
        assert action == RecoveryAction.FAIL_FAST

    def test_classify_retryable_error(self):
        """测试 RetryableError 分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
            RetryableError,
        )

        error = RetryableError("Temporary failure")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.TRANSIENT
        assert action == RecoveryAction.RETRY

    def test_classify_non_retryable_error(self):
        """测试 NonRetryableError 分类"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
            NonRetryableError,
        )

        error = NonRetryableError("Permanent failure")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.INVALID_INPUT
        assert action == RecoveryAction.FAIL_FAST

    def test_classify_unknown_error(self):
        """测试未知错误分类（默认不重试）"""
        from datapillar_oneagentic.resilience.errors import (
            ErrorCategory,
            ErrorClassifier,
            RecoveryAction,
        )

        error = Exception("Some unknown error")
        category, action = ErrorClassifier.classify(error)

        assert category == ErrorCategory.INTERNAL
        assert action == RecoveryAction.FAIL_FAST

    def test_is_retryable(self):
        """测试 is_retryable() 便捷方法"""
        from datapillar_oneagentic.resilience.errors import ErrorClassifier

        assert ErrorClassifier.is_retryable(TimeoutError("timeout")) is True
        assert ErrorClassifier.is_retryable(ConnectionError("refused")) is True
        assert ErrorClassifier.is_retryable(Exception("rate limit")) is True
        assert ErrorClassifier.is_retryable(Exception("invalid api key")) is False
        assert ErrorClassifier.is_retryable(Exception("unknown")) is False


class TestWithRetry:
    """with_retry 重试装饰器测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            resilience={
                "max_retries": 3,
                "initial_delay_ms": 10,
                "max_delay_ms": 100,
                "jitter": False,
            }
        )
        yield
        reset_config()

    @pytest.mark.asyncio
    async def test_retry_success_first_attempt(self):
        """测试首次成功不重试"""
        from datapillar_oneagentic.resilience.retry import with_retry

        call_count = 0

        @with_retry()
        async def success_func():
            nonlocal call_count
            call_count += 1
            return "success"

        result = await success_func()

        assert result == "success"
        assert call_count == 1

    @pytest.mark.asyncio
    async def test_retry_success_after_failures(self):
        """测试失败后重试成功"""
        from datapillar_oneagentic.resilience.retry import with_retry

        call_count = 0

        @with_retry()
        async def flaky_func():
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                raise TimeoutError("timeout")
            return "success"

        result = await flaky_func()

        assert result == "success"
        assert call_count == 3

    @pytest.mark.asyncio
    async def test_retry_exhaust_retries(self):
        """测试重试耗尽后抛出异常"""
        from datapillar_oneagentic.resilience.retry import with_retry

        call_count = 0

        @with_retry(max_retries=2)
        async def always_fail():
            nonlocal call_count
            call_count += 1
            raise TimeoutError("always timeout")

        with pytest.raises(TimeoutError):
            await always_fail()

        assert call_count == 3

    @pytest.mark.asyncio
    async def test_retry_non_retryable_error(self):
        """测试不可重试错误直接失败"""
        from datapillar_oneagentic.resilience.retry import with_retry

        call_count = 0

        @with_retry()
        async def auth_fail():
            nonlocal call_count
            call_count += 1
            raise Exception("Invalid API key")

        with pytest.raises(Exception) as exc_info:
            await auth_fail()

        assert "Invalid API key" in str(exc_info.value)
        assert call_count == 1

    @pytest.mark.asyncio
    async def test_retry_callback(self):
        """测试重试回调"""
        from datapillar_oneagentic.resilience.retry import with_retry

        retry_attempts = []

        def on_retry(attempt, error):
            retry_attempts.append((attempt, str(error)))

        @with_retry(max_retries=2, on_retry=on_retry)
        async def flaky_func():
            if len(retry_attempts) < 2:
                raise TimeoutError("timeout")
            return "success"

        result = await flaky_func()

        assert result == "success"
        assert len(retry_attempts) == 2


class TestWithRetrySync:
    """with_retry_sync 同步重试装饰器测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            resilience={
                "max_retries": 3,
                "initial_delay_ms": 10,
                "max_delay_ms": 100,
                "jitter": False,
            }
        )
        yield
        reset_config()

    def test_sync_retry_success(self):
        """测试同步重试成功"""
        from datapillar_oneagentic.resilience.retry import with_retry_sync

        call_count = 0

        @with_retry_sync()
        def success_func():
            nonlocal call_count
            call_count += 1
            return "success"

        result = success_func()

        assert result == "success"
        assert call_count == 1

    def test_sync_retry_after_failures(self):
        """测试同步失败后重试成功"""
        from datapillar_oneagentic.resilience.retry import with_retry_sync

        call_count = 0

        @with_retry_sync()
        def flaky_func():
            nonlocal call_count
            call_count += 1
            if call_count < 2:
                raise ConnectionError("connection refused")
            return "success"

        result = flaky_func()

        assert result == "success"
        assert call_count == 2


class TestCircuitBreaker:
    """CircuitBreaker 熔断器测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            llm={
                "circuit_breaker": {
                    "failure_threshold": 3,
                    "recovery_seconds": 1,
                }
            }
        )
        yield
        reset_config()

    @pytest.mark.asyncio
    async def test_circuit_breaker_initial_state(self):
        """测试熔断器初始状态为 CLOSED"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")

        assert cb.state == CircuitState.CLOSED

    @pytest.mark.asyncio
    async def test_circuit_breaker_allows_request_when_closed(self):
        """测试 CLOSED 状态允许请求"""
        from datapillar_oneagentic.resilience.circuit_breaker import CircuitBreaker

        cb = CircuitBreaker("test")

        assert await cb.allow_request() is True

    @pytest.mark.asyncio
    async def test_circuit_breaker_opens_after_failures(self):
        """测试连续失败后熔断器打开"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")

        for _ in range(3):
            await cb.record_failure()

        assert cb.state == CircuitState.OPEN

    @pytest.mark.asyncio
    async def test_circuit_breaker_denies_request_when_open(self):
        """测试 OPEN 状态拒绝请求"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")

        for _ in range(3):
            await cb.record_failure()

        assert cb.state == CircuitState.OPEN
        assert await cb.allow_request() is False

    @pytest.mark.asyncio
    async def test_circuit_breaker_half_open_after_recovery_time(self):
        """测试恢复时间后进入 HALF_OPEN 状态"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")
        cb.recovery_timeout = 0.1

        for _ in range(3):
            await cb.record_failure()

        assert cb.state == CircuitState.OPEN

        await asyncio.sleep(0.15)

        await cb.allow_request()

        assert cb.state == CircuitState.HALF_OPEN

    @pytest.mark.asyncio
    async def test_circuit_breaker_closes_on_success_from_half_open(self):
        """测试 HALF_OPEN 状态成功后关闭"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")
        cb.recovery_timeout = 0.1

        for _ in range(3):
            await cb.record_failure()

        await asyncio.sleep(0.15)
        await cb.allow_request()

        assert cb.state == CircuitState.HALF_OPEN

        await cb.record_success()

        assert cb.state == CircuitState.CLOSED

    @pytest.mark.asyncio
    async def test_circuit_breaker_opens_on_failure_from_half_open(self):
        """测试 HALF_OPEN 状态失败后重新打开"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")
        cb.recovery_timeout = 0.1

        for _ in range(3):
            await cb.record_failure()

        await asyncio.sleep(0.15)
        await cb.allow_request()

        assert cb.state == CircuitState.HALF_OPEN

        await cb.record_failure()

        assert cb.state == CircuitState.OPEN

    @pytest.mark.asyncio
    async def test_circuit_breaker_reset(self):
        """测试熔断器重置"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreaker,
            CircuitState,
        )

        cb = CircuitBreaker("test")

        for _ in range(3):
            await cb.record_failure()

        assert cb.state == CircuitState.OPEN

        cb.reset()

        assert cb.state == CircuitState.CLOSED


class TestGetCircuitBreaker:
    """get_circuit_breaker() 全局熔断器管理测试"""

    def test_get_circuit_breaker_returns_same_instance(self):
        """测试相同名称返回同一个熔断器实例"""
        from datapillar_oneagentic.resilience.circuit_breaker import get_circuit_breaker

        cb1 = get_circuit_breaker("llm")
        cb2 = get_circuit_breaker("llm")

        assert cb1 is cb2

    def test_get_circuit_breaker_returns_different_instance_for_different_name(self):
        """测试不同名称返回不同熔断器实例"""
        from datapillar_oneagentic.resilience.circuit_breaker import get_circuit_breaker

        cb_llm = get_circuit_breaker("llm_test")
        cb_api = get_circuit_breaker("api_test")

        assert cb_llm is not cb_api


class TestCircuitBreakerError:
    """CircuitBreakerError 异常测试"""

    def test_circuit_breaker_error_message(self):
        """测试熔断错误消息"""
        from datapillar_oneagentic.resilience.circuit_breaker import CircuitBreakerError

        error = CircuitBreakerError("llm")

        assert error.name == "llm"
        assert "llm" in str(error)
        assert "熔断" in str(error)

    def test_circuit_breaker_error_custom_message(self):
        """测试熔断错误自定义消息"""
        from datapillar_oneagentic.resilience.circuit_breaker import CircuitBreakerError

        error = CircuitBreakerError("llm", "Custom error message")

        assert str(error) == "Custom error message"


class TestWithCircuitBreakerDecorator:
    """with_circuit_breaker 装饰器测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            llm={
                "circuit_breaker": {
                    "failure_threshold": 2,
                    "recovery_seconds": 60,
                }
            }
        )
        yield
        reset_config()

    @pytest.mark.asyncio
    async def test_decorator_success(self):
        """测试装饰器成功调用"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            get_circuit_breaker,
            with_circuit_breaker,
        )

        cb = get_circuit_breaker("decorator_test")
        cb.reset()

        @with_circuit_breaker("decorator_test")
        async def success_func():
            return "success"

        result = await success_func()

        assert result == "success"

    @pytest.mark.asyncio
    async def test_decorator_records_failure(self):
        """测试装饰器记录失败"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitState,
            get_circuit_breaker,
            with_circuit_breaker,
        )

        cb = get_circuit_breaker("decorator_fail_test")
        cb.reset()

        @with_circuit_breaker("decorator_fail_test")
        async def fail_func():
            raise Exception("failure")

        for _ in range(2):
            with pytest.raises(Exception):
                await fail_func()

        assert cb.state == CircuitState.OPEN

    @pytest.mark.asyncio
    async def test_decorator_raises_circuit_breaker_error_when_open(self):
        """测试熔断器打开时装饰器抛出 CircuitBreakerError"""
        from datapillar_oneagentic.resilience.circuit_breaker import (
            CircuitBreakerError,
            get_circuit_breaker,
            with_circuit_breaker,
        )

        cb = get_circuit_breaker("decorator_open_test")
        cb.reset()

        @with_circuit_breaker("decorator_open_test")
        async def func():
            raise Exception("failure")

        for _ in range(2):
            with pytest.raises(Exception):
                await func()

        with pytest.raises(CircuitBreakerError):
            await func()
