# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Retry and backoff strategy.

Uses exponential backoff with optional jitter and retries only retryable errors.
"""

from __future__ import annotations

import asyncio
import logging
import random
from collections.abc import Awaitable, Callable
from functools import wraps
from typing import TYPE_CHECKING, ParamSpec, TypeVar
from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.classifier import LLMErrorClassifier

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.config import RetryConfig

logger = logging.getLogger(__name__)

P = ParamSpec("P")
T = TypeVar("T")


def calculate_retry_delay(config: "RetryConfig", attempt: int) -> float:
    """
    Calculate the delay for the N-th retry (seconds).

    Uses exponential backoff with optional jitter.
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
    retry_config: "RetryConfig" | None = None,
):
    """
    Async retry decorator.

    Features:
    - Retries only retryable errors
    - Exponential backoff with jitter
    - Uses retry config from LLM settings

    Args:
        max_retries: Max retry attempts (None uses config value)
        on_retry: Retry callback (logging/monitoring)
        retry_config: Retry configuration (required)
    """

    def decorator(func: Callable[P, Awaitable[T]]) -> Callable[P, Awaitable[T]]:
        @wraps(func)
        async def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            if retry_config is None:
                raise ValueError("retry_config is required")
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
                            f"[Retry] Non-retryable error; failing fast | func={func.__name__} | error={e}"
                        )
                        raise

                    if attempt >= retries:
                        logger.error(
                            f"[Retry] Retries exhausted | func={func.__name__} | "
                            f"attempts={attempt + 1} | error={e}"
                        )
                        raise

                    delay = calculate_retry_delay(config, attempt)
                    logger.warning(
                        f"[Retry] Retry {attempt + 1}/{retries} failed; "
                        f"retrying in {delay:.2f}s | func={func.__name__} | error={e}"
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
    retry_config: "RetryConfig" | None = None,
):
    """
    Sync retry decorator (for non-async usage).
    """
    import time

    def decorator(func: Callable[P, T]) -> Callable[P, T]:
        @wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            if retry_config is None:
                raise ValueError("retry_config is required")
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
                        f"[Retry] Retry {attempt + 1}/{retries} failed; "
                        f"retrying in {delay:.2f}s | func={func.__name__} | error={e}"
                    )

                    if on_retry:
                        on_retry(attempt + 1, e)

                    time.sleep(delay)

            raise last_error or RuntimeError("Retry exhausted unexpectedly")

        return wrapper

    return decorator
