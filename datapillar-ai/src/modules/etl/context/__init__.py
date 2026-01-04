"""
上下文（Context）

核心模块：
- Handover: 员工交接物存储
- AgentPrivate: 员工私有存储
- ContextBuilder: 构建 Agent 执行时的上下文

压缩模块：
- 压缩相关功能通过 `from src.modules.etl.context.compress import xxx` 导入
"""

# 核心类
from src.modules.etl.context.agent_private import AgentPrivate, ConversationTurn
from src.modules.etl.context.builder import ContextBuildConfig, ContextBuilder

# 压缩模块常用类型（简化导入）
from src.modules.etl.context.compress.budget import (
    CompressionReason,
    CompressionScope,
    ContextBudget,
    decide_compression_trigger,
    get_default_budget,
)
from src.modules.etl.context.compress.clip import clip_payload
from src.modules.etl.context.handover import DeliverableRef, Handover
from src.modules.etl.context.layers import (
    AgentContext,
    ArtifactContext,
    ConversationContext,
    KnowledgeContext,
    TaskContext,
)

__all__ = [
    # 核心类
    "AgentContext",
    "AgentPrivate",
    "ArtifactContext",
    "ContextBuildConfig",
    "ContextBuilder",
    "ConversationContext",
    "ConversationTurn",
    "DeliverableRef",
    "Handover",
    "KnowledgeContext",
    "TaskContext",
    # 压缩常用
    "ContextBudget",
    "clip_payload",
    "decide_compression_trigger",
    "get_default_budget",
    "CompressionReason",
    "CompressionScope",
]
