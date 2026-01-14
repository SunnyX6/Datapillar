"""熔断器状态机测试

测试核心状态转换：
1. CLOSED → OPEN（连续失败达阈值）
2. OPEN → HALF_OPEN（等待恢复时间）
3. HALF_OPEN → CLOSED（探测成功）
4. HALF_OPEN → OPEN（探测失败）
"""

from __future__ import annotations

import asyncio
import time
from unittest.mock import patch

import pytest

from datapillar_oneagentic.resilience.circuit_breaker import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitState,
    get_circuit_breaker,
    with_circuit_breaker,
)


class _MockConfig:
    failure_threshold = 3
    recovery_seconds = 0.1  # 100ms，测试用


@pytest.fixture
def circuit_breaker():
    """创建测试用熔断器"""
    with patch(
        "datapillar_oneagentic.resilience.circuit_breaker.get_llm_circuit_breaker_config",
        return_value=_MockConfig(),
    ):
        cb = CircuitBreaker("test")
        yield cb


@pytest.mark.asyncio
async def test_circuit_breaker_should_start_in_closed_state(circuit_breaker) -> None:
    """熔断器初始状态应为 CLOSED"""
    assert circuit_breaker.state == CircuitState.CLOSED


@pytest.mark.asyncio
async def test_circuit_breaker_should_allow_request_in_closed_state(circuit_breaker) -> None:
    """CLOSED 状态应允许请求"""
    assert await circuit_breaker.allow_request() is True


@pytest.mark.asyncio
async def test_circuit_breaker_should_transition_to_open_after_threshold_failures(
    circuit_breaker,
) -> None:
    """连续失败达阈值后应转换到 OPEN 状态"""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN


@pytest.mark.asyncio
async def test_circuit_breaker_should_reject_request_in_open_state(circuit_breaker) -> None:
    """OPEN 状态应拒绝请求"""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert await circuit_breaker.allow_request() is False


@pytest.mark.asyncio
async def test_circuit_breaker_should_transition_to_half_open_after_recovery_timeout(
    circuit_breaker,
) -> None:
    """等待恢复时间后应转换到 HALF_OPEN 状态"""
    for _ in range(3):
        await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN

    # 等待恢复时间
    await asyncio.sleep(0.15)

    # 触发状态检查
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN


@pytest.mark.asyncio
async def test_circuit_breaker_should_transition_to_closed_on_success_in_half_open(
    circuit_breaker,
) -> None:
    """HALF_OPEN 状态下成功后应转换到 CLOSED 状态"""
    # 触发熔断
    for _ in range(3):
        await circuit_breaker.record_failure()

    # 等待进入 HALF_OPEN
    await asyncio.sleep(0.15)
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN

    # 记录成功
    await circuit_breaker.record_success()

    assert circuit_breaker.state == CircuitState.CLOSED


@pytest.mark.asyncio
async def test_circuit_breaker_should_transition_to_open_on_failure_in_half_open(
    circuit_breaker,
) -> None:
    """HALF_OPEN 状态下失败后应转换回 OPEN 状态"""
    # 触发熔断
    for _ in range(3):
        await circuit_breaker.record_failure()

    # 等待进入 HALF_OPEN
    await asyncio.sleep(0.15)
    await circuit_breaker.allow_request()

    assert circuit_breaker.state == CircuitState.HALF_OPEN

    # 记录失败
    await circuit_breaker.record_failure()

    assert circuit_breaker.state == CircuitState.OPEN


@pytest.mark.asyncio
async def test_circuit_breaker_should_reset_failure_count_on_success(circuit_breaker) -> None:
    """成功后应重置失败计数"""
    await circuit_breaker.record_failure()
    await circuit_breaker.record_failure()
    assert circuit_breaker._failure_count == 2

    await circuit_breaker.record_success()
    assert circuit_breaker._failure_count == 0


@pytest.mark.asyncio
async def test_with_circuit_breaker_decorator_should_raise_error_when_open() -> None:
    """装饰器在 OPEN 状态应抛出 CircuitBreakerError"""

    with patch(
        "datapillar_oneagentic.resilience.circuit_breaker.get_llm_circuit_breaker_config",
        return_value=_MockConfig(),
    ):
        cb = get_circuit_breaker("test_decorator")
        cb.reset()

        @with_circuit_breaker("test_decorator")
        async def failing_func():
            raise RuntimeError("失败")

        # 触发熔断
        for _ in range(3):
            with pytest.raises(RuntimeError):
                await failing_func()

        # 熔断后应抛出 CircuitBreakerError
        with pytest.raises(CircuitBreakerError):
            await failing_func()
