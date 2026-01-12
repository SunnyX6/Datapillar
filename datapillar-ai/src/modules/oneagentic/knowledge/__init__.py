"""
Knowledge 模块 - 知识仓库

知识分层：
- L1: 公司知识（Company Knowledge）- 永久存在，方法论、规范、流程
- L2: 领域知识（Domain Knowledge）- 领域存在则存在，专业知识、最佳实践
- L3: Agent 知识（Agent Knowledge）- 与 Agent 生命周期绑定

核心原则：
- 知识是基础设施，不是 Agent
- 知识不进 Blackboard（按需注入 Context）
- 知识只注入 Context.knowledge_payload（按需、隔离）
- 元数据通过工具获取（不存静态数据）

使用示例：
```python
from src.modules.oneagentic.knowledge import KnowledgeStore, KnowledgeDomain, KnowledgeLevel

# 注册公司知识
KnowledgeStore.register_domain(KnowledgeDomain(
    domain_id="etl_methodology",
    name="ETL 方法论",
    level=KnowledgeLevel.COMPANY,
    content="...",
))

# Agent 声明知识需求
@agent(
    ...,
    knowledge_domains=["etl_methodology"],
)
class MyAgent:
    ...

# 获取知识
knowledge = KnowledgeStore.get_knowledge(
    domains=["etl_methodology"],
    agent_id="my_agent",
)
```
"""

from src.modules.oneagentic.knowledge.domain import (
    AgentKnowledgeContribution,
    KnowledgeDomain,
    KnowledgeLevel,
)
from src.modules.oneagentic.knowledge.store import KnowledgeStore

__all__ = [
    "KnowledgeDomain",
    "KnowledgeLevel",
    "AgentKnowledgeContribution",
    "KnowledgeStore",
]
