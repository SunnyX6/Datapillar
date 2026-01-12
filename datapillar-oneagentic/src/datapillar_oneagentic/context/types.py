"""
Context 模块 - 统一的事件类型定义

所有事件类型的单一来源，供 Timeline、SSE、Events 等模块使用。
"""

from __future__ import annotations

from enum import Enum


class EventType(str, Enum):
    """
    统一的事件类型

    命名规范：<模块>.<动作>
    """

    # === 会话事件 ===
    SESSION_START = "session.start"
    SESSION_END = "session.end"
    SESSION_RESUME = "session.resume"
    SESSION_ABORT = "session.abort"

    # === 用户事件 ===
    USER_MESSAGE = "user.message"
    USER_INTERRUPT = "user.interrupt"
    USER_FEEDBACK = "user.feedback"

    # === Agent 事件 ===
    AGENT_START = "agent.start"
    AGENT_END = "agent.end"
    AGENT_HANDOVER = "agent.handover"
    AGENT_FAILED = "agent.failed"
    AGENT_THINKING = "agent.thinking"

    # === 工具事件 ===
    TOOL_CALL = "tool.call"
    TOOL_RESULT = "tool.result"
    TOOL_ERROR = "tool.error"

    # === LLM 事件 ===
    LLM_START = "llm.start"
    LLM_END = "llm.end"
    LLM_CHUNK = "llm.chunk"

    # === 决策事件 ===
    DECISION = "decision"
    CLARIFICATION = "clarification"
    CONSTRAINT = "constraint"

    # === 记忆事件 ===
    MEMORY_COMPACT = "memory.compact"
    MEMORY_UPDATE = "memory.update"

    # === 检查点事件 ===
    CHECKPOINT_CREATE = "checkpoint.create"
    CHECKPOINT_RESTORE = "checkpoint.restore"

    # === 委派事件 ===
    DELEGATION_START = "delegation.start"
    DELEGATION_END = "delegation.end"

    # === 系统事件 ===
    ERROR = "error"
    RETRY = "retry"
    TIMEOUT = "timeout"

    @classmethod
    def from_string(cls, value: str) -> "EventType":
        """从字符串解析事件类型"""
        for event_type in cls:
            if event_type.value == value:
                return event_type
        raise ValueError(f"未知的事件类型: {value}")

    @property
    def category(self) -> str:
        """获取事件类别"""
        if "." in self.value:
            return self.value.split(".")[0]
        return self.value

    @property
    def action(self) -> str:
        """获取事件动作"""
        if "." in self.value:
            return self.value.split(".")[1]
        return self.value


class EventLevel(str, Enum):
    """事件级别"""

    DEBUG = "debug"
    INFO = "info"
    WARNING = "warning"
    ERROR = "error"
    SUCCESS = "success"


class AgentStatus(str, Enum):
    """Agent 状态"""

    IDLE = "idle"
    THINKING = "thinking"
    INVOKING = "invoking"  # 调用工具
    WAITING = "waiting"  # 等待用户
    DONE = "done"
    FAILED = "failed"
    ABORTED = "aborted"


class CheckpointType(str, Enum):
    """检查点类型"""

    AUTO = "auto"  # 自动创建
    MANUAL = "manual"  # 手动创建
    AGENT_END = "agent_end"  # Agent 执行结束
    USER_MESSAGE = "user_message"  # 用户消息后
    ERROR_RECOVERY = "error_recovery"  # 错误恢复点
