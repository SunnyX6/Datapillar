# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Event constants.

Single source of truth for event enums used by Timeline/SSE/Events modules.
"""

from __future__ import annotations

from enum import Enum


class EventType(str, Enum):
    """
    Unified event types.

    Naming convention: <module>.<action>
    """

    # === Session events ===
    SESSION_START = "session.start"
    SESSION_END = "session.end"
    SESSION_RESUME = "session.resume"
    SESSION_ABORT = "session.abort"

    # === User events ===
    USER_MESSAGE = "user.message"
    USER_INTERRUPT = "user.interrupt"
    USER_FEEDBACK = "user.feedback"

    # === Agent events ===
    AGENT_START = "agent.start"
    AGENT_END = "agent.end"
    AGENT_HANDOVER = "agent.handover"
    AGENT_FAILED = "agent.failed"
    AGENT_THINKING = "agent.thinking"
    AGENT_INTERRUPT = "agent.interrupt"

    # === Tool events ===
    TOOL_CALL = "tool.call"
    TOOL_RESULT = "tool.result"
    TOOL_ERROR = "tool.error"

    # === LLM events ===
    LLM_START = "llm.start"
    LLM_END = "llm.end"
    LLM_CHUNK = "llm.chunk"

    # === Decision events ===
    DECISION = "decision"
    CLARIFICATION = "clarification"
    CONSTRAINT = "constraint"

    # === Memory events ===
    MEMORY_COMPACT = "memory.compact"
    MEMORY_UPDATE = "memory.update"

    # === Checkpoint events ===
    CHECKPOINT_CREATE = "checkpoint.create"
    CHECKPOINT_RESTORE = "checkpoint.restore"

    # === Delegation events ===
    DELEGATION_START = "delegation.start"
    DELEGATION_END = "delegation.end"

    # === System events ===
    ERROR = "error"
    RETRY = "retry"
    TIMEOUT = "timeout"

    @classmethod
    def from_string(cls, value: str) -> "EventType":
        """Parse an event type from a string."""
        for event_type in cls:
            if event_type.value == value:
                return event_type
        raise ValueError(f"Unknown event type: {value}")

    @property
    def category(self) -> str:
        """Return the event category."""
        if "." in self.value:
            return self.value.split(".")[0]
        return self.value

    @property
    def action(self) -> str:
        """Return the event action."""
        if "." in self.value:
            return self.value.split(".")[1]
        return self.value


class EventLevel(str, Enum):
    """Event level."""

    DEBUG = "debug"
    INFO = "info"
    WARNING = "warning"
    ERROR = "error"
    SUCCESS = "success"
