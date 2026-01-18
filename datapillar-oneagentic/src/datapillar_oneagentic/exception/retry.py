"""
重试与退避策略

支持指数退避 + 抖动，只对可重试错误进行重试。
"""

from __future__ import annotations

import asyncio
import logging
import random
from collections.abc import Awaitable, Callable
from functools import wraps
from typing import ParamSpec, Protocol, TypeVar

from datapillar_oneagentic.providers.llm.config import RetryConfig
from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.classifier import LLMErrorClassifier

logger = logging.getLogger(__name__)

P = ParamSpec("P")
T = TypeVar("T")


class RetryConfigLike(Protocol):
    """支持重试延迟计算的配置协议"""

    initial_delay_ms: int
    max_delay_ms: int
    exponential_base: float
    jitter: bool


def calculate_retry_delay(config: RetryConfigLike, attempt: int) -> float:
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


def with_retry(
    max_retries: int | None = None,
    on_retry: Callable[[int, Exception], None] | None = None,
    retry_config: RetryConfig | None = None,
):
    """
    异步重试装饰器

    特性：
    - 只对可重试错误进行重试
    - 指数退避 + 抖动
    - 使用 LLM 配置中的重试参数

    参数：
        max_retries: 最大重试次数（None 则使用配置值）
        on_retry: 重试回调（用于日志/监控）
        retry_config: 重试配置（必传）
    """

    def decorator(func: Callable[P, Awaitable[T]]) -> Callable[P, Awaitable[T]]:
        @wraps(func)
        async def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            if retry_config is None:
                raise ValueError("retry_config 不能为空")
            config = retry_config
            retries = max_retries if max_retries is not None else config.max_retries
            last_error: Exception | None = None

            for attempt in range(retries + 1):
                try:
                    return await func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    _, action = LLMErrorClassifier.classify(e)

                    if action != RecoveryAction.RETRY:
                        logger.warning(
                            f"[Retry] 不可重试错误，直接失败 | func={func.__name__} | error={e}"
                        )
                        raise

                    if attempt >= retries:
                        logger.error(
                            f"[Retry] 重试耗尽 | func={func.__name__} | "
                            f"attempts={attempt + 1} | error={e}"
                        )
                        raise

                    delay = calculate_retry_delay(config, attempt)
                    logger.warning(
                        f"[Retry] 第 {attempt + 1}/{retries} 次重试失败，"
                        f"{delay:.2f}s 后重试 | func={func.__name__} | error={e}"
                    )

                    if on_retry:
                        on_retry(attempt + 1, e)

                    await asyncio.sleep(delay)

            raise last_error or RuntimeError("Retry exhausted unexpectedly")

        return wrapper

    return decorator


def with_retry_sync(
    max_retries: int | None = None,
    on_retry: Callable[[int, Exception], None] | None = None,
    retry_config: RetryConfig | None = None,
):
    """
    同步重试装饰器（用于非异步场景）
    """
    import time

    def decorator(func: Callable[P, T]) -> Callable[P, T]:
        @wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            if retry_config is None:
                raise ValueError("retry_config 不能为空")
            config = retry_config
            retries = max_retries if max_retries is not None else config.max_retries
            last_error: Exception | None = None

            for attempt in range(retries + 1):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    _, action = LLMErrorClassifier.classify(e)

                    if action != RecoveryAction.RETRY:
                        raise

                    if attempt >= retries:
                        raise

                    delay = calculate_retry_delay(config, attempt)
                    logger.warning(
                        f"[Retry] 第 {attempt + 1}/{retries} 次重试失败，"
                        f"{delay:.2f}s 后重试 | func={func.__name__} | error={e}"
                    )

                    if on_retry:
                        on_retry(attempt + 1, e)

                    time.sleep(delay)

            raise last_error or RuntimeError("Retry exhausted unexpectedly")

        return wrapper

    return decorator
