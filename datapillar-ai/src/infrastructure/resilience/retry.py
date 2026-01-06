"""
重试装饰器

支持指数退避 + 抖动，只对可重试错误进行重试。
"""

import asyncio
import logging
from collections.abc import Awaitable, Callable
from functools import wraps
from typing import ParamSpec, TypeVar

from src.infrastructure.resilience.config import get_resilience_config
from src.infrastructure.resilience.errors import ErrorClassifier, RecoveryAction

logger = logging.getLogger(__name__)

P = ParamSpec("P")
T = TypeVar("T")


def with_retry(
    max_retries: int | None = None,
    on_retry: Callable[[int, Exception], None] | None = None,
):
    """
    异步重试装饰器

    特性：
    - 只对可重试错误进行重试
    - 指数退避 + 抖动
    - 使用统一配置

    参数：
        max_retries: 最大重试次数（None 则使用配置值）
        on_retry: 重试回调（用于日志/监控）

    使用：
        @with_retry()
        async def call_llm(...):
            ...

        @with_retry(max_retries=5)
        async def call_external_api(...):
            ...
    """

    def decorator(func: Callable[P, Awaitable[T]]) -> Callable[P, Awaitable[T]]:
        @wraps(func)
        async def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            config = get_resilience_config()
            retries = max_retries if max_retries is not None else config.max_retries
            last_error: Exception | None = None

            for attempt in range(retries + 1):
                try:
                    return await func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    _, action = ErrorClassifier.classify(e)

                    # 不可重试错误 - 直接抛出
                    if action != RecoveryAction.RETRY:
                        logger.warning(
                            f"[Retry] 不可重试错误，直接失败 | func={func.__name__} | error={e}"
                        )
                        raise

                    # 已达最大重试次数
                    if attempt >= retries:
                        logger.error(
                            f"[Retry] 重试耗尽 | func={func.__name__} | "
                            f"attempts={attempt + 1} | error={e}"
                        )
                        raise

                    # 计算延迟并等待
                    delay = config.calculate_delay(attempt)
                    logger.warning(
                        f"[Retry] 第 {attempt + 1}/{retries} 次重试失败，"
                        f"{delay:.2f}s 后重试 | func={func.__name__} | error={e}"
                    )

                    if on_retry:
                        on_retry(attempt + 1, e)

                    await asyncio.sleep(delay)

            # 理论上不会到这里
            raise last_error or RuntimeError("Retry exhausted unexpectedly")

        return wrapper

    return decorator


def with_retry_sync(
    max_retries: int | None = None,
    on_retry: Callable[[int, Exception], None] | None = None,
):
    """
    同步重试装饰器（用于非异步场景）
    """
    import time

    def decorator(func: Callable[P, T]) -> Callable[P, T]:
        @wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> T:
            config = get_resilience_config()
            retries = max_retries if max_retries is not None else config.max_retries
            last_error: Exception | None = None

            for attempt in range(retries + 1):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    _, action = ErrorClassifier.classify(e)

                    if action != RecoveryAction.RETRY:
                        raise

                    if attempt >= retries:
                        raise

                    delay = config.calculate_delay(attempt)
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
