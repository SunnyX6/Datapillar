"""
指标 AI 治理数据模型
"""

from typing import List, Optional
from enum import Enum
from pydantic import BaseModel, Field


class MetricType(str, Enum):
    ATOMIC = "ATOMIC"
    DERIVED = "DERIVED"
    COMPOSITE = "COMPOSITE"


class IssueSeverity(str, Enum):
    ERROR = "error"
    WARNING = "warning"


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
    comment: Optional[str] = None


class FilterColumnValue(BaseModel):
    """过滤列值"""
    key: str
    label: str


class FilterColumn(BaseModel):
    """过滤列（含值域）"""
    name: str
    type: str
    comment: Optional[str] = None
    values: List[FilterColumnValue] = Field(default_factory=list)


class FormOptions(BaseModel):
    """表单可选项"""
    data_types: List[str] = Field(alias="dataTypes")
    units: List[str]
    word_roots: List[WordRoot] = Field(alias="wordRoots", default_factory=list)
    modifiers: List[Modifier] = Field(default_factory=list)

    class Config:
        populate_by_name = True


class AtomicPayload(BaseModel):
    """原子指标 payload"""
    measure_columns: List[MeasureColumn] = Field(alias="measureColumns", default_factory=list)
    filter_columns: List[FilterColumn] = Field(alias="filterColumns", default_factory=list)

    class Config:
        populate_by_name = True


class BaseMetric(BaseModel):
    """基础指标引用"""
    code: str


class DerivedPayload(BaseModel):
    """派生指标 payload"""
    base_metric: BaseMetric = Field(alias="baseMetric")
    modifiers: List[str] = Field(default_factory=list)

    class Config:
        populate_by_name = True


class CompositePayload(BaseModel):
    """复合指标 payload"""
    metrics: List[str] = Field(default_factory=list)
    operation: str = "divide"


class FillContext(BaseModel):
    """填写上下文"""
    metric_type: MetricType = Field(alias="metricType")
    payload: dict
    form_options: FormOptions = Field(alias="formOptions")

    class Config:
        populate_by_name = True

    def get_atomic_payload(self) -> Optional[AtomicPayload]:
        """获取原子指标 payload"""
        if self.metric_type == MetricType.ATOMIC:
            return AtomicPayload(**self.payload)
        return None

    def get_derived_payload(self) -> Optional[DerivedPayload]:
        """获取派生指标 payload"""
        if self.metric_type == MetricType.DERIVED:
            return DerivedPayload(**self.payload)
        return None

    def get_composite_payload(self) -> Optional[CompositePayload]:
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
    name: str
    code: str
    type: MetricType
    data_type: str = Field(alias="dataType")
    unit: Optional[str] = None
    calculation_formula: str = Field(alias="calculationFormula")
    comment: str

    class Config:
        populate_by_name = True
        by_alias = True


# ============ Check ============

class MetricForm(BaseModel):
    """指标表单"""
    name: str
    code: str
    type: MetricType
    data_type: str = Field(alias="dataType")
    unit: Optional[str] = None
    calculation_formula: str = Field(alias="calculationFormula")
    comment: str

    class Config:
        populate_by_name = True


class AICheckRequest(BaseModel):
    """AI 检查请求"""
    form: MetricForm


class SemanticIssue(BaseModel):
    """语义问题"""
    field: str
    severity: IssueSeverity
    message: str


class AICheckResponse(BaseModel):
    """AI 检查响应"""
    valid: bool
    issues: List[SemanticIssue] = Field(default_factory=list)
    suggestions: dict = Field(default_factory=dict)
