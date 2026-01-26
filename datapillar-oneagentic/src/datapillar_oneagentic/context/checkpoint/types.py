"""Checkpoint type definitions."""

from __future__ import annotations

from enum import Enum


class CheckpointType(str, Enum):
    """Checkpoint type."""

    AUTO = "auto"  # Auto-created.
    MANUAL = "manual"  # Manually created.
    AGENT_END = "agent_end"  # Agent execution finished.
    USER_MESSAGE = "user_message"  # After user message.
    ERROR_RECOVERY = "error_recovery"  # Error recovery point.
