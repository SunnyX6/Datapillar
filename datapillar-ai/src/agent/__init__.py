"""
Agent模块 - Multi-Agent工作流编排

新架构（v5.0 - 仅 ETL 工作流）：
- WorkflowOrchestrator: 动态编排 PlannerAgent、CoderAgent
- PlannerAgent: 理解需求、生成执行计划（ReactFlow JSON）
- CoderAgent: 填充配置 Slot
"""

# 新的Multi-Agent公司架构
from src.agent.orchestrator import Orchestrator, create_orchestrator
from src.agent.state import OrchestratorState
from src.agent.planner_agent import PlannerAgent
from src.agent.coder_agent import CoderAgent

# 工具和辅助模块
from src.tools import AgentTools

__all__ = [
    "Orchestrator",
    "create_orchestrator",
    "OrchestratorState",
    "PlannerAgent",
    "CoderAgent",
    # 工具
    "AgentTools",
]
