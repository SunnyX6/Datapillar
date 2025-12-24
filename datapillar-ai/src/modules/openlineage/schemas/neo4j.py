"""
Neo4j 节点和关系模型

节点唯一性通过 id 保证，层级关系通过边表达：
- Catalog -[:HAS_SCHEMA]-> Schema
- Schema -[:HAS_TABLE]-> Table
- Table -[:HAS_COLUMN]-> Column
"""

import hashlib
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any


def generate_id(*parts: str) -> str:
    """生成唯一 ID"""
    content = ":".join(str(p) for p in parts if p)
    return hashlib.sha256(content.encode()).hexdigest()[:16]


@dataclass
class CatalogNode:
    """Catalog 节点"""

    id: str  # 唯一标识
    name: str
    metalake: str | None = None
    catalog_type: str | None = None
    provider: str | None = None
    description: str | None = None
    properties: dict[str, str] | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None

    @classmethod
    def create(cls, metalake: str, catalog_name: str, **kwargs) -> "CatalogNode":
        """工厂方法：创建 Catalog 节点"""
        node_id = generate_id("catalog", metalake, catalog_name)
        return cls(id=node_id, name=catalog_name, metalake=metalake, **kwargs)


@dataclass
class SchemaNode:
    """Schema 节点"""

    id: str  # 唯一标识
    name: str
    description: str | None = None
    properties: dict[str, str] | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None

    @classmethod
    def create(cls, metalake: str, catalog: str, schema_name: str, **kwargs) -> "SchemaNode":
        """工厂方法：创建 Schema 节点"""
        node_id = generate_id("schema", metalake, catalog, schema_name)
        return cls(id=node_id, name=schema_name, **kwargs)


@dataclass
class TableNode:
    """Table 节点"""

    id: str  # 唯一标识
    name: str  # 表名（简称）
    producer: str | None = None
    description: str | None = None
    properties: dict[str, str] | None = None
    partitions: str | None = None
    distribution: str | None = None
    sort_orders: str | None = None
    indexes: str | None = None
    creator: str | None = None
    create_time: str | None = None
    last_modifier: str | None = None
    last_modified_time: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None

    @classmethod
    def create(
        cls, metalake: str, catalog: str, schema: str, table_name: str, **kwargs
    ) -> "TableNode":
        """工厂方法：创建 Table 节点"""
        node_id = generate_id("table", metalake, catalog, schema, table_name)
        return cls(id=node_id, name=table_name, **kwargs)


@dataclass
class ColumnNode:
    """Column 节点"""

    id: str  # 唯一标识
    name: str
    data_type: str | None = None
    description: str | None = None
    nullable: bool | None = None
    auto_increment: bool | None = None
    default_value: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None

    @classmethod
    def create(
        cls,
        metalake: str,
        catalog: str,
        schema: str,
        table: str,
        column_name: str,
        **kwargs,
    ) -> "ColumnNode":
        """工厂方法：创建 Column 节点"""
        node_id = generate_id("column", metalake, catalog, schema, table, column_name)
        return cls(id=node_id, name=column_name, **kwargs)


@dataclass
class SQLNode:
    """SQL 节点"""

    id: str  # 唯一标识
    content: str
    dialect: str | None = None
    engine: str | None = None
    job_namespace: str | None = None
    job_name: str | None = None
    execution_count: int = 1
    created_at: datetime | None = None
    updated_at: datetime | None = None

    @classmethod
    def create(cls, sql: str, job_namespace: str, job_name: str, **kwargs) -> "SQLNode":
        """工厂方法：创建 SQL 节点"""
        # SQL 节点唯一性只基于 SQL 内容，同一条 SQL 只有一个节点
        normalized_sql = " ".join(sql.lower().split())
        node_id = generate_id("sql", normalized_sql)
        return cls(
            id=node_id,
            content=sql,
            job_namespace=job_namespace,
            job_name=job_name,
            **kwargs,
        )


@dataclass
class MetricNode:
    """指标节点"""

    id: str  # 唯一标识
    name: str
    code: str
    metric_type: str  # ATOMIC, DERIVED, COMPOSITE
    description: str | None = None
    unit: str | None = None
    aggregation_logic: str | None = None
    calculation_formula: str | None = None
    parent_metric_codes: list[str] = field(default_factory=list)
    embedding: list[float] | None = None
    created_at: datetime | None = None

    @classmethod
    def create(cls, code: str, name: str, metric_type: str, **kwargs) -> "MetricNode":
        """工厂方法：创建 Metric 节点"""
        node_id = generate_id("metric", code)
        return cls(id=node_id, name=name, code=code, metric_type=metric_type, **kwargs)


@dataclass
class WordRootNode:
    """词根节点"""

    id: str  # 唯一标识
    code: str
    name: str | None = None
    data_type: str | None = None
    description: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None

    @classmethod
    def create(cls, code: str, **kwargs) -> "WordRootNode":
        """工厂方法：创建 WordRoot 节点"""
        node_id = generate_id("wordroot", code)
        return cls(id=node_id, code=code, **kwargs)


@dataclass
class ModifierNode:
    """修饰符节点"""

    id: str  # 唯一标识
    code: str
    modifier_type: str  # PREFIX, SUFFIX, INFIX
    description: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None

    @classmethod
    def create(cls, code: str, modifier_type: str, **kwargs) -> "ModifierNode":
        """工厂方法：创建 Modifier 节点"""
        node_id = generate_id("modifier", code)
        return cls(id=node_id, code=code, modifier_type=modifier_type, **kwargs)


@dataclass
class UnitNode:
    """单位节点"""

    id: str  # 唯一标识
    code: str
    name: str | None = None
    symbol: str | None = None
    description: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None

    @classmethod
    def create(cls, code: str, **kwargs) -> "UnitNode":
        """工厂方法：创建 Unit 节点"""
        node_id = generate_id("unit", code)
        return cls(id=node_id, code=code, **kwargs)


@dataclass
class ValueDomainNode:
    """值域节点"""

    id: str  # 唯一标识
    domain_code: str
    domain_type: str  # ENUM, RANGE, REGEX
    item_value: str
    domain_name: str | None = None
    item_label: str | None = None
    description: str | None = None
    embedding: list[float] | None = None
    created_at: datetime | None = None

    @classmethod
    def create(cls, domain_code: str, item_value: str, domain_type: str, **kwargs) -> "ValueDomainNode":
        """工厂方法：创建 ValueDomain 节点"""
        node_id = generate_id("valuedomain", domain_code, item_value)
        return cls(id=node_id, domain_code=domain_code, domain_type=domain_type, item_value=item_value, **kwargs)


@dataclass
class TableLineage:
    """表级血缘关系"""

    source_table_id: str
    target_table_id: str
    sql_id: str | None = None
    created_at: datetime | None = None


@dataclass
class ColumnLineage:
    """列级血缘关系"""

    source_column_id: str
    target_column_id: str
    transformation_description: str | None = None
    transformation_type: str | None = None
    created_at: datetime | None = None


@dataclass
class MetricColumnLineage:
    """原子指标与列的血缘关系"""

    metric_id: str
    column_id: str
    lineage_type: str  # MEASURES, FILTERS_BY
    created_at: datetime | None = None
