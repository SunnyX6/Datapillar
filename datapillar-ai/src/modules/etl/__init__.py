"""
ETL 模块 - 智能 ETL 工作流生成

功能：
- 多智能体协作生成 ETL 工作流
- 需求分析、架构设计、SQL 开发、测试验证
- 知识图谱上下文增强

API 端点：
- POST /api/agent/etl/generate - 生成 ETL 工作流
- GET /api/agent/etl/status - 获取生成状态

智能体角色：
- Knowledge: 知识图谱检索
- Analyst: 需求分析师
- Architect: 架构设计师
- Developer: SQL 开发者
- Tester: 测试工程师
"""

from src.modules.etl.api import router
from src.modules.etl.orchestrator import EtlOrchestrator, create_etl_orchestrator

__all__ = [
    "router",
    "EtlOrchestrator",
    "create_etl_orchestrator",
]
