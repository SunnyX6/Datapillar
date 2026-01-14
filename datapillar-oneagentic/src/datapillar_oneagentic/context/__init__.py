"""
Context 模块 - 统一的上下文管理

提供：
- ContextBuilder: 统一的上下文管理器（管理 messages、timeline、压缩）
- Timeline: 执行时间线
- Compaction: 上下文压缩（由 ContextLengthExceededError 触发）
- Knowledge: 知识注册

设计原则：
- 所有上下文操作通过 ContextBuilder
- messages 是 LangGraph 的短期记忆
- Timeline 记录执行历史
- 压缩由 LLM 上下文超限异常触发
"""

from datapillar_oneagentic.context.builder import ContextBuilder
from datapillar_oneagentic.context.checkpoint import CheckpointManager
from datapillar_oneagentic.context.compaction import (
    Compactor,
    CompactPolicy,
    CompactResult,
    clear_compactor_cache,
    get_compactor,
)
from datapillar_oneagentic.context.knowledge import (
    AgentKnowledgeContribution,
    KnowledgeDomain,
    KnowledgeLevel,
    KnowledgeRegistry,
)
from datapillar_oneagentic.context.timeline import (
    Timeline,
    TimelineEntry,
    TimeTravelRequest,
    TimeTravelResult,
)
from datapillar_oneagentic.context.types import (
    AgentStatus,
    CheckpointType,
    EventLevel,
    EventType,
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
