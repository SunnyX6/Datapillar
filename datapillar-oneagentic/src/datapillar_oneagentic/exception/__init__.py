# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""Unified exports for exception and resilience modules."""

from datapillar_oneagentic.exception.agent_execution_failed import (
    AgentExecutionFailedException,
)
from datapillar_oneagentic.exception.agent_result_invalid import AgentResultInvalidException
from datapillar_oneagentic.exception.bad_request import BadRequestException
from datapillar_oneagentic.exception.base import DatapillarException, RecoveryAction
from datapillar_oneagentic.exception.circuit_breaker import (
    CircuitBreaker,
    CircuitBreakerError,
    CircuitBreakerRegistry,
    CircuitState,
    with_circuit_breaker,
)
from datapillar_oneagentic.exception.connection_failed import ConnectionFailedException
from datapillar_oneagentic.exception.context_length_exceeded import (
    ContextLengthExceededException,
)
from datapillar_oneagentic.exception.internal import InternalException
from datapillar_oneagentic.exception.mapper import ExceptionMapper
from datapillar_oneagentic.exception.not_found import NotFoundException
from datapillar_oneagentic.exception.policy import action_for
from datapillar_oneagentic.exception.retry import calculate_retry_delay, with_retry, with_retry_sync
from datapillar_oneagentic.exception.service_unavailable import ServiceUnavailableException
from datapillar_oneagentic.exception.structured_output_invalid import (
    StructuredOutputInvalidException,
)
from datapillar_oneagentic.exception.timeout import TimeoutException
from datapillar_oneagentic.exception.too_many_requests import TooManyRequestsException
from datapillar_oneagentic.exception.unauthorized import UnauthorizedException

__all__ = [
    "RecoveryAction",
    "DatapillarException",
    "BadRequestException",
    "UnauthorizedException",
    "NotFoundException",
    "TooManyRequestsException",
    "ConnectionFailedException",
    "TimeoutException",
    "ServiceUnavailableException",
    "ContextLengthExceededException",
    "StructuredOutputInvalidException",
    "InternalException",
    "AgentExecutionFailedException",
    "AgentResultInvalidException",
    "ExceptionMapper",
    "action_for",
    "calculate_retry_delay",
    "with_retry",
    "with_retry_sync",
    "CircuitBreaker",
    "CircuitBreakerRegistry",
    "CircuitBreakerError",
    "CircuitState",
    "with_circuit_breaker",
]
