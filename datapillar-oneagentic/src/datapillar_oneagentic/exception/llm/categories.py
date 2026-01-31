# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
LLM error categories.
"""

from __future__ import annotations

from enum import Enum


class LLMErrorCategory(str, Enum):
    """LLM error categories."""

    # Retryable errors (handled automatically)
    TRANSIENT = "transient"  # Transient error: network jitter, temporary outage
    TIMEOUT = "timeout"  # Timeout error: LLM/API response timeout
    RATE_LIMIT = "rate_limit"  # Rate limit: 429, quota exhausted

    # Non-retryable errors (require intervention)
    CONTEXT = "context"  # Context length exceeded
    INVALID_INPUT = "invalid_input"  # Invalid input: parameter format error
    STRUCTURED_OUTPUT = "structured_output"  # Structured output parsing failed
    AUTH_FAILURE = "auth_failure"  # Authentication failure: invalid API key
    NOT_FOUND = "not_found"  # Resource not found

    # System errors
    INTERNAL = "internal"  # Internal error: code bug
    CIRCUIT_OPEN = "circuit_open"  # Circuit open
