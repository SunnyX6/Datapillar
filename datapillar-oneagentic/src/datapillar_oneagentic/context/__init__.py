"""
Context 模块 - 统一的上下文管理

提供：
- ContextBuilder: 统一的上下文构建器（协调 memory 和 timeline）
- EventType: 统一的事件类型
- Timeline: 执行时间线
- CheckpointManager: 检查点管理
- Memory: 会话记忆（重导出）

设计原则：
- session_id, team_id, user_id 由 Blackboard 管理（单一来源）
- memory 和 timeline 在 Blackboard 中独立存储
- ContextBuilder 从 Blackboard 恢复，提供统一操作 API
"""

from datapillar_oneagentic.context.types import (
    EventType,
    EventLevel,
    AgentStatus,
    CheckpointType,
)
from datapillar_oneagentic.context.builder import ContextBuilder
from datapillar_oneagentic.context.timeline import (
    Timeline,
    TimelineEntry,
    TimeTravelRequest,
    TimeTravelResult,
)
from datapillar_oneagentic.context.checkpoint import CheckpointManager
from datapillar_oneagentic.context.memory import (
    SessionMemory,
    ConversationEntry,
    ConversationMemory,
    EntryType,
    PinnedContext,
    Decision,
    ArtifactRef,
    CompactPolicy,
    CompactResult,
    Compactor,
    get_compactor,
    clear_compactor_cache,
    EntryCategory,
)

__all__ = [
    # 核心
    "ContextBuilder",
    # 类型
    "EventType",
    "EventLevel",
    "AgentStatus",
    "CheckpointType",
    # 时间线
    "Timeline",
    "TimelineEntry",
    "TimeTravelRequest",
    "TimeTravelResult",
    # 检查点
    "CheckpointManager",
    # 记忆
    "SessionMemory",
    "ConversationEntry",
    "ConversationMemory",
    "EntryType",
    "PinnedContext",
    "Decision",
    "ArtifactRef",
    # 压缩
    "CompactPolicy",
    "CompactResult",
    "Compactor",
    "get_compactor",
    "clear_compactor_cache",
    "EntryCategory",
]
