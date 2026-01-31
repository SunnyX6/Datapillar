# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
ETL Agent 产出定义

Agent 产出是 Agent 工作的结果，通过 AgentResult.deliverable 交付。

已定义产出：
- CatalogResult: 元数据问答结果
- AnalysisResult: 需求分析结果
- ArchitectOutput: 架构设计结果（pipeline 级）
- Workflow: 工作流定义（开发阶段可包含 SQL）
- ReviewResult: 评审结果
"""

from src.modules.etl.schemas.analyst import (
    AmbiguityItem,
    AnalysisResult,
    AnalysisResultOutput,
    Pipeline,
    PipelineJob,
)
from src.modules.etl.schemas.catalog import (
    CatalogResult,
    CatalogResultOutput,
    OptionItem,
)
from src.modules.etl.schemas.architect import (
    ArchitectJob,
    ArchitectOutput,
    ArchitectPipeline,
)
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
    "AmbiguityItem",
    "AnalysisResult",
    "AnalysisResultOutput",
    "Pipeline",
    "PipelineJob",
    # ArchitectAgent 产出
    "ArchitectOutput",
    "ArchitectPipeline",
    "ArchitectJob",
    # DeveloperAgent 产出
    "Workflow",
    "WorkflowOutput",
    "Job",
    "Stage",
    # ReviewerAgent 产出
    "ReviewResult",
]
