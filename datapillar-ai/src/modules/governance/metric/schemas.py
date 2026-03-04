# @author Sunny
# @date 2026-01-27

"""
indicator AI Governance data model
"""

from enum import Enum

from pydantic import BaseModel, Field


class MetricType(str, Enum):
    ATOMIC = "ATOMIC"
    DERIVED = "DERIVED"
    COMPOSITE = "COMPOSITE"


# ============ Fill ============


class WordRoot(BaseModel):
    """root"""

    code: str
    name: str


class Modifier(BaseModel):
    """modifier"""

    code: str
    name: str


class MeasureColumn(BaseModel):
    """measure column"""

    name: str
    type: str
    comment: str | None = None


class FilterColumnValue(BaseModel):
    """Filter column values"""

    key: str
    label: str


class FilterColumn(BaseModel):
    """Filter columns（Contains value range）"""

    name: str
    type: str
    comment: str | None = None
    values: list[FilterColumnValue] = Field(default_factory=list)


class FormOptions(BaseModel):
    """form options（root/modifier/unit：Just use it after passing it from the front end，Not transmitted by the backend from Neo4j Get）"""

    data_types: list[str] = Field(alias="dataTypes")
    units: list[str] | None = Field(default=None)
    word_roots: list[WordRoot] | None = Field(alias="wordRoots", default=None)
    modifiers: list[Modifier] | None = Field(default=None)

    class Config:
        populate_by_name = True


class AtomicPayload(BaseModel):
    """Atomic indicators payload"""

    measure_columns: list[MeasureColumn] = Field(alias="measureColumns", default_factory=list)
    filter_columns: list[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    # Physical table reference，The context used to query the table
    ref_catalog: str | None = Field(alias="refCatalog", default=None)
    ref_schema: str | None = Field(alias="refSchema", default=None)
    ref_table: str | None = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class BaseMetric(BaseModel):
    """Basic indicator reference"""

    code: str
    name: str | None = None
    description: str | None = None


class DerivedPayload(BaseModel):
    """Derived indicators payload"""

    base_metric: BaseMetric = Field(alias="baseMetric")
    modifiers: list[Modifier] = Field(default_factory=list)
    filter_columns: list[FilterColumn] = Field(alias="filterColumns", default_factory=list)
    ref_catalog: str | None = Field(alias="refCatalog", default=None)
    ref_schema: str | None = Field(alias="refSchema", default=None)
    ref_table: str | None = Field(alias="refTable", default=None)

    class Config:
        populate_by_name = True


class CompositeMetricRef(BaseModel):
    """Indicators referenced by composite indicators"""

    code: str
    name: str | None = None
    description: str | None = None


class CompositePayload(BaseModel):
    """Composite indicator payload"""

    metrics: list[CompositeMetricRef] = Field(default_factory=list)

    class Config:
        populate_by_name = True


class FillContext(BaseModel):
    """Fill in the context"""

    metric_type: MetricType = Field(alias="metricType")
    payload: dict
    form_options: FormOptions = Field(alias="formOptions")

    class Config:
        populate_by_name = True

    def get_atomic_payload(self) -> AtomicPayload | None:
        """Get atomic indicators payload"""
        if self.metric_type == MetricType.ATOMIC:
            return AtomicPayload(**self.payload)
        return None

    def get_derived_payload(self) -> DerivedPayload | None:
        """Get derived metrics payload"""
        if self.metric_type == MetricType.DERIVED:
            return DerivedPayload(**self.payload)
        return None

    def get_composite_payload(self) -> CompositePayload | None:
        """Get composite indicator payload"""
        if self.metric_type == MetricType.COMPOSITE:
            return CompositePayload(**self.payload)
        return None


class AIFillRequest(BaseModel):
    """AI Fill out the request"""

    user_input: str = Field(alias="userInput", description="User natural language input")
    context: FillContext

    class Config:
        populate_by_name = True


class AIFillOutput(BaseModel):
    """AI Fill in the output（LLM generate，used for structured output）"""

    success: bool = Field(
        ...,
        description="Whether the indicator was successfully generated，When the verification fails, it is False",
    )
    message: str = Field(
        ...,
        description="news，Friendly reminder when successful，Failure is the reason for failure（Use a arrogant tone）",
    )

    # The following fields are only available in success=True sometimes valuable
    name: str | None = Field(None, description="Indicator Chinese name")
    word_roots: list[str] = Field(
        alias="wordRoots", default_factory=list, description="chosen root word code list"
    )
    aggregation: str | None = Field(None, description="aggregate function，Such as SUM/COUNT/AVG")
    modifiers_selected: list[str] = Field(
        alias="modifiersSelected", default_factory=list, description="Selected modifiers code list"
    )
    type: MetricType | None = Field(None, description="Indicator type：ATOMIC/DERIVED/COMPOSITE")
    data_type: str | None = Field(alias="dataType", default=None, description="data type")
    unit: str | None = Field(
        None, description="unit code，Must be a unit in the semantic asset context"
    )
    calculation_formula: str | None = Field(
        alias="calculationFormula", default=None, description="Indicator calculation formula"
    )
    comment: str | None = Field(None, description="Business description")
    measure_columns: list[str] = Field(
        alias="measureColumns", default_factory=list, description="Metric column name list"
    )
    filter_columns: list[str] = Field(
        alias="filterColumns", default_factory=list, description="Filter list of column names"
    )

    class Config:
        populate_by_name = True


class AIFillResponse(BaseModel):
    """AI Fill in the response"""

    # Status：success/failed
    success: bool = True
    # AI news：always valuable，Friendly reminder when successful，Failure is the reason for failure
    message: str = ""
    # Recommended list：Returns recommended tables and columns on failure（populated by code，No LLM generate）
    recommendations: list[dict] = Field(default_factory=list)

    # The following fields are only available in success=True sometimes valuable
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
        """from LLM Output build response，additional recommendations"""
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
