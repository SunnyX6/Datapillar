# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
需求分析数据结构

AnalystAgent 的职责是业务分析：
- 将用户需求拆分为多条 pipeline
- 每条 pipeline 由多个 Job 组成，每个 Job 明确 source_tables/target_table
- 只关心"做什么"，不关心"怎么做"
- 不涉及 SQL、Stage 等技术细节
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
    结构化澄清项

    说明：
    - 表问题：candidates 中必须是 catalog.schema.table
    - 调度周期问题：candidates 是可选 cron 列表（可为空）
    """

    type: Literal["table", "schedule"] = Field(..., description="问题类型：table/schedule")
    question: str = Field(..., description="需要澄清的问题")
    candidates: list[str] = Field(default_factory=list, description="候选项列表")

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
    PipelineJob - 业务步骤

    业务层面的概念，表示 pipeline 内的一个逻辑步骤。
    每个 Job 支持多个 source_tables，只有一个 target_table。
    """

    job_id: str = Field(..., description="Job 唯一标识（需带 pipeline 前缀）")
    job_name: str = Field(..., description="Job 名称")
    description: str = Field(..., description="这个 Job 做什么（业务描述）")
    source_tables: list[str] = Field(default_factory=list, description="源表列表（catalog.schema.table）")
    target_table: str | None = Field(None, description="目标表（单表，catalog.schema.table）")
    depends_on: list[str] = Field(default_factory=list, description="依赖的上游 Job ID")

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
    Pipeline - 业务线/工作流路线

    一条 pipeline 对应一条业务线，通过多个 Job 组织多源多目标路径。
    """

    pipeline_id: str = Field(..., description="Pipeline 唯一标识")
    pipeline_name: str = Field(..., description="Pipeline 名称（由 LLM 生成）")
    schedule: str | None = Field(..., description="调度周期 cron 表达式，未明确则为 null")
    jobs: list[PipelineJob] = Field(default_factory=list, description="Job 列表")
    depends_on_pipelines: list[str] = Field(
        default_factory=list, description="依赖的上游 Pipeline ID 列表"
    )
    ambiguities: list[AmbiguityItem] = Field(
        default_factory=list,
        description="该 Pipeline 需要澄清的结构化问题列表",
    )
    confidence: float = Field(
        default=0.5, ge=0.0, le=1.0, description="该 Pipeline 的需求明确程度"
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
    LLM 输出的需求分析结果（用于 structured output）

    不含 user_query，因为那是代码传入的。
    """

    summary: str = Field(..., description="一句话概括用户需求（必须具体，不能模糊）")
    pipelines: list[Pipeline] = Field(default_factory=list, description="Pipeline 列表")

    @field_validator("pipelines", mode="before")
    @classmethod
    def _parse_pipelines(cls, v: object) -> object:
        return _try_parse_json(v)

class AnalysisResult(BaseModel):
    """
    需求分析结果（AnalystAgent 输出）

    业务层面的分析：
    - 将用户需求拆分为多条 pipeline
    - 每条 pipeline 由多个 Job 组成，每个 Job 明确 source_tables/target_table
    - 标注歧义点

    不涉及技术实现（Job、Stage、SQL），那是架构师的职责。
    """

    user_query: str = Field(..., description="用户原始输入")
    summary: str = Field(..., description="一句话概括用户需求")

    pipelines: list[Pipeline] = Field(default_factory=list, description="Pipeline 列表")

    @classmethod
    def from_output(cls, output: AnalysisResultOutput, user_query: str) -> "AnalysisResult":
        """从 LLM 输出构建完整的分析结果"""
        return cls(
            user_query=user_query,
            summary=output.summary,
            pipelines=output.pipelines,
        )

    def needs_clarification(self) -> bool:
        """是否需要用户澄清"""
        for pipeline in self.pipelines:
            if pipeline.ambiguities:
                return True
            if pipeline.confidence < 0.7:
                return True
        return False

    def get_all_tables(self) -> list[str]:
        """获取所有涉及的表"""
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
        """根据 ID 获取 Job"""
        for pipeline in self.pipelines:
            for job in pipeline.jobs:
                if job.job_id == job_id:
                    return job
        return None

    def plan_summary(self) -> str:
        """获取执行计划摘要"""
        lines = []
        for pipeline in self.pipelines:
            deps = (
                f" (依赖 pipeline: {', '.join(pipeline.depends_on_pipelines)})"
                if pipeline.depends_on_pipelines
                else ""
            )
            lines.append(f"[Pipeline {pipeline.pipeline_id}] {pipeline.pipeline_name}{deps}")
            for job in pipeline.jobs:
                job_deps = f" (依赖: {', '.join(job.depends_on)})" if job.depends_on else ""
                source_label = ", ".join(job.source_tables) if job.source_tables else "未指定"
                target_label = job.target_table or "未指定"
                lines.append(f"  [{job.job_name}] {source_label} → {target_label}{job_deps}")
                lines.append(f"    {job.description}")
        return "\n".join(lines)
