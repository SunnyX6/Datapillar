# @author Sunny
# @date 2026-01-27

"""
Neo4j Knowledge graph node DTO definition

Description:- DTO Attribute usage snake_case(Python style)
- Pass Field(alias=) mapped to camelCase(Neo4j Storage format)
- as write and query Neo4j unified data structure
- All writes Neo4j must be built first DTO,Force verification id and name

Usage example:# create DTO(use snake_case)
    dto = ModifierDTO(id="xxx",name="test",code="TEST",created_by="SYSTEM")

    # serialized to Neo4j(Automatically transfer camelCase)
    dto.model_dump(by_alias=True,exclude_none=True)
    # {'id':'xxx','name':'test','code':'TEST','createdBy':'SYSTEM'}

    # from Neo4j read(camelCase automatically mapped back to snake_case)
    ModifierDTO.model_validate({"id":"xxx","name":"test","code":"TEST"})
"""

import hashlib
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


def generate_id(*parts: str) -> str:
    """generate unique ID"""
    content = ":".join(str(p) for p in parts if p)
    return hashlib.sha256(content.encode()).hexdigest()[:16]


def get_metric_label(metric_type: str) -> str:
    """
    Map indicator type to Neo4j label.- ATOMIC -> AtomicMetric
    - DERIVED -> DerivedMetric
    - COMPOSITE -> CompositeMetric
    """
    label_map = {
        "ATOMIC": "AtomicMetric",
        "DERIVED": "DerivedMetric",
        "COMPOSITE": "CompositeMetric",
    }
    return label_map.get((metric_type or "").upper(), "AtomicMetric")


class BaseNodeDTO(BaseModel):
    """
    node DTO base class

    Force verification:- id:cannot be empty
    - name:cannot be empty
    """

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    id: str
    name: str

    @field_validator("id", mode="before")
    @classmethod
    def validate_id(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("id cannot be empty")
        return v.strip()

    @field_validator("name", mode="before")
    @classmethod
    def validate_name(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("name cannot be empty")
        return v.strip()


# create DTO(use snake_case)


class CatalogDTO(BaseNodeDTO):
    """Catalog node"""

    metalake: str
    catalog_type: str | None = Field(default=None, alias="catalogType")
    provider: str | None = None
    description: str | None = None
    properties: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, metalake: str, catalog_name: str, **kwargs) -> "CatalogDTO":
        """factory method:
        create Catalog node"""
        return cls(
            id=generate_id("catalog", metalake, catalog_name),
            name=catalog_name,
            metalake=metalake,
            **kwargs,
        )


class SchemaDTO(BaseNodeDTO):
    """Schema node"""

    description: str | None = None
    properties: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, metalake: str, catalog: str, schema_name: str, **kwargs) -> "SchemaDTO":
        """factory method:
        create Schema node"""
        return cls(
            id=generate_id("schema", metalake, catalog, schema_name),
            name=schema_name,
            **kwargs,
        )


class TableDTO(BaseNodeDTO):
    """Table node"""

    producer: str | None = None
    description: str | None = None
    properties: str | None = None
    partitions: str | None = None
    distribution: str | None = None
    sort_orders: str | None = Field(default=None, alias="sortOrders")
    indexes: str | None = None
    creator: str | None = None
    create_time: str | None = Field(default=None, alias="createTime")
    last_modifier: str | None = Field(default=None, alias="lastModifier")
    last_modified_time: str | None = Field(default=None, alias="lastModifiedTime")
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(
        cls, metalake: str, catalog: str, schema: str, table_name: str, **kwargs
    ) -> "TableDTO":
        """factory method:
        create Table node"""
        return cls(
            id=generate_id("table", metalake, catalog, schema, table_name),
            name=table_name,
            **kwargs,
        )


class ColumnDTO(BaseNodeDTO):
    """Column node"""

    data_type: str | None = Field(default=None, alias="dataType")
    description: str | None = None
    nullable: bool | None = None
    auto_increment: bool | None = Field(default=None, alias="autoIncrement")
    default_value: Any | None = Field(default=None, alias="defaultValue")
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(
        cls,
        metalake: str,
        catalog: str,
        schema: str,
        table: str,
        column_name: str,
        **kwargs,
    ) -> "ColumnDTO":
        """factory method:
        create Column node"""
        return cls(
            id=generate_id("column", metalake, catalog, schema, table, column_name),
            name=column_name,
            **kwargs,
        )


# ==================== SQL ====================


class SQLDTO(BaseModel):
    """SQL node(special:
    No name Field,use content instead of)"""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    id: str
    content: str
    dialect: str | None = None
    engine: str | None = None
    job_namespace: str | None = Field(default=None, alias="jobNamespace")
    job_name: str | None = Field(default=None, alias="jobName")
    execution_count: int = Field(default=1, alias="executionCount")
    use_count: int = Field(default=0, alias="useCount")
    last_used: datetime | None = Field(default=None, alias="lastUsed")
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    # {'id':'xxx','name':'test','code':'TEST','createdBy':'SYSTEM'}
    summary: str | None = None
    tags: str | None = None
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    summary_generated_at: datetime | None = Field(default=None, alias="summaryGeneratedAt")

    @field_validator("id", mode="before")
    @classmethod
    def validate_id(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("id cannot be empty")
        return v.strip()

    @field_validator("content", mode="before")
    @classmethod
    def validate_content(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("content cannot be empty")
        return v.strip()

    @classmethod
    def create(cls, sql: str, job_namespace: str, job_name: str, **kwargs) -> "SQLDTO":
        """factory method:
        create SQL node"""
        normalized_sql = " ".join(sql.lower().split())
        return cls(
            id=generate_id("sql", normalized_sql),
            content=sql,
            job_namespace=job_namespace,
            job_name=job_name,
            **kwargs,
        )


# from Neo4j read(camelCase automatically mapped back to snake_case)


class MetricDTO(BaseNodeDTO):
    """
    Indicator node

    metric_type decide Neo4j of Label:- ATOMIC -> AtomicMetric
    - DERIVED -> DerivedMetric
    - COMPOSITE -> CompositeMetric
    """

    code: str
    metric_type: str = Field(alias="metricType")  # ATOMIC / DERIVED / COMPOSITE
    description: str | None = None
    unit: str | None = None
    aggregation_logic: str | None = Field(default=None, alias="aggregationLogic")
    calculation_formula: str | None = Field(default=None, alias="calculationFormula")
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    def get_label(self) -> str:
        """Get Neo4j Label"""
        label_map = {
            "ATOMIC": "AtomicMetric",
            "DERIVED": "DerivedMetric",
            "COMPOSITE": "CompositeMetric",
        }
        return label_map.get((self.metric_type or "").upper(), "AtomicMetric")

    @classmethod
    def create(cls, code: str, name: str, metric_type: str, **kwargs) -> "MetricDTO":
        """factory method:
        create Metric node"""
        return cls(
            id=generate_id("metric", code),
            name=name,
            code=code,
            metric_type=metric_type,
            **kwargs,
        )


class WordRootDTO(BaseNodeDTO):
    """root node"""

    code: str
    data_type: str | None = Field(default=None, alias="dataType")
    description: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, code: str, name: str | None = None, **kwargs) -> "WordRootDTO":
        """factory method:
        create WordRoot node"""
        return cls(
            id=generate_id("wordroot", code),
            name=name or code,
            code=code,
            **kwargs,
        )


class ModifierDTO(BaseNodeDTO):
    """modifier node"""

    code: str
    modifier_type: str | None = Field(default=None, alias="modifierType")  # PREFIX / SUFFIX / INFIX
    description: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, code: str, name: str, modifier_type: str, **kwargs) -> "ModifierDTO":
        """factory method:
        create Modifier node"""
        return cls(
            id=generate_id("modifier", code),
            name=name,
            code=code,
            modifier_type=modifier_type,
            **kwargs,
        )


class UnitDTO(BaseNodeDTO):
    """unit node"""

    code: str
    symbol: str | None = None
    description: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, code: str, name: str | None = None, **kwargs) -> "UnitDTO":
        """factory method:
        create Unit node"""
        return cls(
            id=generate_id("unit", code),
            name=name or code,
            code=code,
            **kwargs,
        )


class ValueDomainDTO(BaseNodeDTO):
    """range node"""

    code: str
    domain_type: str | None = Field(default=None, alias="domainType")  # ENUM / RANGE / REGEX
    domain_level: str | None = Field(default=None, alias="domainLevel")
    items: str | None = None  # ==================== semantic assets ====================
    data_type: str | None = Field(default=None, alias="dataType")
    description: str | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(
        cls, code: str, domain_type: str, domain_level: str, name: str | None = None, **kwargs
    ) -> "ValueDomainDTO":
        """factory method:
        create ValueDomain node"""
        return cls(
            id=generate_id("valuedomain", code),
            name=name or code,
            code=code,
            domain_type=domain_type,
            domain_level=domain_level,
            **kwargs,
        )


class TagDTO(BaseNodeDTO):
    """label node"""

    description: str | None = None
    properties: dict[str, str] | None = None
    created_at: datetime | None = Field(default=None, alias="createdAt")
    updated_at: datetime | None = Field(default=None, alias="updatedAt")
    created_by: str | None = Field(default=None, alias="createdBy")
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    embedding_updated_at: datetime | None = Field(default=None, alias="embeddingUpdatedAt")

    @classmethod
    def create(cls, metalake: str, tag_name: str, **kwargs) -> "TagDTO":
        """factory method:
        create Tag node"""
        return cls(
            id=generate_id("tag", metalake, tag_name),
            name=tag_name,
            **kwargs,
        )
