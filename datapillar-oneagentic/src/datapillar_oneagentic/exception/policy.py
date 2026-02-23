# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""
Recovery policy.
"""

from __future__ import annotations

from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.circuit_breaker import CircuitBreakerError
from datapillar_oneagentic.exception.connection_failed import ConnectionFailedException
from datapillar_oneagentic.exception.service_unavailable import (
    ServiceUnavailableException,
)
from datapillar_oneagentic.exception.timeout import TimeoutException
from datapillar_oneagentic.exception.too_many_requests import (
    TooManyRequestsException,
)


def action_for(error: Exception) -> RecoveryAction:
    """Resolve recovery action by exception type."""
    if isinstance(error, CircuitBreakerError):
        return RecoveryAction.CIRCUIT_BREAK

    if isinstance(
        error,
        (
            TooManyRequestsException,
            TimeoutException,
            ConnectionFailedException,
            ServiceUnavailableException,
        ),
    ):
        return RecoveryAction.RETRY

    return RecoveryAction.FAIL_FAST
