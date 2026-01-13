"""
Context Knowledge 子模块

知识注册与管理，包括：
- KnowledgeRegistry: 知识注册表（内存存储）
- KnowledgeDomain: 知识领域定义
- KnowledgeLevel: 知识层级（公司/领域/Agent）

知识分层：
- L1: 公司知识（Company Knowledge）- 永久存在，方法论、规范、流程
- L2: 领域知识（Domain Knowledge）- 领域存在则存在，专业知识、最佳实践
- L3: Agent 知识（Agent Knowledge）- 与 Agent 生命周期绑定

核心原则：
- 知识是基础设施，不是 Agent
- 知识不进 Blackboard（按需注入 Context）
- 知识只注入 Context._knowledge_prompt（按需、隔离）
- 元数据通过工具获取（不存静态数据）
"""

from datapillar_oneagentic.context.knowledge.domain import (
    AgentKnowledgeContribution,
    KnowledgeDomain,
    KnowledgeLevel,
)
from datapillar_oneagentic.context.knowledge.registry import (
    KnowledgeRegistry,
)

__all__ = [
    "KnowledgeDomain",
    "KnowledgeLevel",
    "AgentKnowledgeContribution",
    "KnowledgeRegistry",
]
