# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Unified status definitions.

Goal: keep all execution statuses centralized and avoid scattered literals.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Final


class ExecutionStatus(StrEnum):
    """Execution status (single source of truth)."""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"
    ABORTED = "aborted"


class AgentStatus(StrEnum):
    """Agent status."""

    IDLE = "idle"
    THINKING = "thinking"
    INVOKING = "invoking"  # Invoking tools.
    WAITING = "waiting"  # Waiting for user input.
    DONE = "done"
    FAILED = "failed"
    ABORTED = "aborted"

class ProcessStage(StrEnum):
    """Process stage (internal stage such as ReAct, not a status)."""

    PLANNING = "planning"
    EXECUTING = "executing"
    REFLECTING = "reflecting"


TERMINAL_STATUSES: Final[set[ExecutionStatus]] = {
    ExecutionStatus.COMPLETED,
    ExecutionStatus.FAILED,
    ExecutionStatus.SKIPPED,
    ExecutionStatus.ABORTED,
}

def _normalize_status(status: ExecutionStatus | str | None) -> ExecutionStatus | None:
    if status is None:
        return None
    if isinstance(status, ExecutionStatus):
        return status
    if isinstance(status, str):
        try:
            return ExecutionStatus(status)
        except ValueError:
            return None
    return None


def is_terminal(status: ExecutionStatus | str | None) -> bool:
    normalized = _normalize_status(status)
    if normalized is None:
        return False
    return normalized in TERMINAL_STATUSES


def is_failed(status: ExecutionStatus | str | None) -> bool:
    normalized = _normalize_status(status)
    return normalized == ExecutionStatus.FAILED
