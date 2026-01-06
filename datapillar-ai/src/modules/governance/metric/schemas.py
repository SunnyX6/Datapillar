"""
指标 AI 治理数据模型
"""

from enum import Enum

from pydantic import BaseModel, Field


class MetricType(str, Enum):
    ATOMIC = "ATOMIC"
    DERIVED = "DERIVED"
    COMPOSITE = "COMPOSITE"


# ============ Fill ============


class WordRoot(BaseModel):
    """词根"""

    code: str
    name: str


class Modifier(BaseModel):
    """修饰符"""

    code: str
    name: str


class MeasureColumn(BaseModel):
    """度量列"""

    name: str
    type: str
    comment: str | None = None


class FilterColumnValue(BaseModel):
    """过滤列值"""

    key: str
    label: str


class FilterColumn(BaseModel):
    """过滤列（含值域）"""

    name: str
    type: str
    comment: str | None = None
    values: list[FilterColumnValue] = Field(default_factory=list)


class FormOptions(BaseModel):
    """表单可选项（词根/修饰符/单位：前端传了就用，没传由后端从 Neo4j 获取）"""

    data_types: list[str] = Field(alias="dataTypes")
    units: list[str] | None = Field(default=None)
    word_roots: list[WordRoot] | None = Field(alias="wordRoots", default=None)
    modifiers: list[Modifier] | None = Field(default=None)

    class Config:
        populate_by_name = True


class AtomicPayload(BaseModel):
    """原子指标 payload"""

    measure_columns: list[MeasureColumn] = Field(alias="measureColumns", default_factory=list)
    filter_columns: list[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    # 物理表引用，用于查询表的上下文
    ref_catalog: str | None = Field(alias="refCatalog", default=None)
    ref_schema: str | None = Field(alias="refSchema", default=None)
    ref_table: str | None = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class BaseMetric(BaseModel):
    """基础指标引用"""

    code: str
    name: str | None = None
    description: str | None = None


class DerivedPayload(BaseModel):
    """派生指标 payload"""

    base_metric: BaseMetric = Field(alias="baseMetric")
    modifiers: list[Modifier] = Field(default_factory=list)
    filter_columns: list[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    ref_catalog: str | None = Field(alias="refCatalog", default=None)
    ref_schema: str | None = Field(alias="refSchema", default=None)
    ref_table: str | None = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class CompositeMetricRef(BaseModel):
    """复合指标引用的指标"""

    code: str
    name: str | None = None
    description: str | None = None


class CompositePayload(BaseModel):
    """复合指标 payload"""

    metrics: list[CompositeMetricRef] = Field(default_factory=list)

    class Config:
        populate_by_name = True


class FillContext(BaseModel):
    """填写上下文"""

    metric_type: MetricType = Field(alias="metricType")
    payload: dict
    form_options: FormOptions = Field(alias="formOptions")

    class Config:
        populate_by_name = True

    def get_atomic_payload(self) -> AtomicPayload | None:
        """获取原子指标 payload"""
        if self.metric_type == MetricType.ATOMIC:
            return AtomicPayload(**self.payload)
        return None

    def get_derived_payload(self) -> DerivedPayload | None:
        """获取派生指标 payload"""
        if self.metric_type == MetricType.DERIVED:
            return DerivedPayload(**self.payload)
        return None

    def get_composite_payload(self) -> CompositePayload | None:
        """获取复合指标 payload"""
        if self.metric_type == MetricType.COMPOSITE:
            return CompositePayload(**self.payload)
        return None


class AIFillRequest(BaseModel):
    """AI 填写请求"""

    user_input: str = Field(alias="userInput", description="用户自然语言输入")
    context: FillContext

    class Config:
        populate_by_name = True


class AIFillOutput(BaseModel):
    """AI 填写输出（LLM 生成，用于 structured output）"""

    success: bool = Field(..., description="是否成功生成指标，验证不通过时为 False")
    message: str = Field(
        ..., description="消息，成功时是友好提示，失败时是失败原因（使用傲娇语气）"
    )

    # 以下字段仅在 success=True 时有值
    name: str | None = Field(None, description="指标中文名称")
    word_roots: list[str] = Field(
        alias="wordRoots", default_factory=list, description="选用的词根 code 列表"
    )
    aggregation: str | None = Field(None, description="聚合函数，如 SUM/COUNT/AVG")
    modifiers_selected: list[str] = Field(
        alias="modifiersSelected", default_factory=list, description="选用的修饰符 code 列表"
    )
    type: MetricType | None = Field(None, description="指标类型：ATOMIC/DERIVED/COMPOSITE")
    data_type: str | None = Field(alias="dataType", default=None, description="数据类型")
    unit: str | None = Field(None, description="单位 code，必须是语义资产上下文中的单位")
    calculation_formula: str | None = Field(
        alias="calculationFormula", default=None, description="指标计算公式"
    )
    comment: str | None = Field(None, description="业务描述")
    measure_columns: list[str] = Field(
        alias="measureColumns", default_factory=list, description="度量列名列表"
    )
    filter_columns: list[str] = Field(
        alias="filterColumns", default_factory=list, description="过滤列名列表"
    )

    class Config:
        populate_by_name = True


class AIFillResponse(BaseModel):
    """AI 填写响应"""

    # 状态：成功/失败
    success: bool = True
    # AI 消息：始终有值，成功时是友好提示，失败时是失败原因
    message: str = ""
    # 推荐列表：失败时返回推荐的表和列（由代码填充，不是 LLM 生成）
    recommendations: list[dict] = Field(default_factory=list)

    # 以下字段仅在 success=True 时有值
    name: str | None = None
    word_roots: list[str] = Field(alias="wordRoots", default_factory=list)
    aggregation: str | None = None
    modifiers_selected: list[str] = Field(alias="modifiersSelected", default_factory=list)
    type: MetricType | None = None
    data_type: str | None = Field(alias="dataType", default=None)
    unit: str | None = None
    calculation_formula: str | None = Field(alias="calculationFormula", default=None)
    comment: str | None = None
    measure_columns: list[str] = Field(alias="measureColumns", default_factory=list)
    filter_columns: list[str] = Field(alias="filterColumns", default_factory=list)

    class Config:
        populate_by_name = True
        by_alias = True

    @classmethod
    def from_output(
        cls, output: AIFillOutput, recommendations: list[dict] | None = None
    ) -> "AIFillResponse":
        """从 LLM 输出构建响应，附加 recommendations"""
        return cls(
            success=output.success,
            message=output.message,
            recommendations=recommendations or [],
            name=output.name,
            word_roots=output.word_roots,
            aggregation=output.aggregation,
            modifiers_selected=output.modifiers_selected,
            type=output.type,
            data_type=output.data_type,
            unit=output.unit,
            calculation_formula=output.calculation_formula,
            comment=output.comment,
            measure_columns=output.measure_columns,
            filter_columns=output.filter_columns,
        )
