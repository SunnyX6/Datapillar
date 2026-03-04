# @author Sunny
# @date 2026-01-27

"""
Requirements analysis data structure

AnalystAgent Responsibilities are Business Analysis:- Split user needs into multiple items pipeline
- each pipeline by multiple Job composition,each Job clear source_tables/target_table
- only care"what to do",dont care"how to do"
- Not involved SQL,Stage and other technical details
"""

import json
from typing import Literal

from pydantic import BaseModel, Field, field_validator


def _try_parse_json(value: object) -> object:
    if not isinstance(value, str):
        return value
    text = value.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return value


class AmbiguityItem(BaseModel):
    """
    structured clarifying items

    Description:- table problem:candidates in must be catalog.schema.table
    - Scheduling cycle problem:candidates is optional cron list(Can be null)
    """

    type: Literal["table", "schedule"] = Field(..., description="Question type:table/schedule")
    question: str = Field(..., description="Questions that need clarification")
    candidates: list[str] = Field(default_factory=list, description="Candidate list")

    @field_validator("candidates", mode="before")
    @classmethod
    def _parse_candidates(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


class PipelineJob(BaseModel):
    """
    PipelineJob - business steps

    business level concepts,express pipeline a logical step within.each Job Support multiple source_tables,only one target_table.
    """

    job_id: str = Field(..., description="Job unique identifier(Need to bring pipeline prefix)")
    job_name: str = Field(..., description="Job Name")
    description: str = Field(..., description="this Job what to do(Business description)")
    source_tables: list[str] = Field(
        default_factory=list, description="Source table list(catalog.schema.table)"
    )
    target_table: str | None = Field(
        None, description="target table(Single table,catalog.schema.table)"
    )
    depends_on: list[str] = Field(default_factory=list, description="Dependent upstream Job ID")

    @field_validator("source_tables", "depends_on", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


class Pipeline(BaseModel):
    """
    Pipeline - business line/workflow route

    one piece pipeline Corresponds to a business line,through multiple Job Organize multi-source and multi-destination paths.
    """

    pipeline_id: str = Field(..., description="Pipeline unique identifier")
    pipeline_name: str = Field(..., description="Pipeline Name(by LLM generate)")
    schedule: str | None = Field(
        ..., description="Scheduling cycle cron expression,If not specified,it is null"
    )
    jobs: list[PipelineJob] = Field(default_factory=list, description="Job list")
    depends_on_pipelines: list[str] = Field(
        default_factory=list, description="Dependent upstream Pipeline ID list"
    )
    ambiguities: list[AmbiguityItem] = Field(
        default_factory=list,
        description="the Pipeline Structured list of questions requiring clarification",
    )
    confidence: float = Field(
        default=0.5, ge=0.0, le=1.0, description="the Pipeline degree of clarity of needs"
    )

    @field_validator("depends_on_pipelines", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v

    @field_validator("ambiguities", mode="before")
    @classmethod
    def _parse_ambiguities(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, list):
            parsed = []
            for item in v:
                parsed.append(_try_parse_json(item))
            return parsed
        if isinstance(v, dict):
            return [v]
        return v

    @field_validator("jobs", mode="before")
    @classmethod
    def _parse_jobs(cls, v: object) -> object:
        return _try_parse_json(v)


class AnalysisResultOutput(BaseModel):
    """
    LLM Output requirements analysis results(used for structured output)

    Does not contain user_query,Because thats what the code passed in."""

    summary: str = Field(
        ..., description="Summarize user needs in one sentence(Must be specific,Cant be blurry)"
    )
    pipelines: list[Pipeline] = Field(default_factory=list, description="Pipeline list")

    @field_validator("pipelines", mode="before")
    @classmethod
    def _parse_pipelines(cls, v: object) -> object:
        return _try_parse_json(v)


class AnalysisResult(BaseModel):
    """
    Requirements analysis results(AnalystAgent output)

    Business level analysis:- Split user needs into multiple items pipeline
    - each pipeline by multiple Job composition,each Job clear source_tables/target_table
    - Mark ambiguous points

    Does not involve technical implementation(Job,Stage,SQL),Thats the architects job."""

    user_query: str = Field(..., description="original user input")
    summary: str = Field(..., description="Summarize user needs in one sentence")

    pipelines: list[Pipeline] = Field(default_factory=list, description="Pipeline list")

    @classmethod
    def from_output(cls, output: AnalysisResultOutput, user_query: str) -> "AnalysisResult":
        """from LLM Output builds complete analysis results"""
        return cls(
            user_query=user_query,
            summary=output.summary,
            pipelines=output.pipelines,
        )

    def needs_clarification(self) -> bool:
        """Does the user need clarification?"""
        for pipeline in self.pipelines:
            if pipeline.ambiguities:
                return True
            if pipeline.confidence < 0.7:
                return True
        return False

    def get_all_tables(self) -> list[str]:
        """Get all involved tables"""
        tables = set()
        for pipeline in self.pipelines:
            for job in pipeline.jobs:
                for table in job.source_tables:
                    if table:
                        tables.add(table)
                if job.target_table:
                    tables.add(job.target_table)
        return list(tables)

    def job_by_id(self, job_id: str) -> PipelineJob | None:
        """According to ID Get Job"""
        for pipeline in self.pipelines:
            for job in pipeline.jobs:
                if job.job_id == job_id:
                    return job
        return None

    def plan_summary(self) -> str:
        """Get execution plan summary"""
        lines = []
        for pipeline in self.pipelines:
            deps = (
                f" (Depend on pipeline:{','.join(pipeline.depends_on_pipelines)})"
                if pipeline.depends_on_pipelines
                else ""
            )
            lines.append(f"[Pipeline {pipeline.pipeline_id}] {pipeline.pipeline_name}{deps}")
            for job in pipeline.jobs:
                job_deps = f" (Depend on:{','.join(job.depends_on)})" if job.depends_on else ""
                source_label = ", ".join(job.source_tables) if job.source_tables else "unspecified"
                target_label = job.target_table or "unspecified"
                lines.append(f"  [{job.job_name}] {source_label} → {target_label}{job_deps}")
                lines.append(f"    {job.description}")
        return "\n".join(lines)
