"""
Context module - unified context management.

Provides:
- ContextBuilder: builds LLM messages (read-only state, no Blackboard writes)
- ContextCollector: runtime _context collector
- ContextComposer: pure functional message composer
- Timeline: execution timeline
- Compaction: context compaction (triggered by LLM context overflow)

Design principles:
- Blackboard state read/write is handled by state/StateBuilder
- messages are LangGraph short-term memory
- Timeline records execution history
- Compaction is triggered by LLM context overflow
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
    # Core
    "ContextBuilder",
    "ContextCollector",
    "ContextComposer",
    "ContextScenario",
    # Timeline
    "Timeline",
    "TimelineEntry",
    "TimeTravelRequest",
    "TimeTravelResult",
    # Checkpoint
    "CheckpointManager",
    # Compaction
    "CompactPolicy",
    "CompactResult",
    "Compactor",
    "get_compactor",
]
