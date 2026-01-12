"""
ETL Agent 产出定义

Agent 产出是 Agent 工作的结果，通过 AgentResult.deliverable 交付。

已定义产出：
- CatalogResult: 元数据问答结果
- AnalysisResult: 需求分析结果
- Workflow: 工作流定义
- DeveloperSqlOutput: SQL 生成结果
- ReviewResult: 评审结果
"""

from src.modules.etl.schemas.analyst import (
    AnalysisResult,
    AnalysisResultOutput,
    DataTarget,
    Step,
)
from src.modules.etl.schemas.catalog import (
    CatalogResult,
    CatalogResultOutput,
    OptionItem,
)
from src.modules.etl.schemas.developer import DeveloperSqlOutput
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import (
    Job,
    Stage,
    Workflow,
    WorkflowOutput,
)

__all__ = [
    # CatalogAgent 产出
    "CatalogResult",
    "CatalogResultOutput",
    "OptionItem",
    # AnalystAgent 产出
    "AnalysisResult",
    "AnalysisResultOutput",
    "Step",
    "DataTarget",
    # ArchitectAgent 产出
    "Workflow",
    "WorkflowOutput",
    "Job",
    "Stage",
    # DeveloperAgent 产出
    "DeveloperSqlOutput",
    # ReviewerAgent 产出
    "ReviewResult",
]
