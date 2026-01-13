"""
事件类型定义

定义框架中使用的各种事件类型。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from datapillar_oneagentic.events.base import BaseEvent


# === Agent 事件 ===


@dataclass
class AgentStartedEvent(BaseEvent):
    """Agent 开始执行"""

    agent_id: str = ""
    agent_name: str = ""
    session_id: str = ""
    query: str = ""


@dataclass
class AgentCompletedEvent(BaseEvent):
    """Agent 执行完成"""

    agent_id: str = ""
    agent_name: str = ""
    session_id: str = ""
    result: Any = None
    duration_ms: float = 0.0


@dataclass
class AgentFailedEvent(BaseEvent):
    """Agent 执行失败"""

    agent_id: str = ""
    agent_name: str = ""
    session_id: str = ""
    error: str = ""
    error_type: str = ""


# === 工具事件 ===


@dataclass
class ToolCalledEvent(BaseEvent):
    """工具被调用"""

    agent_id: str = ""
    tool_name: str = ""
    tool_input: dict[str, Any] = field(default_factory=dict)


@dataclass
class ToolCompletedEvent(BaseEvent):
    """工具执行完成"""

    agent_id: str = ""
    tool_name: str = ""
    tool_output: Any = None
    duration_ms: float = 0.0


@dataclass
class ToolFailedEvent(BaseEvent):
    """工具执行失败"""

    agent_id: str = ""
    tool_name: str = ""
    error: str = ""


# === LLM 事件 ===


@dataclass
class LLMThinkingEvent(BaseEvent):
    """LLM 思考过程（开启 thinking 模式时）"""

    agent_id: str = ""
    thinking_content: str = ""


@dataclass
class LLMCallStartedEvent(BaseEvent):
    """LLM 调用开始"""

    agent_id: str = ""
    model: str = ""
    message_count: int = 0


@dataclass
class LLMCallCompletedEvent(BaseEvent):
    """LLM 调用完成"""

    agent_id: str = ""
    model: str = ""
    input_tokens: int = 0
    output_tokens: int = 0
    duration_ms: float = 0.0


@dataclass
class LLMCallFailedEvent(BaseEvent):
    """LLM 调用失败"""

    agent_id: str = ""
    model: str = ""
    error: str = ""
    duration_ms: float = 0.0


@dataclass
class LLMStreamChunkEvent(BaseEvent):
    """LLM 流式输出块"""

    agent_id: str = ""
    chunk: str = ""
    is_final: bool = False


# === 委派事件 ===


@dataclass
class DelegationStartedEvent(BaseEvent):
    """委派开始"""

    from_agent_id: str = ""
    to_agent_id: str = ""
    task: str = ""
    is_a2a: bool = False


@dataclass
class DelegationCompletedEvent(BaseEvent):
    """委派完成"""

    from_agent_id: str = ""
    to_agent_id: str = ""
    result: Any = None
    duration_ms: float = 0.0


# === 系统事件 ===


@dataclass
class SessionStartedEvent(BaseEvent):
    """会话开始"""

    session_id: str = ""
    query: str = ""


@dataclass
class SessionCompletedEvent(BaseEvent):
    """会话完成"""

    session_id: str = ""
    result: Any = None
    duration_ms: float = 0.0
    agent_count: int = 0
    tool_count: int = 0
