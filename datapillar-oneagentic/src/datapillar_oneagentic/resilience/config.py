"""
弹性机制配置

提供获取 LLM 弹性配置的辅助函数。
配置本身定义在 providers/llm/config.py 中。
"""

import random

from datapillar_oneagentic.providers.llm.config import (
    CircuitBreakerConfig,
    RetryConfig,
)


def get_llm_retry_config() -> RetryConfig:
    """获取 LLM 重试配置"""
    from datapillar_oneagentic.config import get_config

    return get_config().llm.retry


def get_llm_circuit_breaker_config() -> CircuitBreakerConfig:
    """获取 LLM 熔断配置"""
    from datapillar_oneagentic.config import get_config

    return get_config().llm.circuit_breaker


def get_llm_timeout() -> float:
    """获取 LLM 超时配置（秒）"""
    from datapillar_oneagentic.config import get_config

    return get_config().llm.timeout_seconds


def calculate_retry_delay(config: RetryConfig, attempt: int) -> float:
    """
    计算第 N 次重试的延迟（秒）

    使用指数退避 + 可选抖动。
    """
    delay_ms = config.initial_delay_ms * (config.exponential_base**attempt)
    delay_ms = min(delay_ms, config.max_delay_ms)

    if config.jitter:
        jitter_range = delay_ms * 0.25
        delay_ms += random.uniform(-jitter_range, jitter_range)

    return max(delay_ms / 1000.0, 0.0)
