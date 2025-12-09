"""
Agent模块 - Multi-Agent工作流编排

架构：
- Orchestrator: 动态编排 PlannerAgent、CoderAgent
- PlannerAgent: 理解需求、生成执行计划
- CoderAgent: 填充配置 Slot
- tools/: Agent 工具集
- assembler/: 工作流组装器
"""

from src.agent.orchestrator import Orchestrator, create_orchestrator
from src.agent.state import OrchestratorState
from src.agent.planner_agent import PlannerAgent
from src.agent.coder_agent import CoderAgent

__all__ = [
    "Orchestrator",
    "create_orchestrator",
    "OrchestratorState",
    "PlannerAgent",
    "CoderAgent",
]
