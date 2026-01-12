"""
Memory 模块

记忆系统：
- SessionMemory: 会话记忆（统一入口）
- PinnedContext: 固定上下文（不压缩的结构化信息）
- ConversationMemory: 对话历史
- CompactPolicy: 压缩策略
- Compactor: 压缩器
"""

from src.modules.oneagentic.memory.compact_policy import (
    CompactPolicy,
    CompactResult,
)
from src.modules.oneagentic.memory.compactor import (
    Compactor,
    clear_compactor_cache,
    get_compactor,
)
from src.modules.oneagentic.memory.conversation import (
    ConversationEntry,
    ConversationMemory,
)
from src.modules.oneagentic.memory.pinned_context import (
    ArtifactRef,
    Decision,
    PinnedContext,
)
from src.modules.oneagentic.memory.session_memory import (
    SessionMemory,
)

__all__ = [
    # 统一入口
    "SessionMemory",
    # 固定上下文
    "PinnedContext",
    "Decision",
    "ArtifactRef",
    # 对话历史
    "ConversationMemory",
    "ConversationEntry",
    # 压缩
    "CompactPolicy",
    "CompactResult",
    "Compactor",
    "get_compactor",
    "clear_compactor_cache",
]
