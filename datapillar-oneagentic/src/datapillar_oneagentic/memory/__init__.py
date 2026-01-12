"""
Memory 模块

会话记忆管理。
"""

from datapillar_oneagentic.memory.compact_policy import (
    CompactPolicy,
    CompactResult,
    EntryCategory,
)
from datapillar_oneagentic.memory.compactor import (
    Compactor,
    get_compactor,
    clear_compactor_cache,
)
from datapillar_oneagentic.memory.conversation import (
    ConversationEntry,
    ConversationMemory,
    EntryType,
)
from datapillar_oneagentic.memory.pinned_context import (
    PinnedContext,
    Decision,
    ArtifactRef,
)
from datapillar_oneagentic.memory.session_memory import (
    SessionMemory,
    PreCompactHook,
)

__all__ = [
    # 核心
    "SessionMemory",
    "PreCompactHook",
    # 压缩
    "CompactPolicy",
    "CompactResult",
    "Compactor",
    "get_compactor",
    "clear_compactor_cache",
    "EntryCategory",
    # 对话
    "ConversationEntry",
    "ConversationMemory",
    "EntryType",
    # 固定上下文
    "PinnedContext",
    "Decision",
    "ArtifactRef",
]
