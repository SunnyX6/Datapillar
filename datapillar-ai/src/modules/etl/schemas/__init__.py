"""
ETL 多智能体系统数据结构
"""

from src.modules.etl.schemas.kg_context import (
    KnowledgeContext,
    TableSchema,
    ColumnInfo,
    TableLineage,
    JoinHint,
    BusinessContext,
)
from src.modules.etl.schemas.requirement import (
    AnalysisResult,
    Step,
    Stage,
    DataTarget,
    Ambiguity,
)
from src.modules.etl.schemas.plan import (
    Workflow,
    Job,
    ReviewResult,
    TestResult,
)
from src.modules.etl.schemas.dag import (
    ReactFlowDag,
    DagNode,
    DagEdge,
    DagMetadata,
    NodeData,
    NodePosition,
    workflow_to_react_flow,
)
from src.modules.etl.schemas.state import AgentState

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
    "workflow_to_react_flow",
    # State
    "AgentState",
]
