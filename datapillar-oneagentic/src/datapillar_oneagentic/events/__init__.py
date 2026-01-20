"""
事件总线模块

提供框架级别的事件发布/订阅机制。
"""

from datapillar_oneagentic.events.base import BaseEvent
from datapillar_oneagentic.events.bus import EventBus
from datapillar_oneagentic.events.constants import EventLevel, EventType
from datapillar_oneagentic.events.payload import build_event_payload
from datapillar_oneagentic.events.types import (
    AgentCompletedEvent,
    AgentFailedEvent,
    # Agent 事件
    AgentStartedEvent,
    AgentThinkingEvent,
    AgentInterruptedEvent,
    DelegationCompletedEvent,
    # 委派事件
    DelegationStartedEvent,
    LLMCallCompletedEvent,
    LLMCallFailedEvent,
    LLMCallStartedEvent,
    LLMStreamChunkEvent,
    # LLM 事件
    LLMThinkingEvent,
    SessionCompletedEvent,
    # 系统事件
    SessionStartedEvent,
    # 工具事件
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
)

__all__ = [
    # 核心
    "EventBus",
    "BaseEvent",
    # 事件常量
    "EventType",
    "EventLevel",
    "build_event_payload",
    # Agent 事件
    "AgentStartedEvent",
    "AgentCompletedEvent",
    "AgentFailedEvent",
    "AgentThinkingEvent",
    "AgentInterruptedEvent",
    # 工具事件
    "ToolCalledEvent",
    "ToolCompletedEvent",
    "ToolFailedEvent",
    # LLM 事件
    "LLMThinkingEvent",
    "LLMCallStartedEvent",
    "LLMCallCompletedEvent",
    "LLMCallFailedEvent",
    "LLMStreamChunkEvent",
    # 委派事件
    "DelegationStartedEvent",
    "DelegationCompletedEvent",
    # 系统事件
    "SessionStartedEvent",
    "SessionCompletedEvent",
]
