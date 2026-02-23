# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""
Circuit breaker.

Prevents cascading failures by failing fast when a service repeatedly fails.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Awaitable, Callable
from enum import Enum
from functools import wraps
from typing import TYPE_CHECKING, ParamSpec, TypeVar

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.config import CircuitBreakerConfig

logger = logging.getLogger(__name__)

P = ParamSpec("P")
T = TypeVar("T")


class CircuitState(str, Enum):
    """Circuit breaker state."""

    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"


class CircuitBreakerError(Exception):
    """Raised when the circuit breaker is open."""

    def __init__(self, name: str, message: str | None = None):
        self.name = name
        super().__init__(message or f"Service {name} is circuit open; try again later")


class CircuitBreaker:
    """Circuit breaker."""

    def __init__(self, name: str, config: "CircuitBreakerConfig"):
        self.name = name
        self.failure_threshold = config.failure_threshold
        self.recovery_timeout = config.recovery_seconds

        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._last_failure_time: float | None = None
        self._lock = asyncio.Lock()

    @property
    def state(self) -> CircuitState:
        return self._state

    async def _check_state_transition(self) -> None:
        if self._state == CircuitState.OPEN and self._last_failure_time:
            elapsed = time.time() - self._last_failure_time
            if elapsed >= self.recovery_timeout:
                self._state = CircuitState.HALF_OPEN
                logger.info(f"[CircuitBreaker:{self.name}] OPEN -> HALF_OPEN")

    async def allow_request(self) -> bool:
        async with self._lock:
            await self._check_state_transition()
            if self._state == CircuitState.CLOSED:
                return True
            return self._state != CircuitState.OPEN

    async def record_success(self) -> None:
        async with self._lock:
            if self._state == CircuitState.HALF_OPEN:
                self._state = CircuitState.CLOSED
                logger.info(f"[CircuitBreaker:{self.name}] HALF_OPEN -> CLOSED (recovered)")
            self._failure_count = 0

    async def record_failure(self) -> None:
        async with self._lock:
            self._failure_count += 1
            self._last_failure_time = time.time()

            if self._state == CircuitState.HALF_OPEN:
                self._state = CircuitState.OPEN
                logger.warning(f"[CircuitBreaker:{self.name}] HALF_OPEN -> OPEN (probe failed)")
            elif self._failure_count >= self.failure_threshold:
                self._state = CircuitState.OPEN
                logger.warning(
                    f"[CircuitBreaker:{self.name}] CLOSED -> OPEN "
                    f"(consecutive failures: {self._failure_count})"
                )

    def reset(self) -> None:
        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._last_failure_time = None


class CircuitBreakerRegistry:
    """Circuit breaker registry (team-scoped)."""

    def __init__(self, config: "CircuitBreakerConfig") -> None:
        self._config = config
        self._circuit_breakers: dict[str, CircuitBreaker] = {}

    def get(self, name: str) -> CircuitBreaker:
        if name in self._circuit_breakers:
            return self._circuit_breakers[name]
        breaker = CircuitBreaker(name, self._config)
        self._circuit_breakers[name] = breaker
        return breaker

    def reset(self) -> None:
        self._circuit_breakers.clear()


def with_circuit_breaker(name: str, registry: CircuitBreakerRegistry):
    """Circuit breaker decorator."""

    def decorator(func: Callable[P, Awaitable[T]]) -> Callable[P, Awaitable[T]]:
        @wraps(func)
        async def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            cb = registry.get(name)

            if not await cb.allow_request():
                raise CircuitBreakerError(name)

            try:
                result = await func(*args, **kwargs)
                await cb.record_success()
                return result
            except CircuitBreakerError:
                raise
            except Exception:
                await cb.record_failure()
                raise

        return wrapper

    return decorator
