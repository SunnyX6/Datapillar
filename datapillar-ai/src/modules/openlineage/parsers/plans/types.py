from __future__ import annotations

from dataclasses import dataclass, field

from src.modules.openlineage.parsers.plans.metadata import (
    AlterTablePlan,
    CatalogWritePlan,
    MetricWritePlan,
    SchemaWritePlan,
    TableWritePlan,
    TagUpdatePlan,
    TagWritePlan,
)
from src.modules.openlineage.schemas.neo4j import (
    ModifierNode,
    SQLNode,
    UnitNode,
    ValueDomainNode,
    WordRootNode,
)


@dataclass(frozen=True)
class MetadataWritePlans:
    operation: str

    table_plans: list[TableWritePlan] = field(default_factory=list)
    alter_table_plans: list[AlterTablePlan] = field(default_factory=list)
    schema_plans: list[SchemaWritePlan] = field(default_factory=list)
    catalog_plans: list[CatalogWritePlan] = field(default_factory=list)

    metric_plans: list[MetricWritePlan] = field(default_factory=list)

    wordroot_ids_to_drop: list[str] = field(default_factory=list)
    modifier_ids_to_drop: list[str] = field(default_factory=list)
    unit_ids_to_drop: list[str] = field(default_factory=list)
    valuedomain_ids_to_drop: list[str] = field(default_factory=list)
    metric_ids_to_drop: list[str] = field(default_factory=list)

    table_ids_to_drop: list[str] = field(default_factory=list)
    schema_ids_to_drop: list[str] = field(default_factory=list)
    catalog_ids_to_drop: list[str] = field(default_factory=list)

    # Tag 节点写入计划（create_tag / alter_tag）
    tag_plans: list[TagWritePlan] = field(default_factory=list)
    tag_ids_to_drop: list[str] = field(default_factory=list)

    wordroot_nodes: list[WordRootNode] = field(default_factory=list)
    modifier_nodes: list[ModifierNode] = field(default_factory=list)
    unit_nodes: list[UnitNode] = field(default_factory=list)
    valuedomain_nodes: list[ValueDomainNode] = field(default_factory=list)


@dataclass(frozen=True)
class LineageWritePlans:
    operation: str

    # ===== 结构边 =====
    catalog_schema_edges: list[tuple[str, str]] = field(default_factory=list)
    schema_table_edges: list[tuple[str, str]] = field(default_factory=list)
    table_column_edges: list[tuple[str, list[str]]] = field(default_factory=list)
    schema_metric_edges: list[tuple[str, str]] = field(default_factory=list)

    metric_parent_relationships: list[dict] = field(default_factory=list)

    # ===== SQL & 血缘 =====
    sql_node: SQLNode | None = None
    table_input_ids: list[str] = field(default_factory=list)
    table_output_ids: list[str] = field(default_factory=list)
    column_lineage_data: list[dict] = field(default_factory=list)

    # ===== 指标列血缘 =====
    metric_measures: list[tuple[str, list[str]]] = field(default_factory=list)
    metric_filters: list[tuple[str, list[str]]] = field(default_factory=list)

    # ===== 值域关系 =====
    column_valuedomain_add: list[tuple[str, str]] = field(default_factory=list)
    column_valuedomain_remove: list[tuple[str, str]] = field(default_factory=list)

    # ===== Tag 关系边（HAS_TAG）=====
    tag_update_plans: list[TagUpdatePlan] = field(default_factory=list)


@dataclass(frozen=True)
class OpenLineageWritePlans:
    metadata: MetadataWritePlans
    lineage: LineageWritePlans
