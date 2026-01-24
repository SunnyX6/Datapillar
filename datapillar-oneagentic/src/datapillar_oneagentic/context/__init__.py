"""
Context 模块 - 统一的上下文管理

提供：
- ContextBuilder: LLM messages 构建器（只读 state，不负责 Blackboard 写入）
- ContextCollector: 运行态 __context 收集器
- ContextComposer: 纯函数消息组装器
- Timeline: 执行时间线
- Compaction: 上下文压缩（由 LLM 上下文超限触发）

设计原则：
- Blackboard 状态读写由 state/StateBuilder 统一负责
- messages 是 LangGraph 的短期记忆
- Timeline 记录执行历史
- 压缩由 LLM 上下文超限触发
"""

from datapillar_oneagentic.context.builder import (
    ContextBuilder,
    ContextCollector,
    ContextComposer,
    ContextScenario,
)
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
    "ContextCollector",
    "ContextComposer",
    "ContextScenario",
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
