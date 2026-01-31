# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Event bus module.

Provides framework-level publish/subscribe for events.
"""

from datapillar_oneagentic.events.base import BaseEvent
from datapillar_oneagentic.events.bus import EventBus
from datapillar_oneagentic.events.constants import EventLevel, EventType
from datapillar_oneagentic.events.payload import build_event_payload
from datapillar_oneagentic.events.types import (
    AgentCompletedEvent,
    AgentFailedEvent,
    # Agent events
    AgentStartedEvent,
    AgentThinkingEvent,
    AgentInterruptedEvent,
    DelegationCompletedEvent,
    # Delegation events
    DelegationStartedEvent,
    LLMCallCompletedEvent,
    LLMCallFailedEvent,
    LLMCallStartedEvent,
    LLMStreamChunkEvent,
    # LLM events
    LLMThinkingEvent,
    SessionCompletedEvent,
    # System events
    SessionStartedEvent,
    # Tool events
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
)

__all__ = [
    # Core
    "EventBus",
    "BaseEvent",
    # Event constants
    "EventType",
    "EventLevel",
    "build_event_payload",
    # Agent events
    "AgentStartedEvent",
    "AgentCompletedEvent",
    "AgentFailedEvent",
    "AgentThinkingEvent",
    "AgentInterruptedEvent",
    # Tool events
    "ToolCalledEvent",
    "ToolCompletedEvent",
    "ToolFailedEvent",
    # LLM events
    "LLMThinkingEvent",
    "LLMCallStartedEvent",
    "LLMCallCompletedEvent",
    "LLMCallFailedEvent",
    "LLMStreamChunkEvent",
    # Delegation events
    "DelegationStartedEvent",
    "DelegationCompletedEvent",
    # System events
    "SessionStartedEvent",
    "SessionCompletedEvent",
]
