"""
错误分类体系

将异常分为「可重试」和「不可重试」两类，决定恢复策略。

设计原则：
- 技术故障（网络、超时、限流）→ 可重试，系统自动处理
- 业务问题（参数错误、认证失败）→ 不可重试，需要干预
"""

import re
from enum import Enum


class ErrorCategory(str, Enum):
    """错误分类"""

    # 可重试错误（系统自动处理，用户无感知）
    TRANSIENT = "transient"  # 瞬态错误：网络抖动、临时不可用
    TIMEOUT = "timeout"  # 超时错误：LLM/API 响应超时
    RATE_LIMIT = "rate_limit"  # 限流错误：429、配额耗尽

    # 不可重试错误（需要干预）
    INVALID_INPUT = "invalid_input"  # 输入错误：参数格式错误
    AUTH_FAILURE = "auth_failure"  # 认证错误：API Key 无效
    NOT_FOUND = "not_found"  # 资源不存在

    # 系统错误
    INTERNAL = "internal"  # 内部错误：代码 bug


class RecoveryAction(str, Enum):
    """恢复动作"""

    RETRY = "retry"  # 自动重试
    FAIL_FAST = "fail_fast"  # 快速失败（不重试）
    CIRCUIT_BREAK = "circuit_break"  # 熔断


class RetryableError(Exception):
    """可重试错误（包装原始异常）"""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class NonRetryableError(Exception):
    """不可重试错误（包装原始异常）"""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class ErrorClassifier:
    """
    错误智能分类器

    根据异常信息判断是否可重试。
    """

    # 可重试错误的特征模式
    TRANSIENT_PATTERNS = [
        r"timeout",
        r"timed?\s*out",
        r"connection\s*(reset|refused|closed|error)",
        r"temporarily\s*unavailable",
        r"service\s*unavailable",
        r"503",
        r"502",
        r"504",
        r"500",  # 服务端错误通常可重试
        r"rate\s*limit",
        r"too\s*many\s*requests",
        r"429",
        r"quota\s*exceeded",
        r"overloaded",
        r"capacity",
        r"retry",
        r"temporary",
        r"transient",
        r"ECONNRESET",
        r"ETIMEDOUT",
        r"ENOTFOUND",
    ]

    # 不可重试错误的特征模式
    NON_RETRYABLE_PATTERNS = [
        r"invalid\s*api\s*key",
        r"unauthorized",
        r"401",
        r"403",
        r"authentication\s*failed",
        r"permission\s*denied",
        r"invalid\s*(request|parameter|argument)",
        r"bad\s*request",
        r"400",
        r"not\s*found",
        r"404",
        r"malformed",
        r"validation\s*(error|failed)",
    ]

    @classmethod
    def classify(cls, error: Exception) -> tuple[ErrorCategory, RecoveryAction]:
        """
        分类错误并返回恢复策略

        Returns:
            (ErrorCategory, RecoveryAction)
        """
        error_str = str(error).lower()
        error_type = type(error).__name__.lower()

        # 1. 已知的可重试异常类型
        if isinstance(error, (TimeoutError, ConnectionError, OSError)):
            return (ErrorCategory.TIMEOUT, RecoveryAction.RETRY)

        if isinstance(error, RetryableError):
            return (ErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        if isinstance(error, NonRetryableError):
            return (ErrorCategory.INVALID_INPUT, RecoveryAction.FAIL_FAST)

        # 2. 检查不可重试模式（优先级更高，避免误重试）
        for pattern in cls.NON_RETRYABLE_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (ErrorCategory.AUTH_FAILURE, RecoveryAction.FAIL_FAST)

        # 3. 检查可重试模式
        for pattern in cls.TRANSIENT_PATTERNS:
            if re.search(pattern, error_str, re.IGNORECASE):
                return (ErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        # 4. 根据异常类型名推断
        if "timeout" in error_type:
            return (ErrorCategory.TIMEOUT, RecoveryAction.RETRY)
        if "connection" in error_type:
            return (ErrorCategory.TRANSIENT, RecoveryAction.RETRY)

        # 5. 未知错误 - 默认不重试（保守策略）
        return (ErrorCategory.INTERNAL, RecoveryAction.FAIL_FAST)

    @classmethod
    def is_retryable(cls, error: Exception) -> bool:
        """判断错误是否可重试"""
        _, action = cls.classify(error)
        return action == RecoveryAction.RETRY
