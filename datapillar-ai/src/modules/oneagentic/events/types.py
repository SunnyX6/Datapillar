"""
事件类型定义

定义框架中使用的各种事件类型。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from src.modules.oneagentic.events.base import BaseEvent

# === Agent 事件 ===


@dataclass
class AgentStartedEvent(BaseEvent):
    """Agent 开始执行"""

    agent_id: str = ""
    """Agent ID"""

    agent_name: str = ""
    """Agent 名称"""

    session_id: str = ""
    """会话 ID"""

    query: str = ""
    """用户输入"""


@dataclass
class AgentCompletedEvent(BaseEvent):
    """Agent 执行完成"""

    agent_id: str = ""
    """Agent ID"""

    agent_name: str = ""
    """Agent 名称"""

    session_id: str = ""
    """会话 ID"""

    result: Any = None
    """执行结果"""

    duration_ms: float = 0.0
    """执行耗时（毫秒）"""


@dataclass
class AgentFailedEvent(BaseEvent):
    """Agent 执行失败"""

    agent_id: str = ""
    """Agent ID"""

    agent_name: str = ""
    """Agent 名称"""

    session_id: str = ""
    """会话 ID"""

    error: str = ""
    """错误信息"""

    error_type: str = ""
    """错误类型"""


# === 工具事件 ===


@dataclass
class ToolCalledEvent(BaseEvent):
    """工具被调用"""

    agent_id: str = ""
    """调用工具的 Agent ID"""

    tool_name: str = ""
    """工具名称"""

    tool_input: dict[str, Any] = field(default_factory=dict)
    """工具输入参数"""


@dataclass
class ToolCompletedEvent(BaseEvent):
    """工具执行完成"""

    agent_id: str = ""
    """调用工具的 Agent ID"""

    tool_name: str = ""
    """工具名称"""

    tool_output: Any = None
    """工具输出"""

    duration_ms: float = 0.0
    """执行耗时（毫秒）"""


@dataclass
class ToolFailedEvent(BaseEvent):
    """工具执行失败"""

    agent_id: str = ""
    """调用工具的 Agent ID"""

    tool_name: str = ""
    """工具名称"""

    error: str = ""
    """错误信息"""


# === LLM 事件 ===


@dataclass
class LLMCallStartedEvent(BaseEvent):
    """LLM 调用开始"""

    agent_id: str = ""
    """Agent ID"""

    model: str = ""
    """模型名称"""

    message_count: int = 0
    """消息数量"""


@dataclass
class LLMCallCompletedEvent(BaseEvent):
    """LLM 调用完成"""

    agent_id: str = ""
    """Agent ID"""

    model: str = ""
    """模型名称"""

    input_tokens: int = 0
    """输入 token 数"""

    output_tokens: int = 0
    """输出 token 数"""

    duration_ms: float = 0.0
    """耗时（毫秒）"""


@dataclass
class LLMStreamChunkEvent(BaseEvent):
    """LLM 流式输出块"""

    agent_id: str = ""
    """Agent ID"""

    chunk: str = ""
    """输出块内容"""

    is_final: bool = False
    """是否是最后一块"""


# === 委派事件 ===


@dataclass
class DelegationStartedEvent(BaseEvent):
    """委派开始"""

    from_agent_id: str = ""
    """源 Agent ID"""

    to_agent_id: str = ""
    """目标 Agent ID"""

    task: str = ""
    """委派任务"""

    is_a2a: bool = False
    """是否是 A2A 远程委派"""


@dataclass
class DelegationCompletedEvent(BaseEvent):
    """委派完成"""

    from_agent_id: str = ""
    """源 Agent ID"""

    to_agent_id: str = ""
    """目标 Agent ID"""

    result: Any = None
    """委派结果"""

    duration_ms: float = 0.0
    """耗时（毫秒）"""


# === 系统事件 ===


@dataclass
class SessionStartedEvent(BaseEvent):
    """会话开始"""

    session_id: str = ""
    """会话 ID"""

    user_id: str = ""
    """用户 ID"""

    query: str = ""
    """初始查询"""


@dataclass
class SessionCompletedEvent(BaseEvent):
    """会话完成"""

    session_id: str = ""
    """会话 ID"""

    user_id: str = ""
    """用户 ID"""

    result: Any = None
    """最终结果"""

    duration_ms: float = 0.0
    """总耗时（毫秒）"""

    agent_count: int = 0
    """参与的 Agent 数量"""

    tool_count: int = 0
    """工具调用次数"""
