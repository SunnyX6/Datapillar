"""
Context 模块 - 统一的上下文管理

提供：
- ContextBuilder: 统一的上下文管理器（管理 messages、timeline、压缩）
- Timeline: 执行时间线
- Compaction: 上下文压缩
- Knowledge: 知识注册

设计原则：
- 所有上下文操作通过 ContextBuilder
- messages 是 LangGraph 的短期记忆
- Timeline 记录执行历史
- 压缩在 ContextBuilder 中自动触发
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
from datapillar_oneagentic.context.compaction import (
    CompactPolicy,
    CompactResult,
    Compactor,
    get_compactor,
    clear_compactor_cache,
)
from datapillar_oneagentic.context.knowledge import (
    KnowledgeRegistry,
    KnowledgeDomain,
    KnowledgeLevel,
    AgentKnowledgeContribution,
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
    # 压缩
    "CompactPolicy",
    "CompactResult",
    "Compactor",
    "get_compactor",
    "clear_compactor_cache",
    # 知识
    "KnowledgeRegistry",
    "KnowledgeDomain",
    "KnowledgeLevel",
    "AgentKnowledgeContribution",
]
