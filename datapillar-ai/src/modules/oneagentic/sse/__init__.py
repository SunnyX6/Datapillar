"""
SSE 模块

提供 SSE 事件定义和流管理器。

核心类：
- SseEvent: SSE 事件定义
- StreamManager: SSE 流管理器
"""

from src.modules.oneagentic.sse.event import (
    SseAgent,
    SseError,
    SseEvent,
    SseEventType,
    SseInterrupt,
    SseLevel,
    SseLlm,
    SseMessage,
    SseResult,
    SseSpan,
    SseState,
    SseTool,
)
from src.modules.oneagentic.sse.manager import StreamManager

__all__ = [
    # 事件
    "SseEvent",
    "SseEventType",
    "SseState",
    "SseLevel",
    # 子结构
    "SseAgent",
    "SseSpan",
    "SseMessage",
    "SseTool",
    "SseLlm",
    "SseInterrupt",
    "SseResult",
    "SseError",
    # 管理器
    "StreamManager",
]
