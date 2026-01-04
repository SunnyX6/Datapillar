"""
ETL 多智能体系统

智能体列表：
- KnowledgeAgent: 知识检索专家（LLM + Tools）
- AnalystAgent: 需求分析师（需求收敛 + 表存在性验证）
- ArchitectAgent: 数据架构师（Job/Stage 规划 + 组件选择）
- DeveloperAgent: 数据开发（SQL 生成 + 血缘参考）
- TesterAgent: 测试验证（语法检查 + 列名验证 + 方言校验）

设计原则：
- 所有 Agent 通过 KnowledgeAgent.query_pointers() 按需获取指针
- 工具调用前必须校验 pointer.tools 权限
"""

from src.modules.etl.agents.analyst_agent import AnalystAgent
from src.modules.etl.agents.architect_agent import ArchitectAgent
from src.modules.etl.agents.developer_agent import DeveloperAgent
from src.modules.etl.agents.knowledge_agent import KnowledgeAgent
from src.modules.etl.agents.tester_agent import TesterAgent

__all__ = [
    "KnowledgeAgent",
    "AnalystAgent",
    "ArchitectAgent",
    "DeveloperAgent",
    "TesterAgent",
]
