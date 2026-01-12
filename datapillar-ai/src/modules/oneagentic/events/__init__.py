"""
全局事件总线模块

提供框架级别的事件发布/订阅机制，支持：
- 同步/异步事件处理器
- 事件依赖管理
- 线程安全
- 作用域隔离（用于测试）

核心组件：
- EventBus: 事件总线单例
- BaseEvent: 事件基类
- 各种事件类型
"""

from src.modules.oneagentic.events.base import BaseEvent
from src.modules.oneagentic.events.bus import EventBus, event_bus
from src.modules.oneagentic.events.types import (
    AgentCompletedEvent,
    AgentFailedEvent,
    # Agent 事件
    AgentStartedEvent,
    DelegationCompletedEvent,
    # 委派事件
    DelegationStartedEvent,
    LLMCallCompletedEvent,
    # LLM 事件
    LLMCallStartedEvent,
    LLMStreamChunkEvent,
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
