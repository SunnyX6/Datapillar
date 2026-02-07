# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 元数据 plans 解析器（解析 → Neo4j DTO / WritePlans）

定位：
- 只负责从 OpenLineage RunEvent 解析出“节点写入所需的 DTO/Plan”
- writers 只消费 plans，不在 writer 层做任何事件解析/映射

约束：
- Parser 无副作用：不触碰 Neo4j、不调用 Repository、不做写入
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal

from src.infrastructure.repository.knowledge.dto import (
    CatalogDTO,
    ColumnDTO,
    MetricDTO,
    ModifierDTO,
    SchemaDTO,
    TableDTO,
    TagDTO,
    UnitDTO,
    ValueDomainDTO,
    WordRootDTO,
    generate_id,
    get_metric_label,
)
from src.modules.openlineage.parsers.common.namespace import (
    dataset_table_name,
    parse_gravitino_namespace,
)
from src.modules.openlineage.parsers.common.qualified_name import (
    parse_schema_table,
    parse_table_column,
    split_schema_object,
)
from src.modules.openlineage.schemas.events import Dataset, RunEvent
from src.modules.openlineage.schemas.facets import (
    GravitinoDatasetFacet,
    SchemaDatasetFacet,
    TableChangeInfo,
)

GRAVITINO_FACET_KEY = "gravitino"


@dataclass(frozen=True)
class ParsedNamespace:
    metalake: str | None
    catalog: str | None
    raw: str


@dataclass(frozen=True)
class TableWritePlan:
    catalog: CatalogDTO
    schema: SchemaDTO
    table: TableDTO
    columns: list[ColumnDTO]


@dataclass(frozen=True)
class SchemaWritePlan:
    catalog: CatalogDTO
    schema: SchemaDTO


@dataclass(frozen=True)
class CatalogWritePlan:
    catalog: CatalogDTO


@dataclass(slots=True)
class _AlterTableState:
    name: str
    table_id: str


@dataclass(frozen=True)
class MetricWritePlan:
    catalog: CatalogDTO
    schema: SchemaDTO
    schema_id: str
    metric: MetricDTO
    metric_label: str
    is_atomic: bool
    parent_metric_codes: list[str]


@dataclass(frozen=True)
class TagUpdatePlan:
    """Tag 关联计划 - 用于 associate_tags 操作"""

    object_type: str  # CATALOG, SCHEMA, TABLE, COLUMN
    node_id: str  # 被关联对象的 ID
    node_label: str  # 被关联对象的 Neo4j Label
    metalake: str  # Tag 所属的 metalake
    tags_to_add: list[str]  # 需要添加的 Tag 名称
    tags_to_remove: list[str]  # 需要移除的 Tag 名称


@dataclass(frozen=True)
class TagWritePlan:
    """Tag 节点写入计划 - 用于 create_tag/alter_tag 操作"""

    tag: TagDTO


# ===== alter_table actions =====


@dataclass(frozen=True)
class RenameTableAction:
    old_table_id: str
    schema_id: str
    new_table: TableDTO


@dataclass(frozen=True)
class UpdateTableCommentAction:
    table_id: str
    new_comment: str | None


@dataclass(frozen=True)
class UpdateTablePropertiesAction:
    table_id: str
    properties: dict[str, str] | None


@dataclass(frozen=True)
class AddColumnAction:
    table_id: str
    column: ColumnDTO

    # 来自 change（OpenLineage 事件）
    nullable: bool | None
    auto_increment: bool | None
    default_value: str | None


@dataclass(frozen=True)
class DeleteColumnAction:
    column_id: str


@dataclass(frozen=True)
class RenameColumnAction:
    table_id: str
    old_column_id: str
    new_column_id: str
    new_column_name: str


ColumnPropertyName = Literal["description", "dataType", "nullable", "defaultValue"]


@dataclass(frozen=True)
class UpdateColumnPropertyAction:
    column_id: str
    property_name: ColumnPropertyName
    value: Any


AlterTableAction = (
    RenameTableAction
    | UpdateTableCommentAction
    | UpdateTablePropertiesAction
    | AddColumnAction
    | DeleteColumnAction
    | RenameColumnAction
    | UpdateColumnPropertyAction
)


@dataclass(frozen=True)
class AlterTablePlan:
    actions: list[AlterTableAction]


class OpenLineageMetadataPlanParser:
    """OpenLineage 元数据 plans 解析（只负责产出 DTO/写入计划）"""

    VALUE_DOMAIN_TAG_PREFIX = "vd:"

    @staticmethod
    def parse_namespace(namespace: str) -> ParsedNamespace:
        parsed = parse_gravitino_namespace(namespace or "")
        if not parsed:
            return ParsedNamespace(metalake=None, catalog=None, raw=(namespace or ""))
        return ParsedNamespace(metalake=parsed[0], catalog=parsed[1], raw=(namespace or ""))

    @staticmethod
    def parse_dataset_name(name: str) -> tuple[str | None, str]:
        return split_schema_object(name or "")

    @staticmethod
    def parse_gravitino_facet(dataset: Dataset) -> GravitinoDatasetFacet | None:
        if not dataset.facets or GRAVITINO_FACET_KEY not in dataset.facets:
            return None
        facet = dataset.facets.get(GRAVITINO_FACET_KEY)
        if not isinstance(facet, dict):
            return None
        return GravitinoDatasetFacet.from_dict(facet)

    @staticmethod
    def tag_node_label(object_type: str) -> str:
        label_map = {
            "CATALOG": "Catalog",
            "SCHEMA": "Schema",
            "TABLE": "Table",
            "COLUMN": "Column",
        }
        return label_map.get((object_type or "").upper(), "Knowledge")

    @classmethod
    def tag_node_id(
        cls,
        parsed_ns: ParsedNamespace,
        name: str,
        object_type: str,
    ) -> str | None:
        object_type_upper = (object_type or "").upper()
        name = name or ""

        if object_type_upper == "CATALOG":
            if parsed_ns.metalake:
                return generate_id("catalog", parsed_ns.metalake, name)
            return None

        if object_type_upper == "SCHEMA":
            if parsed_ns.metalake and parsed_ns.catalog:
                return generate_id("schema", parsed_ns.metalake, parsed_ns.catalog, name)
            return None

        if object_type_upper == "TABLE":
            if parsed_ns.metalake and parsed_ns.catalog:
                parsed_table = parse_schema_table(name)
                if parsed_table:
                    schema_name, table_name = parsed_table
                    return generate_id(
                        "table", parsed_ns.metalake, parsed_ns.catalog, schema_name, table_name
                    )
            return None

        if object_type_upper == "COLUMN":
            if parsed_ns.metalake and parsed_ns.catalog:
                parsed_column = parse_table_column(name)
                if parsed_column:
                    schema_name, table_name, column_name = parsed_column
                    return generate_id(
                        "column",
                        parsed_ns.metalake,
                        parsed_ns.catalog,
                        schema_name,
                        table_name,
                        column_name,
                    )
            return None

        return None

    # ===== physical assets =====

    @classmethod
    def parse_observed_plans(cls, event: RunEvent) -> list[TableWritePlan]:
        """
        从“通用 OpenLineage 事件”（如 Spark/Flink）中补齐 Table/Column 节点写入计划。

        适用场景：
        - dataset.namespace 非 gravitino://（无法走 GravitinoFacet 解析）
        - 但 job.namespace 是 gravitino://{metalake}/{catalog}，并且 dataset.facets.symlinks/schema 可提供逻辑表名与字段

        注意：这里只写节点，不写任何关系。
        """
        job_ns = cls.parse_namespace(event.job.namespace if event.job else "")
        if not job_ns.metalake or not job_ns.catalog:
            return []

        by_table_id: dict[str, TableWritePlan] = {}

        for dataset in event.get_all_datasets():
            table_name = dataset_table_name(dataset.namespace, dataset.name, dataset.facets)
            if not table_name:
                continue

            parsed_table = parse_schema_table(table_name)
            if not parsed_table:
                continue
            schema_name, table_only = parsed_table

            catalog_node = CatalogDTO.create(
                metalake=job_ns.metalake,
                catalog_name=job_ns.catalog,
            )
            schema_node = SchemaDTO.create(
                metalake=job_ns.metalake,
                catalog=job_ns.catalog,
                schema_name=schema_name,
            )
            table_node = TableDTO.create(
                metalake=job_ns.metalake,
                catalog=job_ns.catalog,
                schema=schema_name,
                table_name=table_only,
                producer=event.producer,
            )

            columns: list[ColumnDTO] = []
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field_info in schema_facet.fields:
                    columns.append(
                        ColumnDTO.create(
                            metalake=job_ns.metalake,
                            catalog=job_ns.catalog,
                            schema=schema_name,
                            table=table_only,
                            column_name=field_info.name,
                            data_type=field_info.type,
                            description=field_info.description,
                        )
                    )

            existing = by_table_id.get(table_node.id)
            if not existing:
                by_table_id[table_node.id] = TableWritePlan(
                    catalog=catalog_node,
                    schema=schema_node,
                    table=table_node,
                    columns=columns,
                )
                continue

            if not columns:
                continue

            merged: dict[str, ColumnDTO] = {c.id: c for c in existing.columns}
            for c in columns:
                merged.setdefault(c.id, c)
            by_table_id[table_node.id] = TableWritePlan(
                catalog=existing.catalog,
                schema=existing.schema,
                table=existing.table,
                columns=list(merged.values()),
            )

        return list(by_table_id.values())

    @classmethod
    def parse_table_plans(cls, event: RunEvent) -> list[TableWritePlan]:
        plans: list[TableWritePlan] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            schema_name, table_name = cls.parse_dataset_name(dataset.name)

            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                continue

            gravitino_facet = cls.parse_gravitino_facet(dataset)

            catalog_node = CatalogDTO.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            schema_node = SchemaDTO.create(
                metalake=parsed_ns.metalake,
                catalog=parsed_ns.catalog,
                schema_name=schema_name,
            )

            table_node = TableDTO.create(
                metalake=parsed_ns.metalake,
                catalog=parsed_ns.catalog,
                schema=schema_name,
                table_name=table_name,
                producer=event.producer,
            )

            if gravitino_facet:
                table_node.description = gravitino_facet.description
                table_node.properties = gravitino_facet.properties
                table_node.partitions = gravitino_facet.partitions
                table_node.distribution = gravitino_facet.distribution
                table_node.sort_orders = gravitino_facet.sortOrders
                table_node.indexes = gravitino_facet.indexes
                table_node.creator = gravitino_facet.creator
                table_node.create_time = gravitino_facet.createTime
                table_node.last_modifier = gravitino_facet.lastModifier
                table_node.last_modified_time = gravitino_facet.lastModifiedTime

            columns: list[ColumnDTO] = []
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field_info in schema_facet.fields:
                    column = ColumnDTO.create(
                        metalake=parsed_ns.metalake,
                        catalog=parsed_ns.catalog,
                        schema=schema_name,
                        table=table_name,
                        column_name=field_info.name,
                        data_type=field_info.type,
                        description=field_info.description,
                    )
                    if gravitino_facet:
                        col_meta = gravitino_facet.get_column_metadata(field_info.name)
                        if col_meta:
                            column.nullable = col_meta.nullable
                            column.auto_increment = col_meta.autoIncrement
                            column.default_value = col_meta.defaultValue
                    columns.append(column)

            plans.append(
                TableWritePlan(
                    catalog=catalog_node,
                    schema=schema_node,
                    table=table_node,
                    columns=columns,
                )
            )
        return plans

    @classmethod
    def parse_schema_plans(cls, event: RunEvent) -> list[SchemaWritePlan]:
        plans: list[SchemaWritePlan] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            schema_name = dataset.name or ""
            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                continue

            gravitino_facet = cls.parse_gravitino_facet(dataset)

            catalog_node = CatalogDTO.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            schema_node = SchemaDTO.create(
                metalake=parsed_ns.metalake,
                catalog=parsed_ns.catalog,
                schema_name=schema_name,
            )
            if gravitino_facet and gravitino_facet.description:
                schema_node.description = gravitino_facet.description

            plans.append(SchemaWritePlan(catalog=catalog_node, schema=schema_node))
        return plans

    @classmethod
    def parse_catalog_plans(cls, event: RunEvent) -> list[CatalogWritePlan]:
        plans: list[CatalogWritePlan] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            catalog_name = dataset.name or ""
            if not parsed_ns.metalake or not catalog_name:
                continue
            catalog_node = CatalogDTO.create(metalake=parsed_ns.metalake, catalog_name=catalog_name)
            plans.append(CatalogWritePlan(catalog=catalog_node))
        return plans

    @classmethod
    def drop_table_ids(cls, event: RunEvent) -> list[str]:
        table_ids: list[str] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            schema_name, table_name = cls.parse_dataset_name(dataset.name)
            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                continue
            table_ids.append(
                generate_id("table", parsed_ns.metalake, parsed_ns.catalog, schema_name, table_name)
            )
        return table_ids

    @classmethod
    def drop_schema_ids(cls, event: RunEvent) -> list[str]:
        schema_ids: list[str] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            schema_name = dataset.name or ""
            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                continue
            schema_ids.append(
                generate_id("schema", parsed_ns.metalake, parsed_ns.catalog, schema_name)
            )
        return schema_ids

    @classmethod
    def drop_catalog_ids(cls, event: RunEvent) -> list[str]:
        catalog_ids: list[str] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            catalog_name = dataset.name or ""
            if not parsed_ns.metalake or not catalog_name:
                continue
            catalog_ids.append(generate_id("catalog", parsed_ns.metalake, catalog_name))
        return catalog_ids

    @classmethod
    def parse_alter_table(cls, event: RunEvent) -> list[AlterTablePlan]:
        from src.modules.openlineage.schemas.facets import TableChangeType

        plans: list[AlterTablePlan] = []
        for dataset in event.get_all_datasets():
            plan = cls._parse_alter_plan(event, dataset, TableChangeType)
            if plan:
                plans.append(plan)
        return plans

    @classmethod
    def _parse_alter_plan(
        cls, event: RunEvent, dataset: Dataset, table_change_type
    ) -> AlterTablePlan | None:
        parsed_ns = cls.parse_namespace(dataset.namespace)
        schema_name, table_name = cls.parse_dataset_name(dataset.name)
        if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
            return None

        gravitino_facet = cls.parse_gravitino_facet(dataset)
        if not gravitino_facet or not gravitino_facet.changes:
            return None

        schema_id = generate_id("schema", parsed_ns.metalake, parsed_ns.catalog, schema_name)
        table_state = _AlterTableState(
            name=table_name,
            table_id=generate_id(
                "table", parsed_ns.metalake, parsed_ns.catalog, schema_name, table_name
            ),
        )

        actions: list[AlterTableAction] = []
        for change in gravitino_facet.changes:
            action = cls._parse_alter_action(
                event=event,
                parsed_ns=parsed_ns,
                schema_name=schema_name,
                schema_id=schema_id,
                gravitino_facet=gravitino_facet,
                change=change,
                table_state=table_state,
                table_change_type=table_change_type,
            )
            if action:
                actions.append(action)

        return AlterTablePlan(actions=actions) if actions else None

    @classmethod
    def _parse_alter_action(
        cls,
        *,
        event: RunEvent,
        parsed_ns: ParsedNamespace,
        schema_name: str,
        schema_id: str,
        gravitino_facet: GravitinoDatasetFacet,
        change: TableChangeInfo,
        table_state: _AlterTableState,
        table_change_type,
    ) -> AlterTableAction | None:
        change_type = change.type
        action: AlterTableAction | None = None

        if change_type == table_change_type.RENAME_TABLE:
            action = cls._handle_rename_table(
                event=event,
                parsed_ns=parsed_ns,
                schema_name=schema_name,
                schema_id=schema_id,
                gravitino_facet=gravitino_facet,
                change=change,
                table_state=table_state,
            )
        elif change_type == table_change_type.UPDATE_COMMENT:
            action = UpdateTableCommentAction(
                table_id=table_state.table_id, new_comment=change.newComment
            )
        elif change_type == table_change_type.SET_PROPERTY:
            action = UpdateTablePropertiesAction(
                table_id=table_state.table_id,
                properties=gravitino_facet.properties,
            )
        elif change_type == table_change_type.ADD_COLUMN:
            if change.columnName:
                column = ColumnDTO.create(
                    metalake=parsed_ns.metalake,
                    catalog=parsed_ns.catalog,
                    schema=schema_name,
                    table=table_state.name,
                    column_name=change.columnName,
                    data_type=change.dataType,
                    description=change.columnComment,
                )
                action = AddColumnAction(
                    table_id=table_state.table_id,
                    column=column,
                    nullable=change.nullable,
                    auto_increment=change.autoIncrement,
                    default_value=change.defaultValue,
                )
        elif change_type == table_change_type.DELETE_COLUMN:
            if change.columnName:
                action = DeleteColumnAction(
                    column_id=cls._column_id(
                        parsed_ns=parsed_ns,
                        schema_name=schema_name,
                        table_name=table_state.name,
                        column_name=change.columnName,
                    )
                )
        elif change_type == table_change_type.RENAME_COLUMN:
            if change.oldColumnName and change.newColumnName:
                action = RenameColumnAction(
                    table_id=table_state.table_id,
                    old_column_id=cls._column_id(
                        parsed_ns=parsed_ns,
                        schema_name=schema_name,
                        table_name=table_state.name,
                        column_name=change.oldColumnName,
                    ),
                    new_column_id=cls._column_id(
                        parsed_ns=parsed_ns,
                        schema_name=schema_name,
                        table_name=table_state.name,
                        column_name=change.newColumnName,
                    ),
                    new_column_name=change.newColumnName,
                )
        else:
            property_name_by_type: dict[Any, str] = {
                table_change_type.UPDATE_COLUMN_COMMENT: "description",
                table_change_type.UPDATE_COLUMN_TYPE: "dataType",
                table_change_type.UPDATE_COLUMN_NULLABILITY: "nullable",
                table_change_type.UPDATE_COLUMN_DEFAULT_VALUE: "defaultValue",
            }
            property_name = property_name_by_type.get(change_type)
            if property_name and change.columnName:
                value = getattr(
                    change,
                    {
                        "description": "newComment",
                        "dataType": "dataType",
                        "nullable": "nullable",
                        "defaultValue": "defaultValue",
                    }[property_name],
                    None,
                )
                action = UpdateColumnPropertyAction(
                    column_id=cls._column_id(
                        parsed_ns=parsed_ns,
                        schema_name=schema_name,
                        table_name=table_state.name,
                        column_name=change.columnName,
                    ),
                    property_name=property_name,
                    value=value,
                )

        return action

    @classmethod
    def _handle_rename_table(
        cls,
        *,
        event: RunEvent,
        parsed_ns: ParsedNamespace,
        schema_name: str,
        schema_id: str,
        gravitino_facet: GravitinoDatasetFacet,
        change: TableChangeInfo,
        table_state: _AlterTableState,
    ) -> RenameTableAction | None:
        if not change.newName:
            return None
        old_table_id = table_state.table_id
        new_table_name = change.newName
        new_table_node = TableDTO.create(
            metalake=parsed_ns.metalake,
            catalog=parsed_ns.catalog,
            schema=schema_name,
            table_name=new_table_name,
            producer=event.producer,
        )
        new_table_node.description = gravitino_facet.description
        new_table_node.properties = gravitino_facet.properties
        new_table_node.creator = gravitino_facet.creator
        new_table_node.create_time = gravitino_facet.createTime
        new_table_node.last_modifier = gravitino_facet.lastModifier
        new_table_node.last_modified_time = gravitino_facet.lastModifiedTime

        table_state.name = new_table_name
        table_state.table_id = new_table_node.id

        return RenameTableAction(
            old_table_id=old_table_id,
            schema_id=schema_id,
            new_table=new_table_node,
        )

    @classmethod
    def _column_id(
        cls,
        *,
        parsed_ns: ParsedNamespace,
        schema_name: str,
        table_name: str,
        column_name: str,
    ) -> str:
        return generate_id(
            "column",
            parsed_ns.metalake,
            parsed_ns.catalog,
            schema_name,
            table_name,
            column_name,
        )

    # ===== semantic assets =====

    @classmethod
    def parse_metric_plans(cls, event: RunEvent) -> list[MetricWritePlan]:
        plans: list[MetricWritePlan] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            schema_name, metric_name = cls.parse_dataset_name(dataset.name)
            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                continue
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            metric_type_field = fields.get("metricType")
            if not code_field or not metric_type_field:
                continue

            name_field = fields.get("name")
            comment_field = fields.get("comment")
            unit_field = fields.get("unit")
            aggregation_logic_field = fields.get("aggregationLogic")
            calculation_formula_field = fields.get("calculationFormula")
            parent_metric_codes_field = fields.get("parentMetricCodes")

            parent_metric_codes: list[str] = []
            if parent_metric_codes_field and parent_metric_codes_field.description:
                parent_metric_codes = [
                    code.strip()
                    for code in parent_metric_codes_field.description.split(",")
                    if code.strip()
                ]

            catalog_node = CatalogDTO.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            schema_node = SchemaDTO.create(
                metalake=parsed_ns.metalake,
                catalog=parsed_ns.catalog,
                schema_name=schema_name,
            )
            schema_id = schema_node.id

            metric_type = metric_type_field.description or "ATOMIC"
            metric_label = get_metric_label(metric_type)
            is_atomic = metric_type.upper() == "ATOMIC"

            metric_node = MetricDTO.create(
                code=(code_field.description or metric_name),
                name=name_field.description if name_field else metric_name,
                metric_type=metric_type,
                description=comment_field.description if comment_field else None,
                unit=unit_field.description if unit_field else None,
                aggregation_logic=(
                    aggregation_logic_field.description if aggregation_logic_field else None
                ),
                calculation_formula=(
                    calculation_formula_field.description if calculation_formula_field else None
                ),
                parent_metric_codes=parent_metric_codes,
            )

            plans.append(
                MetricWritePlan(
                    catalog=catalog_node,
                    schema=schema_node,
                    schema_id=schema_id,
                    metric=metric_node,
                    metric_label=metric_label,
                    is_atomic=is_atomic,
                    parent_metric_codes=parent_metric_codes,
                )
            )
        return plans

    @classmethod
    def parse_wordroot_nodes(cls, event: RunEvent) -> list[WordRootDTO]:
        nodes: list[WordRootDTO] = []
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue
            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}
            code_field = fields.get("code")
            if not code_field or not code_field.description:
                continue
            code = code_field.description
            name_field = fields.get("name")
            data_type_field = fields.get("dataType")
            comment_field = fields.get("comment")
            nodes.append(
                WordRootDTO.create(
                    code=code,
                    name=name_field.description if name_field else None,
                    data_type=data_type_field.description if data_type_field else None,
                    description=comment_field.description if comment_field else None,
                )
            )
        return nodes

    @classmethod
    def parse_modifier_nodes(cls, event: RunEvent) -> list[ModifierDTO]:
        nodes: list[ModifierDTO] = []
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue
            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}
            code_field = fields.get("code")
            modifier_type_field = fields.get("modifierType")
            if not code_field or not code_field.description or not modifier_type_field:
                continue
            name_field = fields.get("name")
            comment_field = fields.get("comment")
            nodes.append(
                ModifierDTO.create(
                    code=code_field.description,
                    name=name_field.description if name_field else code_field.description,
                    modifier_type=modifier_type_field.description or "PREFIX",
                    description=comment_field.description if comment_field else None,
                )
            )
        return nodes

    @classmethod
    def parse_unit_nodes(cls, event: RunEvent) -> list[UnitDTO]:
        nodes: list[UnitDTO] = []
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue
            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}
            code_field = fields.get("code")
            if not code_field or not code_field.description:
                continue
            name_field = fields.get("name")
            symbol_field = fields.get("symbol")
            comment_field = fields.get("comment")
            nodes.append(
                UnitDTO.create(
                    code=code_field.description,
                    name=name_field.description if name_field else None,
                    symbol=symbol_field.description if symbol_field else None,
                    description=comment_field.description if comment_field else None,
                )
            )
        return nodes

    @classmethod
    def parse_valuedomain_nodes(cls, event: RunEvent) -> list[ValueDomainDTO]:
        nodes: list[ValueDomainDTO] = []
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue
            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            domain_type_field = fields.get("domainType")
            domain_level_field = fields.get("domainLevel")
            if not code_field or not domain_type_field or not domain_level_field:
                continue
            code = (code_field.description or "").strip()
            if not code:
                continue

            name_field = fields.get("name")
            items_field = fields.get("items")
            comment_field = fields.get("comment")
            data_type_field = fields.get("dataType")

            nodes.append(
                ValueDomainDTO.create(
                    code=code,
                    domain_type=domain_type_field.description or "ENUM",
                    domain_level=domain_level_field.description or "GLOBAL",
                    name=name_field.description if name_field else None,
                    items=items_field.description if items_field else None,
                    description=comment_field.description if comment_field else None,
                    data_type=data_type_field.description if data_type_field else None,
                )
            )
        return nodes

    @classmethod
    def drop_metric_ids(cls, event: RunEvent) -> list[str]:
        metric_ids: list[str] = []
        for dataset in event.get_all_datasets():
            _schema_name, metric_name = cls.parse_dataset_name(dataset.name)
            metric_code = metric_name
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        metric_code = field.description
                        break
            if metric_code:
                metric_ids.append(generate_id("metric", metric_code))
        return metric_ids

    @classmethod
    def drop_wordroot_ids(cls, event: RunEvent) -> list[str]:
        ids: list[str] = []
        for dataset in event.get_all_datasets():
            code = None
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        code = field.description
                        break
            if not code:
                code = dataset.name.split(".")[-1] if "." in (dataset.name or "") else dataset.name
            if code:
                ids.append(generate_id("wordroot", code))
        return ids

    @classmethod
    def drop_modifier_ids(cls, event: RunEvent) -> list[str]:
        ids: list[str] = []
        for dataset in event.get_all_datasets():
            code = None
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        code = field.description
                        break
            if not code:
                code = dataset.name.split(".")[-1] if "." in (dataset.name or "") else dataset.name
            if code:
                ids.append(generate_id("modifier", code))
        return ids

    @classmethod
    def drop_unit_ids(cls, event: RunEvent) -> list[str]:
        ids: list[str] = []
        for dataset in event.get_all_datasets():
            code = None
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        code = field.description
                        break
            if not code:
                code = dataset.name.split(".")[-1] if "." in (dataset.name or "") else dataset.name
            if code:
                ids.append(generate_id("unit", code))
        return ids

    @classmethod
    def drop_valuedomain_ids(cls, event: RunEvent) -> list[str]:
        ids: list[str] = []
        for dataset in event.get_all_datasets():
            code = None
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        code = field.description
                        break
            if code:
                ids.append(generate_id("valuedomain", code))
        return ids

    # ===== tags =====

    @classmethod
    def parse_tag_nodes(cls, event: RunEvent) -> list[TagWritePlan]:
        """解析 create_tag / alter_tag 事件，提取 Tag 节点写入计划"""
        plans: list[TagWritePlan] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            if not parsed_ns.metalake:
                continue

            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            name_field = fields.get("name")
            if not name_field or not name_field.description:
                continue

            tag_name = name_field.description
            comment_field = fields.get("comment")
            properties_field = fields.get("properties")

            properties: dict[str, str] | None = None
            if properties_field and properties_field.description:
                properties = {}
                for pair in properties_field.description.split(","):
                    if "=" in pair:
                        k, v = pair.split("=", 1)
                        properties[k.strip()] = v.strip()

            tag_node = TagDTO.create(
                metalake=parsed_ns.metalake,
                tag_name=tag_name,
                description=comment_field.description if comment_field else None,
                properties=properties,
            )

            plans.append(TagWritePlan(tag=tag_node))
        return plans

    @classmethod
    def drop_tag_ids(cls, event: RunEvent) -> list[str]:
        """解析 drop_tag 事件，提取要删除的 Tag ID"""
        ids: list[str] = []
        for dataset in event.get_all_datasets():
            parsed_ns = cls.parse_namespace(dataset.namespace)
            if not parsed_ns.metalake:
                continue

            tag_name = dataset.name
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "name" and field.description:
                        tag_name = field.description
                        break

            if tag_name:
                ids.append(generate_id("tag", parsed_ns.metalake, tag_name))
        return ids

    @classmethod
    def parse_tag_plans(cls, event: RunEvent) -> list[TagUpdatePlan]:
        """解析 associate_tags 事件，提取 HAS_TAG 关系边更新计划"""
        plans: list[TagUpdatePlan] = []
        for dataset in event.get_all_datasets():
            if not dataset.facets or "gravitinoTag" not in dataset.facets:
                continue
            tag_facet = dataset.facets.get("gravitinoTag")
            if not isinstance(tag_facet, dict):
                continue
            object_type = tag_facet.get("objectType")
            if not object_type:
                continue

            parsed_ns = cls.parse_namespace(dataset.namespace)
            if not parsed_ns.metalake:
                continue

            node_id = cls.tag_node_id(parsed_ns, dataset.name, object_type)
            if not node_id:
                continue

            node_label = cls.tag_node_label(object_type)

            # 提取 tagsToAdd 和 tagsToRemove
            tags_to_add_raw = tag_facet.get("tagsToAdd", [])
            tags_to_remove_raw = tag_facet.get("tagsToRemove", [])

            if not isinstance(tags_to_add_raw, list):
                tags_to_add_raw = []
            if not isinstance(tags_to_remove_raw, list):
                tags_to_remove_raw = []

            # 过滤 vd: 前缀的值域标签
            tags_to_add = [
                t
                for t in tags_to_add_raw
                if isinstance(t, str) and t and not t.startswith(cls.VALUE_DOMAIN_TAG_PREFIX)
            ]
            tags_to_remove = [
                t
                for t in tags_to_remove_raw
                if isinstance(t, str) and t and not t.startswith(cls.VALUE_DOMAIN_TAG_PREFIX)
            ]

            if not tags_to_add and not tags_to_remove:
                continue

            plans.append(
                TagUpdatePlan(
                    object_type=str(object_type),
                    node_id=node_id,
                    node_label=node_label,
                    metalake=parsed_ns.metalake,
                    tags_to_add=tags_to_add,
                    tags_to_remove=tags_to_remove,
                )
            )
        return plans
