"""
Event type definitions.

Defines the event types used by the framework.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events.base import BaseEvent

# === Agent events ===


@dataclass
class AgentStartedEvent(BaseEvent):
    """Agent execution started."""

    agent_id: str = ""
    agent_name: str = ""
    key: SessionKey | None = None
    query: str = ""


@dataclass
class AgentCompletedEvent(BaseEvent):
    """Agent execution completed."""

    agent_id: str = ""
    agent_name: str = ""
    key: SessionKey | None = None
    result: Any = None
    duration_ms: float = 0.0


@dataclass
class AgentFailedEvent(BaseEvent):
    """Agent execution failed."""

    agent_id: str = ""
    agent_name: str = ""
    key: SessionKey | None = None
    error: str = ""
    error_type: str = ""


@dataclass
class AgentThinkingEvent(BaseEvent):
    """Agent thinking content."""

    agent_id: str = ""
    agent_name: str = ""
    key: SessionKey | None = None
    content: str = ""


@dataclass
class AgentInterruptedEvent(BaseEvent):
    """Agent interrupted (waiting for user input)."""

    agent_id: str = ""
    agent_name: str = ""
    key: SessionKey | None = None
    payload: Any = None


# === Tool events ===


@dataclass
class ToolCalledEvent(BaseEvent):
    """Tool called."""

    agent_id: str = ""
    key: SessionKey | None = None
    tool_name: str = ""
    tool_call_id: str = ""
    tool_input: dict[str, Any] = field(default_factory=dict)


@dataclass
class ToolCompletedEvent(BaseEvent):
    """Tool execution completed."""

    agent_id: str = ""
    key: SessionKey | None = None
    tool_name: str = ""
    tool_call_id: str = ""
    tool_output: Any = None
    duration_ms: float = 0.0


@dataclass
class ToolFailedEvent(BaseEvent):
    """Tool execution failed."""

    agent_id: str = ""
    key: SessionKey | None = None
    tool_name: str = ""
    tool_call_id: str = ""
    error: str = ""


# === LLM events ===


@dataclass
class LLMThinkingEvent(BaseEvent):
    """LLM thinking content (when thinking mode is enabled)."""

    agent_id: str = ""
    key: SessionKey | None = None
    thinking_content: str = ""


@dataclass
class LLMCallStartedEvent(BaseEvent):
    """LLM invocation started."""

    agent_id: str = ""
    key: SessionKey | None = None
    model: str = ""
    message_count: int = 0


@dataclass
class LLMCallCompletedEvent(BaseEvent):
    """LLM invocation completed."""

    agent_id: str = ""
    key: SessionKey | None = None
    model: str = ""
    input_tokens: int = 0
    output_tokens: int = 0
    cached_tokens: int = 0
    duration_ms: float = 0.0


@dataclass
class LLMCallFailedEvent(BaseEvent):
    """LLM invocation failed."""

    agent_id: str = ""
    key: SessionKey | None = None
    model: str = ""
    error: str = ""
    duration_ms: float = 0.0


@dataclass
class LLMStreamChunkEvent(BaseEvent):
    """LLM streaming output chunk."""

    agent_id: str = ""
    key: SessionKey | None = None
    chunk: str = ""
    is_final: bool = False


# === Delegation events ===


@dataclass
class DelegationStartedEvent(BaseEvent):
    """Delegation started."""

    from_agent_id: str = ""
    to_agent_id: str = ""
    key: SessionKey | None = None
    task: str = ""
    is_a2a: bool = False


@dataclass
class DelegationCompletedEvent(BaseEvent):
    """Delegation completed."""

    from_agent_id: str = ""
    to_agent_id: str = ""
    key: SessionKey | None = None
    result: Any = None
    duration_ms: float = 0.0


# === System events ===


@dataclass
class SessionStartedEvent(BaseEvent):
    """Session started."""

    key: SessionKey | None = None
    query: str = ""


@dataclass
class SessionCompletedEvent(BaseEvent):
    """Session completed."""

    key: SessionKey | None = None
    result: Any = None
    duration_ms: float = 0.0
    agent_count: int = 0
    tool_count: int = 0
