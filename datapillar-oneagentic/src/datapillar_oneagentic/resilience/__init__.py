"""
弹性机制模块

提供统一的错误恢复能力：
- 错误分类（可重试 vs 不可重试）
- 重试装饰器（指数退避 + 抖动）
- 熔断器（防止级联故障）

配置在 llm 配置中：
- llm.retry.*
- llm.circuit_breaker.*

使用方式：
    from datapillar_oneagentic.resilience import (
        with_retry,
        get_circuit_breaker,
        ErrorClassifier,
    )
"""

from datapillar_oneagentic.resilience.circuit_breaker import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitState,
    get_circuit_breaker,
)
from datapillar_oneagentic.resilience.config import (
    calculate_retry_delay,
    get_llm_circuit_breaker_config,
    get_llm_retry_config,
    get_llm_timeout,
)
from datapillar_oneagentic.resilience.errors import (
    ErrorCategory,
    ErrorClassifier,
    NonRetryableError,
    RecoveryAction,
    RetryableError,
)
from datapillar_oneagentic.resilience.exception_mapping import (
    ContextLengthExceededError,
    ExceptionMapper,
)
from datapillar_oneagentic.resilience.retry import with_retry

__all__ = [
    # 配置获取
    "get_llm_retry_config",
    "get_llm_circuit_breaker_config",
    "get_llm_timeout",
    "calculate_retry_delay",
    # 错误
    "ErrorCategory",
    "RecoveryAction",
    "ErrorClassifier",
    "RetryableError",
    "NonRetryableError",
    # 异常映射
    "ContextLengthExceededError",
    "ExceptionMapper",
    # 重试
    "with_retry",
    # 熔断
    "CircuitBreaker",
    "CircuitState",
    "CircuitBreakerError",
    "get_circuit_breaker",
]
