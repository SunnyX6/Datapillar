"""
Context 模块 - 统一的上下文管理

提供：
- ContextBuilder: 统一的上下文管理器（管理 messages、timeline、压缩）
- Timeline: 执行时间线
- Compaction: 上下文压缩（由 LLM 上下文超限触发）

设计原则：
- 所有上下文操作通过 ContextBuilder
- messages 是 LangGraph 的短期记忆
- Timeline 记录执行历史
- 压缩由 LLM 上下文超限触发
"""

from datapillar_oneagentic.context.builder import ContextBuilder
from datapillar_oneagentic.context.checkpoint import CheckpointManager
from datapillar_oneagentic.context.compaction import (
    Compactor,
    CompactPolicy,
    CompactResult,
    get_compactor,
)
from datapillar_oneagentic.context.timeline import (
    Timeline,
    TimelineEntry,
    TimeTravelRequest,
    TimeTravelResult,
)

__all__ = [
    # 核心
    "ContextBuilder",
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
]
