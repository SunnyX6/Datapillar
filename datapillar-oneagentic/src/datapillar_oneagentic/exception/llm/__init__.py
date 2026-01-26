"""
LLM error domain.
"""

from datapillar_oneagentic.exception.llm.categories import LLMErrorCategory
from datapillar_oneagentic.exception.llm.classifier import LLMErrorClassifier
from datapillar_oneagentic.exception.llm.circuit_breaker import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitBreakerRegistry,
    CircuitState,
    with_circuit_breaker,
)
from datapillar_oneagentic.exception.llm.errors import LLMError, NonRetryableError, RetryableError
from datapillar_oneagentic.exception.llm.exception_mapping import (
    ContextLengthExceededError,
    ExceptionMapper,
)

__all__ = [
    "LLMErrorCategory",
    "LLMErrorClassifier",
    "LLMError",
    "RetryableError",
    "NonRetryableError",
    "CircuitBreaker",
    "CircuitBreakerRegistry",
    "CircuitBreakerError",
    "CircuitState",
    "with_circuit_breaker",
    "ContextLengthExceededError",
    "ExceptionMapper",
]
