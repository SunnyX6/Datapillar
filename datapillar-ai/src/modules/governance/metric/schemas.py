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


class AIFillResponse(BaseModel):
    """AI 填写响应"""

    # 状态：成功/失败
    success: bool = True
    # AI 消息：始终有值，成功时是友好提示，失败时是失败原因
    message: str = ""
    # 推荐列表：失败时返回推荐的表和列
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
