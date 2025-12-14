"""
Agent 模块 - ETL 多智能体工作流编排

架构：
- EtlOrchestrator: 智能编排 6 个专业 Agent
- KnowledgeAgent: 知识检索专家（LLM + Tools）
- AnalystAgent: 需求分析师
- ArchitectAgent: 数据架构师
- DeveloperAgent: 数据开发（LLM + 自我修正）
- ReviewerAgent: 方案评审
- TesterAgent: 测试验证

使用方式：
    from src.agent import EtlOrchestrator, create_etl_orchestrator

    orchestrator = await create_etl_orchestrator()
    result = await orchestrator.run(user_input, session_id, user_id)
"""

from src.agent.etl_agents import EtlOrchestrator
from src.agent.etl_agents.orchestrator import create_etl_orchestrator

__all__ = [
    "EtlOrchestrator",
    "create_etl_orchestrator",
]
