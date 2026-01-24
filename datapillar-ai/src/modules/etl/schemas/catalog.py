"""
Catalog / 元数据问答相关 schema

CatalogAgent 的职责是元数据问答：
- 回答"有哪些 catalog/schema/表/字段"等问题
- 回答表结构、血缘概览等问题
- 不做 ETL 需求分析，不生成 SQL
"""

from pydantic import BaseModel, Field


class OptionItem(BaseModel):
    """结构化选项（统一表示各层级资产）"""

    type: str = Field(
        ...,
        description="资产类型：catalog/schema/table/column/lineage/valuedomain",
    )
    name: str = Field(..., description="名称")
    path: str = Field(
        ...,
        description="完整路径：catalog / catalog.schema / catalog.schema.table / catalog.schema.table.column",
    )
    description: str | None = Field(default=None, description="描述")
    tools: list[str] = Field(
        default_factory=list,
        description="可用工具（展开下一层或查看详情）",
    )
    extra: dict | None = Field(
        default=None,
        description="额外信息（如 column 的 data_type，lineage 的 upstream/downstream）",
    )


class CatalogResultOutput(BaseModel):
    """
    CatalogAgent 的 LLM 输出（用于 structured output）

    设计原则：
    - summary: 一句话概括
    - answer: 详细文字回答
    - options: 结构化选项（统一表示各层级资产）
    - ambiguities: 需要澄清的问题
    - confidence: 信息充分程度
    """

    summary: str = Field(..., description="一句话概括回答")
    answer: str = Field(..., description="详细文字回答（给用户看的中文回答）")
    options: list[OptionItem] = Field(
        default_factory=list,
        description="结构化选项（catalog/schema/table/column 等）",
    )
    ambiguities: list[str] = Field(
        default_factory=list,
        description="需要澄清的问题列表（当信息不足时）",
    )
    recommendations: list[str] = Field(
        default_factory=list,
        description="推荐引导（可选）",
    )
    confidence: float = Field(
        default=1.0,
        ge=0.0,
        le=1.0,
        description="信息充分程度，需要澄清时 < 0.7",
    )


class CatalogResult(BaseModel):
    """
    CatalogAgent 的交付物（存入 Blackboard）

    包含用户原始输入 + LLM 输出
    """

    user_query: str = Field(..., description="用户原始输入")
    summary: str = Field(..., description="一句话概括")
    answer: str = Field(..., description="详细文字回答")
    options: list[OptionItem] = Field(default_factory=list, description="结构化选项")
    ambiguities: list[str] = Field(default_factory=list, description="需要澄清的问题")
    recommendations: list[str] = Field(default_factory=list, description="推荐引导")
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)

    @classmethod
    def from_output(cls, output: CatalogResultOutput, user_query: str) -> "CatalogResult":
        """从 LLM 输出构建完整结果"""
        return cls(
            user_query=user_query,
            summary=output.summary,
            answer=output.answer,
            options=output.options,
            ambiguities=output.ambiguities,
            recommendations=output.recommendations,
            confidence=output.confidence,
        )

    def needs_clarification(self) -> bool:
        """是否需要用户澄清"""
        return self.confidence < 0.7 and len(self.ambiguities) > 0
