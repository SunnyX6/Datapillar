"""
Neo4j 知识图谱节点 DTO 定义

说明：
- DTO 属性使用 snake_case（Python 风格）
- 通过 Field(alias=) 映射为 camelCase（Neo4j 存储格式）
- 作为写入和查询 Neo4j 的统一数据结构
- 所有写入 Neo4j 必须先构建 DTO，强制验证 id 和 name

使用示例：
    # 创建 DTO（用 snake_case）
    dto = ModifierDTO(id="xxx", name="test", code="TEST", created_by="SYSTEM")

    # 序列化到 Neo4j（自动转 camelCase）
    dto.model_dump(by_alias=True, exclude_none=True)
    # {'id': 'xxx', 'name': 'test', 'code': 'TEST', 'createdBy': 'SYSTEM'}

    # 从 Neo4j 读取（camelCase 自动映射回 snake_case）
    ModifierDTO.model_validate({"id": "xxx", "name": "test", "code": "TEST"})
"""

import hashlib
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


def generate_id(*parts: str) -> str:
    """生成唯一 ID"""
    content = ":".join(str(p) for p in parts if p)
    return hashlib.sha256(content.encode()).hexdigest()[:16]


def get_metric_label(metric_type: str) -> str:
    """
    将指标类型映射为 Neo4j label。

    - ATOMIC -> AtomicMetric
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
    节点 DTO 基类

    强制验证：
    - id: 不能为空
    - name: 不能为空
    """

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    id: str
    name: str

    @field_validator("id", mode="before")
    @classmethod
    def validate_id(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("id 不能为空")
        return v.strip()

    @field_validator("name", mode="before")
    @classmethod
    def validate_name(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("name 不能为空")
        return v.strip()


# ==================== 物理资产 ====================


class CatalogDTO(BaseNodeDTO):
    """Catalog 节点"""

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
        """工厂方法：创建 Catalog 节点"""
        return cls(
            id=generate_id("catalog", metalake, catalog_name),
            name=catalog_name,
            metalake=metalake,
            **kwargs,
        )


class SchemaDTO(BaseNodeDTO):
    """Schema 节点"""

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
        """工厂方法：创建 Schema 节点"""
        return cls(
            id=generate_id("schema", metalake, catalog, schema_name),
            name=schema_name,
            **kwargs,
        )


class TableDTO(BaseNodeDTO):
    """Table 节点"""

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
        """工厂方法：创建 Table 节点"""
        return cls(
            id=generate_id("table", metalake, catalog, schema, table_name),
            name=table_name,
            **kwargs,
        )


class ColumnDTO(BaseNodeDTO):
    """Column 节点"""

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
        """工厂方法：创建 Column 节点"""
        return cls(
            id=generate_id("column", metalake, catalog, schema, table, column_name),
            name=column_name,
            **kwargs,
        )


# ==================== SQL ====================


class SQLDTO(BaseModel):
    """SQL 节点（特殊：没有 name 字段，用 content 代替）"""

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
    # 语义摘要相关字段（用于智能检索）
    summary: str | None = None
    tags: str | None = None
    embedding: list[float] | None = None
    embedding_provider: str | None = Field(default=None, alias="embeddingProvider")
    summary_generated_at: datetime | None = Field(default=None, alias="summaryGeneratedAt")

    @field_validator("id", mode="before")
    @classmethod
    def validate_id(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("id 不能为空")
        return v.strip()

    @field_validator("content", mode="before")
    @classmethod
    def validate_content(cls, v: Any) -> str:
        if not v or not isinstance(v, str) or not v.strip():
            raise ValueError("content 不能为空")
        return v.strip()

    @classmethod
    def create(cls, sql: str, job_namespace: str, job_name: str, **kwargs) -> "SQLDTO":
        """工厂方法：创建 SQL 节点"""
        normalized_sql = " ".join(sql.lower().split())
        return cls(
            id=generate_id("sql", normalized_sql),
            content=sql,
            job_namespace=job_namespace,
            job_name=job_name,
            **kwargs,
        )


# ==================== 语义资产 ====================


class MetricDTO(BaseNodeDTO):
    """
    指标节点

    metric_type 决定 Neo4j 的 Label：
    - ATOMIC -> AtomicMetric
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
        """获取 Neo4j Label"""
        label_map = {
            "ATOMIC": "AtomicMetric",
            "DERIVED": "DerivedMetric",
            "COMPOSITE": "CompositeMetric",
        }
        return label_map.get((self.metric_type or "").upper(), "AtomicMetric")

    @classmethod
    def create(cls, code: str, name: str, metric_type: str, **kwargs) -> "MetricDTO":
        """工厂方法：创建 Metric 节点"""
        return cls(
            id=generate_id("metric", code),
            name=name,
            code=code,
            metric_type=metric_type,
            **kwargs,
        )


class WordRootDTO(BaseNodeDTO):
    """词根节点"""

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
        """工厂方法：创建 WordRoot 节点"""
        return cls(
            id=generate_id("wordroot", code),
            name=name or code,
            code=code,
            **kwargs,
        )


class ModifierDTO(BaseNodeDTO):
    """修饰符节点"""

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
        """工厂方法：创建 Modifier 节点"""
        return cls(
            id=generate_id("modifier", code),
            name=name,
            code=code,
            modifier_type=modifier_type,
            **kwargs,
        )


class UnitDTO(BaseNodeDTO):
    """单位节点"""

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
        """工厂方法：创建 Unit 节点"""
        return cls(
            id=generate_id("unit", code),
            name=name or code,
            code=code,
            **kwargs,
        )


class ValueDomainDTO(BaseNodeDTO):
    """值域节点"""

    code: str
    domain_type: str | None = Field(default=None, alias="domainType")  # ENUM / RANGE / REGEX
    domain_level: str | None = Field(default=None, alias="domainLevel")
    items: str | None = None  # JSON 格式
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
        """工厂方法：创建 ValueDomain 节点"""
        return cls(
            id=generate_id("valuedomain", code),
            name=name or code,
            code=code,
            domain_type=domain_type,
            domain_level=domain_level,
            **kwargs,
        )


class TagDTO(BaseNodeDTO):
    """标签节点"""

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
        """工厂方法：创建 Tag 节点"""
        return cls(
            id=generate_id("tag", metalake, tag_name),
            name=tag_name,
            **kwargs,
        )
