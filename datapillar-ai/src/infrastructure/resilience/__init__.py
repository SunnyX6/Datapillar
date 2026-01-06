"""
通用弹性机制模块

提供统一的错误恢复能力：
- 错误分类（可重试 vs 不可重试）
- 重试装饰器（指数退避 + 抖动）
- 熔断器（防止级联故障）

使用方式：
    from src.infrastructure.resilience import (
        get_resilience_config,
        with_retry,
        get_circuit_breaker,
        ErrorClassifier,
    )
"""

from src.infrastructure.resilience.circuit_breaker import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitState,
    get_circuit_breaker,
)
from src.infrastructure.resilience.config import (
    ResilienceConfig,
    get_resilience_config,
)
from src.infrastructure.resilience.errors import (
    ErrorCategory,
    ErrorClassifier,
    NonRetryableError,
    RecoveryAction,
    RetryableError,
)
from src.infrastructure.resilience.retry import with_retry

__all__ = [
    # 配置
    "ResilienceConfig",
    "get_resilience_config",
    # 错误
    "ErrorCategory",
    "RecoveryAction",
    "ErrorClassifier",
    "RetryableError",
    "NonRetryableError",
    # 重试
    "with_retry",
    # 熔断
    "CircuitBreaker",
    "CircuitState",
    "CircuitBreakerError",
    "get_circuit_breaker",
]
