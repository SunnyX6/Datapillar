"""
ETL 多智能体系统数据结构
"""

from src.agent.etl_agents.schemas.context import (
    KnowledgeContext,
    TableSchema,
    ColumnInfo,
    TableLineage,
    JoinHint,
    BusinessContext,
)
from src.agent.etl_agents.schemas.requirement import (
    AnalysisResult,
    Step,
    Stage,
    DataTarget,
    Ambiguity,
)
from src.agent.etl_agents.schemas.plan import (
    Workflow,
    Job,
    ReviewResult,
    TestResult,
)
from src.agent.etl_agents.schemas.dag import (
    ReactFlowDag,
    DagNode,
    DagEdge,
    DagMetadata,
    NodeData,
    NodePosition,
    plan_to_dag,
)
from src.agent.etl_agents.schemas.state import AgentState

__all__ = [
    # Context
    "KnowledgeContext",
    "TableSchema",
    "ColumnInfo",
    "TableLineage",
    "JoinHint",
    "BusinessContext",
    # Requirement
    "AnalysisResult",
    "Step",
    "Stage",
    "DataTarget",
    "Ambiguity",
    # Plan
    "Workflow",
    "Job",
    "ReviewResult",
    "TestResult",
    # DAG
    "ReactFlowDag",
    "DagNode",
    "DagEdge",
    "DagMetadata",
    "NodeData",
    "NodePosition",
    "plan_to_dag",
    # State
    "AgentState",
]
