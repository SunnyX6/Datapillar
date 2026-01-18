"""
检查点类型定义
"""

from __future__ import annotations

from enum import Enum


class CheckpointType(str, Enum):
    """检查点类型"""

    AUTO = "auto"  # 自动创建
    MANUAL = "manual"  # 手动创建
    AGENT_END = "agent_end"  # Agent 执行结束
    USER_MESSAGE = "user_message"  # 用户消息后
    ERROR_RECOVERY = "error_recovery"  # 错误恢复点
