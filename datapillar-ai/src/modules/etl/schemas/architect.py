# @author Sunny
# @date 2026-01-27

"""
Architect data structure

ArchitectAgent product of：ArchitectOutput
"""

import json

from pydantic import BaseModel, Field, field_validator

from src.modules.etl.schemas.workflow import StageOutput


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


class ArchitectJob(BaseModel):
    """Job design output（Aligned with needs analysis，Only supplement stages）"""

    job_id: str = Field(..., description="Job unique identifier（Need to bring pipeline prefix）")
    job_name: str = Field(..., description="Job Name，Briefly describe the step")
    description: str = Field(..., description="Job Detailed description")
    source_tables: list[str] = Field(default_factory=list, description="Input table list")
    target_table: str = Field(..., description="Output target table")
    depends_on: list[str] = Field(
        default_factory=list, description="Same pipeline within the upstream Job Depend on"
    )
    stages: list[StageOutput] = Field(
        default_factory=list, description="Job internal execution phase list"
    )

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

    @field_validator("stages", mode="before")
    @classmethod
    def _parse_stages(cls, v: object) -> object:
        return _try_parse_json(v)


class ArchitectPipeline(BaseModel):
    """Pipeline design output（Consistent with needs analysis）"""

    pipeline_id: str = Field(..., description="Pipeline unique identifier")
    pipeline_name: str = Field(..., description="Pipeline Name（Keep needs analysis consistent）")
    schedule: str | None = Field(
        ..., description="Scheduling cycle cron expression（Keep needs analysis consistent）"
    )
    jobs: list[ArchitectJob] = Field(default_factory=list, description="Job list")
    depends_on_pipelines: list[str] = Field(
        default_factory=list, description="Dependent upstream Pipeline ID list"
    )
    confidence: float = Field(
        default=0.8, ge=0.0, le=1.0, description="the Pipeline confidence level"
    )

    @field_validator("jobs", mode="before")
    @classmethod
    def _parse_jobs(cls, v: object) -> object:
        return _try_parse_json(v)

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


class ArchitectOutput(BaseModel):
    """Architecture design output（pipeline Level）"""

    summary: str = Field(..., description="One sentence summary of the overall plan")
    pipelines: list[ArchitectPipeline] = Field(default_factory=list, description="Pipeline list")

    @field_validator("pipelines", mode="before")
    @classmethod
    def _parse_pipelines(cls, v: object) -> object:
        return _try_parse_json(v)
