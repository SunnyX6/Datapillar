"""
全局事件总线模块

提供框架级别的事件发布/订阅机制。
"""

from datapillar_oneagentic.events.bus import EventBus, event_bus
from datapillar_oneagentic.events.base import BaseEvent
from datapillar_oneagentic.events.types import (
    # Agent 事件
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
    # 工具事件
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
    # LLM 事件
    LLMCallStartedEvent,
    LLMCallCompletedEvent,
    LLMStreamChunkEvent,
    # 委派事件
    DelegationStartedEvent,
    DelegationCompletedEvent,
    # 系统事件
    SessionStartedEvent,
    SessionCompletedEvent,
)

__all__ = [
    # 核心
    "EventBus",
    "event_bus",
    "BaseEvent",
    # Agent 事件
    "AgentStartedEvent",
    "AgentCompletedEvent",
    "AgentFailedEvent",
    # 工具事件
    "ToolCalledEvent",
    "ToolCompletedEvent",
    "ToolFailedEvent",
    # LLM 事件
    "LLMCallStartedEvent",
    "LLMCallCompletedEvent",
    "LLMStreamChunkEvent",
    # 委派事件
    "DelegationStartedEvent",
    "DelegationCompletedEvent",
    # 系统事件
    "SessionStartedEvent",
    "SessionCompletedEvent",
]
