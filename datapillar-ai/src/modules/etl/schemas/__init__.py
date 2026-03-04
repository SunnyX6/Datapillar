# @author Sunny
# @date 2026-01-27

"""
ETL Agent output definition

Agent The output is Agent result of work,Pass AgentResult.deliverable delivery.Defined output:- CatalogResult:Metadata Q&A results
- AnalysisResult:Requirements analysis results
- ArchitectOutput:Architectural design results(pipeline level)
- Workflow:Workflow definition(The development phase may include SQL)
- ReviewResult:Review results
"""

from src.modules.etl.schemas.analyst import (
    AmbiguityItem,
    AnalysisResult,
    AnalysisResultOutput,
    Pipeline,
    PipelineJob,
)
from src.modules.etl.schemas.architect import (
    ArchitectJob,
    ArchitectOutput,
    ArchitectPipeline,
)
from src.modules.etl.schemas.catalog import (
    CatalogResult,
    CatalogResultOutput,
    OptionItem,
)
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import (
    Job,
    Stage,
    Workflow,
    WorkflowOutput,
)

__all__ = [  # CatalogAgent output
    "CatalogResult",
    "CatalogResultOutput",
    "OptionItem",  # AnalystAgent output
    "AmbiguityItem",
    "AnalysisResult",
    "AnalysisResultOutput",
    "Pipeline",
    "PipelineJob",  # ArchitectAgent output
    "ArchitectOutput",
    "ArchitectPipeline",
    "ArchitectJob",  # DeveloperAgent output
    "Workflow",
    "WorkflowOutput",
    "Job",
    "Stage",  # ReviewerAgent output
    "ReviewResult",
]
