"""
LLM 错误分类器
"""

from __future__ import annotations

import re

from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.categories import LLMErrorCategory
from datapillar_oneagentic.exception.llm.circuit_breaker import CircuitBreakerError
from datapillar_oneagentic.exception.llm.errors import NonRetryableError, RetryableError


class LLMErrorClassifier:
    """
    LLM 错误分类器

    根据异常信息判断类别和恢复动作。
    """

    # 认证错误
    AUTH_PATTERNS = [
        r"invalid\s*api\s*key",
        r"unauthorized",
        r"401",
        r"403",
        r"authentication\s*failed",
        r"permission\s*denied",
    ]

    # 输入错误
    INVALID_PATTERNS = [
        r"invalid\s*(request|parameter|argument)",
        r"bad\s*request",
        r"400",
        r"malformed",
        r"validation\s*(error|failed)",
    ]

    # 资源不存在
    NOT_FOUND_PATTERNS = [
        r"not\s*found",
        r"404",
    ]

    # 限流错误
    RATE_LIMIT_PATTERNS = [
        r"rate\s*limit",
        r"too\s*many\s*requests",
        r"429",
        r"quota\s*exceeded",
    ]

    # 可重试错误
    TRANSIENT_PATTERNS = [
        r"timed?\s*out",
        r"connection\s*(reset|refused|closed|error)",
        r"temporarily\s*unavailable",
        r"service\s*unavailable",
        r"503",
        r"502",
        r"504",
        r"500",
        r"overloaded",
        r"capacity",
        r"retry",
        r"temporary",
        r"transient",
        r"ECONNRESET",
        r"ETIMEDOUT",
        r"ENOTFOUND",
    ]

    @classmethod
    def classify(cls, error: Exception) -> tuple[LLMErrorCategory, RecoveryAction]:
        """
        分类错误并返回恢复策略

        Returns:
            (LLMErrorCategory, RecoveryAction)
        """
        error_str = str(error).lower()
        error_type = type(error).__name__.lower()

        if isinstance(error, CircuitBreakerError):
            return (LLMErrorCategory.CIRCUIT_OPEN, RecoveryAction.CIRCUIT_BREAK)

        if isinstance(error, (TimeoutError, ConnectionError, OSError)):
            return (LLMErrorCategory.TIMEOUT, RecoveryAction.RETRY)

        if isinstance(error, RetryableError):
            return (LLMErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        if isinstance(error, NonRetryableError):
            return (LLMErrorCategory.INVALID_INPUT, RecoveryAction.FAIL_FAST)

        for pattern in cls.AUTH_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (LLMErrorCategory.AUTH_FAILURE, RecoveryAction.FAIL_FAST)

        for pattern in cls.NOT_FOUND_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (LLMErrorCategory.NOT_FOUND, RecoveryAction.FAIL_FAST)

        for pattern in cls.INVALID_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (LLMErrorCategory.INVALID_INPUT, RecoveryAction.FAIL_FAST)

        for pattern in cls.RATE_LIMIT_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (LLMErrorCategory.RATE_LIMIT, RecoveryAction.RETRY)

        for pattern in cls.TRANSIENT_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (LLMErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        if "timeout" in error_type:
            return (LLMErrorCategory.TIMEOUT, RecoveryAction.RETRY)
        if "connection" in error_type:
            return (LLMErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        return (LLMErrorCategory.INTERNAL, RecoveryAction.FAIL_FAST)

    @classmethod
    def is_retryable(cls, error: Exception) -> bool:
        """判断错误是否可重试"""
        _, action = cls.classify(error)
        return action == RecoveryAction.RETRY
