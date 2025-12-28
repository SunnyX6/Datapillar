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
    """表单可选项（词根/修饰符/单位：前端传了就用，没传由后端从 Neo4j 获取）"""
    data_types: List[str] = Field(alias="dataTypes")
    units: Optional[List[str]] = Field(default=None)
    word_roots: Optional[List[WordRoot]] = Field(alias="wordRoots", default=None)
    modifiers: Optional[List[Modifier]] = Field(default=None)

    class Config:
        populate_by_name = True


class AtomicPayload(BaseModel):
    """原子指标 payload"""
    measure_columns: List[MeasureColumn] = Field(alias="measureColumns", default_factory=list)
    filter_columns: List[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    # 物理表引用，用于查询表的上下文
    ref_catalog: Optional[str] = Field(alias="refCatalog", default=None)
    ref_schema: Optional[str] = Field(alias="refSchema", default=None)
    ref_table: Optional[str] = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class BaseMetric(BaseModel):
    """基础指标引用"""
    code: str
    name: Optional[str] = None
    description: Optional[str] = None


class DerivedPayload(BaseModel):
    """派生指标 payload"""
    base_metric: BaseMetric = Field(alias="baseMetric")
    modifiers: List[Modifier] = Field(default_factory=list)
    filter_columns: List[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    ref_catalog: Optional[str] = Field(alias="refCatalog", default=None)
    ref_schema: Optional[str] = Field(alias="refSchema", default=None)
    ref_table: Optional[str] = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class CompositeMetricRef(BaseModel):
    """复合指标引用的指标"""
    code: str
    name: Optional[str] = None
    description: Optional[str] = None


class CompositePayload(BaseModel):
    """复合指标 payload"""
    metrics: List[CompositeMetricRef] = Field(default_factory=list)

    class Config:
        populate_by_name = True


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
    name: Optional[str] = ""
    word_roots: List[str] = Field(alias="wordRoots", default_factory=list)
    aggregation: Optional[str] = ""
    modifiers_selected: List[str] = Field(alias="modifiersSelected", default_factory=list)
    type: Optional[MetricType] = MetricType.ATOMIC
    data_type: Optional[str] = Field(alias="dataType", default="")
    unit: Optional[str] = None
    calculation_formula: Optional[str] = Field(alias="calculationFormula", default="")
    comment: Optional[str] = ""
    measure_columns: List[str] = Field(alias="measureColumns", default_factory=list)
    filter_columns: List[str] = Field(alias="filterColumns", default_factory=list)
    warning: Optional[str] = None

    class Config:
        populate_by_name = True
        by_alias = True


