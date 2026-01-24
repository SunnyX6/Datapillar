"""
需求分析数据结构

AnalystAgent 的职责是业务分析：
- 将用户需求拆分为业务步骤（Step）
- 只关心"做什么"，不关心"怎么做"
- 不涉及 SQL、Stage 等技术细节
"""

import json

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


class Step(BaseModel):
    """
    Step - 业务步骤

    业务层面的概念，表示完成需求的一个逻辑步骤。
    不涉及 SQL、Stage 等技术实现细节。
    """

    step_id: str = Field(..., description="步骤唯一标识")
    step_name: str = Field(..., description="步骤名称")
    description: str = Field(..., description="这一步做什么（业务描述）")

    input_tables: list[str] = Field(default_factory=list, description="需要读取的表")
    output_table: str | None = Field(None, description="产出的表")

    depends_on: list[str] = Field(default_factory=list, description="依赖的上游 Step ID")

    @field_validator("input_tables", mode="before")
    @classmethod
    def _parse_input_tables(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v

    @field_validator("depends_on", mode="before")
    @classmethod
    def _parse_depends_on(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


class DataTarget(BaseModel):
    """最终数据目标"""

    table_name: str = Field(..., description="目标表名")
    write_mode: str = Field(default="overwrite", description="写入模式：overwrite/append/upsert")
    partition_by: list[str] = Field(default_factory=list, description="分区字段")
    description: str | None = Field(None, description="目标表描述")

    @field_validator("partition_by", mode="before")
    @classmethod
    def _parse_partition_by(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


class AnalysisResultOutput(BaseModel):
    """
    LLM 输出的需求分析结果（用于 structured output）

    不含 user_query，因为那是代码传入的。
    """

    summary: str = Field(..., description="一句话概括用户需求（必须具体，不能模糊）")
    steps: list[Step] = Field(default_factory=list, description="业务步骤列表")
    final_target: DataTarget | None = Field(None, description="最终数据目标")
    ambiguities: list[str] = Field(default_factory=list, description="需要澄清的问题列表")
    recommendations: list[str] = Field(default_factory=list, description="推荐引导")
    confidence: float = Field(
        default=0.5, ge=0.0, le=1.0, description="需求明确程度，模糊需求必须 < 0.7"
    )

    @field_validator("steps", mode="before")
    @classmethod
    def _parse_steps(cls, v: object) -> object:
        return _try_parse_json(v)

    @field_validator("final_target", mode="before")
    @classmethod
    def _parse_final_target(cls, v: object) -> object:
        return _try_parse_json(v)

    @field_validator("ambiguities", mode="before")
    @classmethod
    def _parse_ambiguities(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        return v

    @field_validator("recommendations", mode="before")
    @classmethod
    def _parse_recommendations(cls, v: object) -> object:
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


class AnalysisResult(BaseModel):
    """
    需求分析结果（AnalystAgent 输出）

    业务层面的分析：
    - 将用户需求拆分为业务步骤（Step）
    - 识别数据目标
    - 标注歧义点

    不涉及技术实现（Job、Stage、SQL），那是架构师的职责。
    """

    user_query: str = Field(..., description="用户原始输入")
    summary: str = Field(..., description="一句话概括用户需求")

    steps: list[Step] = Field(default_factory=list, description="业务步骤列表")

    final_target: DataTarget | None = Field(None, description="最终数据目标")
    ambiguities: list[str] = Field(default_factory=list, description="需要澄清的问题列表")
    recommendations: list[str] = Field(default_factory=list, description="推荐引导")
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    @classmethod
    def from_output(cls, output: AnalysisResultOutput, user_query: str) -> "AnalysisResult":
        """从 LLM 输出构建完整的分析结果"""
        return cls(
            user_query=user_query,
            summary=output.summary,
            steps=output.steps,
            final_target=output.final_target,
            ambiguities=output.ambiguities,
            recommendations=output.recommendations,
            confidence=output.confidence,
        )

    def needs_clarification(self) -> bool:
        """是否需要用户澄清"""
        return len(self.ambiguities) > 0

    def get_all_tables(self) -> list[str]:
        """获取所有涉及的表"""
        tables = set()
        for step in self.steps:
            tables.update(step.input_tables)
            if step.output_table:
                tables.add(step.output_table)
        if self.final_target:
            tables.add(self.final_target.table_name)
        return list(tables)

    def step_by_id(self, step_id: str) -> Step | None:
        """根据 ID 获取 Step"""
        for step in self.steps:
            if step.step_id == step_id:
                return step
        return None

    def plan_summary(self) -> str:
        """获取执行计划摘要"""
        lines = []
        for step in self.steps:
            deps = f" (依赖: {', '.join(step.depends_on)})" if step.depends_on else ""
            output = f" → {step.output_table}" if step.output_table else ""
            lines.append(f"[{step.step_name}]{deps}{output}")
            lines.append(f"  {step.description}")
        return "\n".join(lines)
