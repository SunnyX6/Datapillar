"""
ETL 多智能体系统

架构设计：
- Orchestrator: 指挥官，协调各 Agent 协作
- Knowledge Agent: 知识检索专家，从 Neo4j 构建上下文
- Analyst Agent: 需求分析师，理解用户意图
- Architect Agent: 数据架构师，设计技术方案
- Developer Agent: 数据开发，生成可执行代码
- Reviewer Agent: 方案评审，检查方案合理性
- Tester Agent: 测试验证，验证生成的代码
- Memory: 记忆模块，管理知识缓存和历史案例
"""

from src.agent.etl_agents.orchestrator import EtlOrchestrator

__all__ = ["EtlOrchestrator"]
