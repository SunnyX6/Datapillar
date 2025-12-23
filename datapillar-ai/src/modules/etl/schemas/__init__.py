"""
ETL 多智能体系统数据结构
"""

from src.modules.etl.schemas.kg_context import (
    CatalogNav,
    ComponentNav,
    GlobalKGContext,
    LineageEdge,
    SchemaNav,
    TableNav,
    AgentScopedContext,
    AgentType,
    AGENT_TOOLS_MAP,
)
from src.modules.etl.schemas.requirement import (
    AnalysisResult,
    Step,
    DataTarget,
    Ambiguity,
)
from src.modules.etl.schemas.plan import (
    Workflow,
    Job,
    Stage,
    TestResult,
)
from src.modules.etl.schemas.dag import (
    WorkflowResponse,
    JobResponse,
    JobDependencyResponse,
    convert_workflow,
)
from src.modules.etl.schemas.state import AgentState

__all__ = [
    # KG Context - 知识图谱上下文
    "GlobalKGContext",
    "AgentScopedContext",
    "AgentType",
    "AGENT_TOOLS_MAP",
    # Navigation - 导航模型
    "CatalogNav",
    "SchemaNav",
    "TableNav",
    "LineageEdge",
    "ComponentNav",
    # Requirement
    "AnalysisResult",
    "Step",
    "Stage",
    "DataTarget",
    "Ambiguity",
    # Plan
    "Workflow",
    "Job",
    "TestResult",
    # DAG / Workflow Response
    "WorkflowResponse",
    "JobResponse",
    "JobDependencyResponse",
    "convert_workflow",
    # State
    "AgentState",
]
