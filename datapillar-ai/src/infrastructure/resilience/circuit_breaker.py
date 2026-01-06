"""
熔断器

防止级联故障，当服务连续失败时快速失败。

状态机：
CLOSED → (连续失败达阈值) → OPEN → (等待恢复时间) → HALF_OPEN → (探测成功) → CLOSED
                                                              ↓
                                                     (探测失败) → OPEN
"""

import asyncio
import logging
import time
from collections.abc import Awaitable, Callable
from enum import Enum
from functools import wraps
from typing import ParamSpec, TypeVar

from src.infrastructure.resilience.config import get_resilience_config

logger = logging.getLogger(__name__)

P = ParamSpec("P")
T = TypeVar("T")


class CircuitState(str, Enum):
    """熔断器状态"""

    CLOSED = "closed"  # 正常状态
    OPEN = "open"  # 熔断状态（拒绝所有请求）
    HALF_OPEN = "half_open"  # 半开状态（允许探测请求）


class CircuitBreakerError(Exception):
    """熔断器打开时抛出"""

    def __init__(self, name: str, message: str | None = None):
        self.name = name
        super().__init__(message or f"服务 {name} 熔断中，请稍后重试")


class CircuitBreaker:
    """
    熔断器

    使用统一配置，按名称隔离不同服务的熔断状态。
    """

    def __init__(self, name: str):
        self.name = name
        config = get_resilience_config()

        self.failure_threshold = config.circuit_failure_threshold
        self.recovery_timeout = config.circuit_recovery_timeout_seconds

        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._last_failure_time: float | None = None
        self._lock = asyncio.Lock()

    @property
    def state(self) -> CircuitState:
        return self._state

    async def _check_state_transition(self) -> None:
        """检查是否需要状态转换"""
        if self._state == CircuitState.OPEN and self._last_failure_time:
            elapsed = time.time() - self._last_failure_time
            if elapsed >= self.recovery_timeout:
                self._state = CircuitState.HALF_OPEN
                logger.info(f"[CircuitBreaker:{self.name}] OPEN → HALF_OPEN")

    async def allow_request(self) -> bool:
        """判断是否允许请求通过"""
        async with self._lock:
            await self._check_state_transition()

            if self._state == CircuitState.CLOSED:
                return True

            # HALF_OPEN 也允许探测请求通过
            return self._state != CircuitState.OPEN

    async def record_success(self) -> None:
        """记录成功"""
        async with self._lock:
            if self._state == CircuitState.HALF_OPEN:
                self._state = CircuitState.CLOSED
                logger.info(f"[CircuitBreaker:{self.name}] HALF_OPEN → CLOSED (恢复)")
            self._failure_count = 0

    async def record_failure(self) -> None:
        """记录失败"""
        async with self._lock:
            self._failure_count += 1
            self._last_failure_time = time.time()

            if self._state == CircuitState.HALF_OPEN:
                self._state = CircuitState.OPEN
                logger.warning(f"[CircuitBreaker:{self.name}] HALF_OPEN → OPEN (探测失败)")
            elif self._failure_count >= self.failure_threshold:
                self._state = CircuitState.OPEN
                logger.warning(
                    f"[CircuitBreaker:{self.name}] CLOSED → OPEN "
                    f"(连续失败 {self._failure_count} 次)"
                )

    def reset(self) -> None:
        """重置熔断器（测试用）"""
        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._last_failure_time = None


# 全局熔断器注册表
_circuit_breakers: dict[str, CircuitBreaker] = {}
_registry_lock = asyncio.Lock()


def get_circuit_breaker(name: str) -> CircuitBreaker:
    """获取或创建熔断器（按名称隔离）"""
    if name not in _circuit_breakers:
        _circuit_breakers[name] = CircuitBreaker(name)
    return _circuit_breakers[name]


def with_circuit_breaker(name: str):
    """
    熔断器装饰器

    使用：
        @with_circuit_breaker("llm")
        async def call_llm(...):
            ...
    """

    def decorator(func: Callable[P, Awaitable[T]]) -> Callable[P, Awaitable[T]]:
        @wraps(func)
        async def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            cb = get_circuit_breaker(name)

            if not await cb.allow_request():
                raise CircuitBreakerError(name)

            try:
                result = await func(*args, **kwargs)
                await cb.record_success()
                return result
            except CircuitBreakerError:
                # 熔断错误直接抛出，不记录
                raise
            except Exception:
                await cb.record_failure()
                raise

        return wrapper

    return decorator
