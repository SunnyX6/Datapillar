"""Circuit breaker state machine tests.

Core transitions:
1. CLOSED -> OPEN (failures reach threshold)
2. OPEN -> HALF_OPEN (after recovery time)
3. HALF_OPEN -> CLOSED (probe success)
4. HALF_OPEN -> OPEN (probe failure)
"""

from __future__ import annotations

import asyncio
import time
import pytest

from datapillar_oneagentic.exception import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitBreakerRegistry,
    CircuitState,
    with_circuit_breaker,
)
from datapillar_oneagentic.providers.llm.config import CircuitBreakerConfig


@pytest.fixture
def circuit_breaker():
    """Create a test circuit breaker."""
    config = CircuitBreakerConfig(failure_threshold=3, recovery_seconds=1)
    cb = CircuitBreaker("test", config)
    yield cb


@pytest.mark.asyncio
async def test_start_closed(circuit_breaker) -> None:
    """Initial state should be CLOSED."""
    assert circuit_breaker.state == CircuitState.CLOSED


@pytest.mark.asyncio
async def test_circuit_breaker(circuit_breaker) -> None:
    """CLOSED should allow requests."""
    assert await circuit_breaker.allow_request() is True


@pytest.mark.asyncio
async def test_open_after(
    circuit_breaker,
) -> None:
    """Should transition to OPEN after failures reach threshold."""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN


@pytest.mark.asyncio
async def test_open_state(circuit_breaker) -> None:
    """OPEN should reject requests."""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert await circuit_breaker.allow_request() is False


@pytest.mark.asyncio
async def test_open_after2(
    circuit_breaker,
) -> None:
    """Should transition to HALF_OPEN after recovery time."""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN

    # Wait for recovery time.
    await asyncio.sleep(1.1)

    # Trigger state check.
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN


@pytest.mark.asyncio
async def test_success_half(
    circuit_breaker,
) -> None:
    """HALF_OPEN should transition to CLOSED after success."""
    # Trigger circuit open.
    for _ in range(3):
        await circuit_breaker.record_failure()

    # Wait to enter HALF_OPEN.
    await asyncio.sleep(1.1)
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN

    # Record success.
    await circuit_breaker.record_success()

    assert circuit_breaker.state == CircuitState.CLOSED


@pytest.mark.asyncio
async def test_open_failure(
    circuit_breaker,
) -> None:
    """HALF_OPEN should transition back to OPEN after failure."""
    # Trigger circuit open.
    for _ in range(3):
        await circuit_breaker.record_failure()

    # Wait to enter HALF_OPEN.
    await asyncio.sleep(1.1)
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN

    # Record failure.
    await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN


@pytest.mark.asyncio
async def test_reset_failure(circuit_breaker) -> None:
    """Success should reset failure count."""
    await circuit_breaker.record_failure()
    await circuit_breaker.record_failure()
    assert circuit_breaker._failure_count == 2

    await circuit_breaker.record_success()
    assert circuit_breaker._failure_count == 0


@pytest.mark.asyncio
async def test_raise_error() -> None:
    """Decorator should raise CircuitBreakerError when OPEN."""

    config = CircuitBreakerConfig(failure_threshold=3, recovery_seconds=1)
    registry = CircuitBreakerRegistry(config)
    cb = registry.get("test_decorator")
    cb.reset()

    @with_circuit_breaker("test_decorator", registry)
    async def failing_func():
        raise RuntimeError("failed")

    # Trigger circuit open.
    for _ in range(3):
        with pytest.raises(RuntimeError):
            await failing_func()

    # After opening, CircuitBreakerError should be raised.
    with pytest.raises(CircuitBreakerError):
        await failing_func()
