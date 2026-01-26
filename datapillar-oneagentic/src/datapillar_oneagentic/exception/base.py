"""
Shared exception primitives.

Defines RecoveryAction and other base concepts used by Agent/LLM errors.
"""

from __future__ import annotations

from enum import Enum


class RecoveryAction(str, Enum):
    """Recovery action."""

    RETRY = "retry"  # Automatic retry
    FAIL_FAST = "fail_fast"  # Fail fast (no retry)
    CIRCUIT_BREAK = "circuit_break"  # Circuit break
