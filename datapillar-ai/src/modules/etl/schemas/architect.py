# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Architect 数据结构

ArchitectAgent 的产物：ArchitectOutput
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
    """Job 设计输出（与需求分析保持一致，只补充 stages）"""

    job_id: str = Field(..., description="Job 唯一标识（需带 pipeline 前缀）")
    job_name: str = Field(..., description="Job 名称，简洁描述该步骤")
    description: str = Field(..., description="Job 详细描述")
    source_tables: list[str] = Field(default_factory=list, description="输入表列表")
    target_table: str = Field(..., description="输出目标表")
    depends_on: list[str] = Field(default_factory=list, description="同 pipeline 内的上游 Job 依赖")
    stages: list[StageOutput] = Field(default_factory=list, description="Job 内执行阶段列表")

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
    """Pipeline 设计输出（与需求分析一致）"""

    pipeline_id: str = Field(..., description="Pipeline 唯一标识")
    pipeline_name: str = Field(..., description="Pipeline 名称（保持需求分析一致）")
    schedule: str | None = Field(..., description="调度周期 cron 表达式（保持需求分析一致）")
    jobs: list[ArchitectJob] = Field(default_factory=list, description="Job 列表")
    depends_on_pipelines: list[str] = Field(
        default_factory=list, description="依赖的上游 Pipeline ID 列表"
    )
    confidence: float = Field(
        default=0.8, ge=0.0, le=1.0, description="该 Pipeline 的置信度"
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
    """架构设计输出（pipeline 级别）"""

    summary: str = Field(..., description="整体方案一句话概括")
    pipelines: list[ArchitectPipeline] = Field(default_factory=list, description="Pipeline 列表")

    @field_validator("pipelines", mode="before")
    @classmethod
    def _parse_pipelines(cls, v: object) -> object:
        return _try_parse_json(v)
