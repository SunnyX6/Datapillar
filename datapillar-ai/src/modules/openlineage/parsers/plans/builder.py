from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

import structlog

from src.infrastructure.repository.kg.dto import SQLDTO, generate_id
from src.modules.openlineage.parsers.common.dataset import DatasetResolver
from src.modules.openlineage.parsers.common.operation import get_operation
from src.modules.openlineage.parsers.common.qualified_name import (
    parse_schema_table,
    parse_table_column,
)
from src.modules.openlineage.parsers.plans.metadata import (
    AddColumnAction,
    OpenLineageMetadataPlanParser,
    RenameColumnAction,
    RenameTableAction,
)
from src.modules.openlineage.parsers.plans.types import (
    LineageWritePlans,
    MetadataWritePlans,
    OpenLineageWritePlans,
)
from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.schemas.facets import ColumnLineageDatasetFacet, SchemaDatasetFacet
from src.shared.utils.sql_lineage import SQLLineageAnalyzer

logger = structlog.get_logger()


class OpenLineagePlanBuilder:
    """
    OpenLineage 写入计划构建器

    核心原则：
    - 只做解析与计划构建（无副作用）
    - event_processor 构建 plans 后交给 writers 写入
    """

    VALUE_DOMAIN_TAG_PREFIX = "vd:"

    def __init__(self) -> None:
        self._resolver = DatasetResolver()

    def build(self, event: RunEvent) -> OpenLineageWritePlans:
        operation = get_operation(event)
        metadata = self._build_metadata_plans(event, operation)
        lineage = self._build_lineage_plans(event, metadata)
        return OpenLineageWritePlans(metadata=metadata, lineage=lineage)

    def _build_metadata_plans(self, event: RunEvent, operation: str) -> MetadataWritePlans:
        parsers: dict[str, tuple[str, Callable[[RunEvent], Any]]] = {
            # ===== physical assets =====
            "create_table": ("table_plans", OpenLineageMetadataPlanParser.parse_table_plans),
            "load_table": ("table_plans", OpenLineageMetadataPlanParser.parse_table_plans),
            "alter_table": ("alter_table_plans", OpenLineageMetadataPlanParser.parse_alter_table),
            "create_schema": ("schema_plans", OpenLineageMetadataPlanParser.parse_schema_plans),
            "alter_schema": ("schema_plans", OpenLineageMetadataPlanParser.parse_schema_plans),
            "load_schema": ("schema_plans", OpenLineageMetadataPlanParser.parse_schema_plans),
            "create_catalog": ("catalog_plans", OpenLineageMetadataPlanParser.parse_catalog_plans),
            "alter_catalog": ("catalog_plans", OpenLineageMetadataPlanParser.parse_catalog_plans),
            # ===== semantic assets =====
            "register_metric": ("metric_plans", OpenLineageMetadataPlanParser.parse_metric_plans),
            "alter_metric": ("metric_plans", OpenLineageMetadataPlanParser.parse_metric_plans),
            "create_wordroot": (
                "wordroot_nodes",
                OpenLineageMetadataPlanParser.parse_wordroot_nodes,
            ),
            "alter_wordroot": (
                "wordroot_nodes",
                OpenLineageMetadataPlanParser.parse_wordroot_nodes,
            ),
            "create_modifier": (
                "modifier_nodes",
                OpenLineageMetadataPlanParser.parse_modifier_nodes,
            ),
            "alter_modifier": (
                "modifier_nodes",
                OpenLineageMetadataPlanParser.parse_modifier_nodes,
            ),
            "create_unit": ("unit_nodes", OpenLineageMetadataPlanParser.parse_unit_nodes),
            "alter_unit": ("unit_nodes", OpenLineageMetadataPlanParser.parse_unit_nodes),
            "create_valuedomain": (
                "valuedomain_nodes",
                OpenLineageMetadataPlanParser.parse_valuedomain_nodes,
            ),
            "alter_valuedomain": (
                "valuedomain_nodes",
                OpenLineageMetadataPlanParser.parse_valuedomain_nodes,
            ),
            # ===== drops =====
            "drop_table": ("table_ids_to_drop", OpenLineageMetadataPlanParser.drop_table_ids),
            "drop_schema": ("schema_ids_to_drop", OpenLineageMetadataPlanParser.drop_schema_ids),
            "drop_catalog": ("catalog_ids_to_drop", OpenLineageMetadataPlanParser.drop_catalog_ids),
            "drop_metric": ("metric_ids_to_drop", OpenLineageMetadataPlanParser.drop_metric_ids),
            "drop_wordroot": (
                "wordroot_ids_to_drop",
                OpenLineageMetadataPlanParser.drop_wordroot_ids,
            ),
            "drop_modifier": (
                "modifier_ids_to_drop",
                OpenLineageMetadataPlanParser.drop_modifier_ids,
            ),
            "drop_unit": ("unit_ids_to_drop", OpenLineageMetadataPlanParser.drop_unit_ids),
            "drop_valuedomain": (
                "valuedomain_ids_to_drop",
                OpenLineageMetadataPlanParser.drop_valuedomain_ids,
            ),
            # ===== tags =====
            "create_tag": ("tag_plans", OpenLineageMetadataPlanParser.parse_tag_nodes),
            "alter_tag": ("tag_plans", OpenLineageMetadataPlanParser.parse_tag_nodes),
            "drop_tag": ("tag_ids_to_drop", OpenLineageMetadataPlanParser.drop_tag_ids),
        }

        parser = parsers.get(operation)
        if parser:
            field, fn = parser
            return MetadataWritePlans(operation=operation, **{field: fn(event)})

        # ===== Spark/Flink 等通用事件：补齐节点写入计划（保证关系写入可 MATCH 到节点）=====
        observed_table_plans = OpenLineageMetadataPlanParser.parse_observed_plans(event)
        if observed_table_plans:
            return MetadataWritePlans(operation=operation, table_plans=observed_table_plans)

        return MetadataWritePlans(operation=operation)

    def _build_lineage_plans(
        self, event: RunEvent, metadata: MetadataWritePlans
    ) -> LineageWritePlans:
        operation = metadata.operation

        has_column_lineage_facet = any(
            output_ds.facets and "columnLineage" in output_ds.facets for output_ds in event.outputs
        )
        need_lineage_details = bool(event.get_sql()) or has_column_lineage_facet

        # ===== 结构边：HAS_*（依赖 metadata plans 的解析结果，避免重复解析）=====
        if operation in {"create_schema", "alter_schema", "load_schema"}:
            return LineageWritePlans(
                operation=operation,
                catalog_schema_edges=[(p.catalog.id, p.schema.id) for p in metadata.schema_plans],
            )

        if operation in {"create_table", "load_table"}:
            return LineageWritePlans(
                operation=operation,
                catalog_schema_edges=[(p.catalog.id, p.schema.id) for p in metadata.table_plans],
                schema_table_edges=[(p.schema.id, p.table.id) for p in metadata.table_plans],
                table_column_edges=[
                    (p.table.id, [c.id for c in p.columns])
                    for p in metadata.table_plans
                    if p.columns
                ],
                # SQL 血缘也可能存在；下面继续补充
                sql_node=self._build_sql_node(event) if event.get_sql() else None,
                table_input_ids=self._table_in_ids(event) if need_lineage_details else [],
                table_output_ids=self._table_out_ids(event) if need_lineage_details else [],
                table_input_names=self._table_in_names(event) if need_lineage_details else [],
                table_output_names=self._table_out_names(event) if need_lineage_details else [],
                column_lineage_data=self._col_lineage_data(event) if need_lineage_details else [],
            )

        if operation == "alter_table":
            schema_table_edges: list[tuple[str, str]] = []
            table_column_edges: list[tuple[str, list[str]]] = []
            for plan in metadata.alter_table_plans:
                for action in plan.actions:
                    if isinstance(action, RenameTableAction):
                        schema_table_edges.append((action.schema_id, action.new_table.id))
                    elif isinstance(action, AddColumnAction):
                        table_column_edges.append((action.table_id, [action.column.id]))
                    elif isinstance(action, RenameColumnAction):
                        table_column_edges.append((action.table_id, [action.new_column_id]))

            return LineageWritePlans(
                operation=operation,
                schema_table_edges=schema_table_edges,
                table_column_edges=table_column_edges,
                sql_node=self._build_sql_node(event) if event.get_sql() else None,
                table_input_ids=self._table_in_ids(event) if need_lineage_details else [],
                table_output_ids=self._table_out_ids(event) if need_lineage_details else [],
                table_input_names=self._table_in_names(event) if need_lineage_details else [],
                table_output_names=self._table_out_names(event) if need_lineage_details else [],
                column_lineage_data=self._col_lineage_data(event) if need_lineage_details else [],
            )

        if operation in {"register_metric", "alter_metric"}:
            schema_metric_edges: list[tuple[str, str]] = []
            metric_parent_relationships: list[dict] = []

            for p in metadata.metric_plans:
                if p.is_atomic:
                    schema_metric_edges.append((p.schema_id, p.metric.id))
                elif p.parent_metric_codes:
                    rel_type = (
                        "DERIVED_FROM"
                        if p.metric.metric_type.upper() == "DERIVED"
                        else "COMPUTED_FROM"
                    )
                    parent_ids = [generate_id("metric", code) for code in p.parent_metric_codes]
                    metric_parent_relationships.append(
                        {
                            "child_label": p.metric_label,
                            "child_id": p.metric.id,
                            "rel_type": rel_type,
                            "parent_ids": parent_ids,
                        }
                    )

            metric_measures, metric_filters = self._metric_lineage(event)

            return LineageWritePlans(
                operation=operation,
                # 指标事件会补写 Catalog/Schema 节点，需要补齐 Catalog->Schema
                catalog_schema_edges=[(p.catalog.id, p.schema.id) for p in metadata.metric_plans],
                schema_metric_edges=schema_metric_edges,
                metric_parent_relationships=metric_parent_relationships,
                metric_measures=metric_measures,
                metric_filters=metric_filters,
            )

        if operation == "associate_tags":
            add_pairs, remove_pairs = self._col_domain_pairs(event)
            tag_update_plans = OpenLineageMetadataPlanParser.parse_tag_plans(event)
            return LineageWritePlans(
                operation=operation,
                column_valuedomain_add=add_pairs,
                column_valuedomain_remove=remove_pairs,
                tag_update_plans=tag_update_plans,
            )

        # ===== 通用：基于 metadata plans 补齐结构边（HAS_*）=====
        catalog_schema_edges: set[tuple[str, str]] = set()
        schema_table_edges: set[tuple[str, str]] = set()
        table_column_edges: dict[str, set[str]] = {}

        for p in metadata.table_plans:
            catalog_schema_edges.add((p.catalog.id, p.schema.id))
            schema_table_edges.add((p.schema.id, p.table.id))
            if p.columns:
                table_column_edges.setdefault(p.table.id, set()).update(c.id for c in p.columns)

        # 默认：结构边总是可产出；列血缘（facet 或 SQL）按需补齐
        if not need_lineage_details:
            return LineageWritePlans(
                operation=operation,
                catalog_schema_edges=list(catalog_schema_edges),
                schema_table_edges=list(schema_table_edges),
                table_column_edges=[
                    (table_id, list(col_ids)) for table_id, col_ids in table_column_edges.items()
                ],
            )

        return LineageWritePlans(
            operation=operation,
            catalog_schema_edges=list(catalog_schema_edges),
            schema_table_edges=list(schema_table_edges),
            table_column_edges=[
                (table_id, list(col_ids)) for table_id, col_ids in table_column_edges.items()
            ],
            sql_node=self._build_sql_node(event) if event.get_sql() else None,
            table_input_ids=self._table_in_ids(event),
            table_output_ids=self._table_out_ids(event),
            table_input_names=self._table_in_names(event),
            table_output_names=self._table_out_names(event),
            column_lineage_data=self._col_lineage_data(event),
        )

    def _build_sql_node(self, event: RunEvent) -> SQLDTO | None:
        sql = event.get_sql()
        if not sql:
            return None
        return SQLDTO.create(
            sql=sql,
            job_namespace=event.job.namespace,
            job_name=event.job.name,
            dialect=event.get_sql_dialect(),
            engine=event.get_producer_type(),
        )

    def _table_in_ids(self, event: RunEvent) -> list[str]:
        job_namespace = event.job.namespace
        ids: list[str] = []
        for input_ds in event.inputs:
            table_info = self._resolver.extract_table_info(input_ds, job_namespace=job_namespace)
            if table_info:
                ids.append(table_info.id)
        return ids

    def _table_out_ids(self, event: RunEvent) -> list[str]:
        job_namespace = event.job.namespace
        ids: list[str] = []
        for output_ds in event.outputs:
            table_info = self._resolver.extract_table_info(output_ds, job_namespace=job_namespace)
            if table_info:
                ids.append(table_info.id)
        return ids

    def _table_in_names(self, event: RunEvent) -> list[str]:
        """获取输入表名列表（格式：schema.table）"""
        job_namespace = event.job.namespace
        names: list[str] = []
        for input_ds in event.inputs:
            table_info = self._resolver.extract_table_info(input_ds, job_namespace=job_namespace)
            if table_info:
                names.append(f"{table_info.schema}.{table_info.table}")
        return names

    def _table_out_names(self, event: RunEvent) -> list[str]:
        """获取输出表名列表（格式：schema.table）"""
        job_namespace = event.job.namespace
        names: list[str] = []
        for output_ds in event.outputs:
            table_info = self._resolver.extract_table_info(output_ds, job_namespace=job_namespace)
            if table_info:
                names.append(f"{table_info.schema}.{table_info.table}")
        return names

    def _col_lineage_data(self, event: RunEvent) -> list[dict]:
        has_column_lineage_facet = any(
            output_ds.facets and "columnLineage" in output_ds.facets for output_ds in event.outputs
        )
        if has_column_lineage_facet:
            return self._col_lineage_facet(event)
        return self._col_lineage_sql(event)

    def _col_lineage_facet(self, event: RunEvent) -> list[dict]:
        job_namespace = event.job.namespace
        path_to_table = self._resolver.path_table_map(event.inputs, job_namespace=job_namespace)
        lineage_data: list[dict] = []

        for output_ds in event.outputs:
            if not output_ds.facets or "columnLineage" not in output_ds.facets:
                continue

            output_table_info = self._resolver.extract_table_info(
                output_ds, job_namespace=job_namespace
            )
            if not output_table_info:
                continue

            col_lineage = ColumnLineageDatasetFacet.from_dict(output_ds.facets["columnLineage"])

            for output_col_name, lineage_info in col_lineage.fields.items():
                target_col_id = output_table_info.column_id(output_col_name)

                for input_field in lineage_info.inputFields:
                    source_table_info = path_to_table.get(input_field.name)
                    if source_table_info:
                        source_col_id = source_table_info.column_id(input_field.field)
                    else:
                        # Spark columnLineage facet 的 inputFields.name 可能直接是逻辑表名（schema.table）
                        parsed_ns = self._resolver.parse_job_namespace(job_namespace)
                        can_fallback = (
                            isinstance(input_field.name, str)
                            and "://" not in input_field.name
                            and input_field.name.count(".") == 1
                        )
                        parsed_table = (
                            parse_schema_table(input_field.name) if can_fallback else None
                        )
                        if not parsed_ns.metalake or not parsed_ns.catalog or not parsed_table:
                            continue
                        source_schema, source_table = parsed_table
                        source_col_id = generate_id(
                            "column",
                            parsed_ns.metalake,
                            parsed_ns.catalog,
                            source_schema,
                            source_table,
                            input_field.field,
                        )

                    transform_type = None
                    transform_subtype = None
                    if input_field.transformations:
                        first_transform = input_field.transformations[0]
                        transform_type = (
                            first_transform.type.value if first_transform.type else None
                        )
                        transform_subtype = first_transform.subtype

                    lineage_data.append(
                        {
                            "srcId": source_col_id,
                            "dstId": target_col_id,
                            "transformType": transform_type,
                            "transformSubtype": transform_subtype,
                        }
                    )

        return lineage_data

    def _col_lineage_sql(self, event: RunEvent) -> list[dict]:
        sql = event.get_sql()
        if not sql:
            return []

        parsed_ns = self._resolver.parse_job_namespace(event.job.namespace)
        if not parsed_ns.metalake or not parsed_ns.catalog:
            return []

        dialect = event.get_sql_dialect() or "hive"
        try:
            result = SQLLineageAnalyzer(dialect=dialect).analyze_sql(sql)
        except Exception as e:
            logger.warning("sql_column_lineage_analysis_failed", error=str(e))
            return []

        lineage_data: list[dict] = []
        for col_lineage in result.column_lineages:
            source_schema = col_lineage.source.table.schema or ""
            source_table = col_lineage.source.table.table
            if not source_schema and "." in source_table:
                parsed_source = parse_schema_table(source_table)
                if parsed_source:
                    source_schema, source_table = parsed_source

            source_col_id = generate_id(
                "column",
                parsed_ns.metalake,
                parsed_ns.catalog,
                source_schema,
                source_table,
                col_lineage.source.column,
            )

            target_schema = col_lineage.target.table.schema or ""
            target_table = col_lineage.target.table.table
            if not target_schema and "." in target_table:
                parsed_target = parse_schema_table(target_table)
                if parsed_target:
                    target_schema, target_table = parsed_target

            target_col_id = generate_id(
                "column",
                parsed_ns.metalake,
                parsed_ns.catalog,
                target_schema,
                target_table,
                col_lineage.target.column,
            )

            lineage_data.append(
                {
                    "srcId": source_col_id,
                    "dstId": target_col_id,
                    "transformType": col_lineage.transformation,
                    "transformSubtype": None,
                }
            )

        return lineage_data

    def _metric_lineage(
        self, event: RunEvent
    ) -> tuple[list[tuple[str, list[str]]], list[tuple[str, list[str]]]]:
        """
        原子指标与列血缘：
        - AtomicMetric -[:MEASURES]-> Column
        - AtomicMetric -[:FILTERS_BY]-> Column
        """
        measures: list[tuple[str, list[str]]] = []
        filters: list[tuple[str, list[str]]] = []

        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            type_field = fields.get("type")
            if not code_field or not code_field.description:
                continue

            metric_type = (type_field.description or "ATOMIC") if type_field else "ATOMIC"
            if metric_type.upper() != "ATOMIC":
                continue

            metric_id = generate_id("metric", code_field.description)

            ref_catalog = (
                fields.get("refCatalogName").description if fields.get("refCatalogName") else None
            )
            ref_schema = (
                fields.get("refSchemaName").description if fields.get("refSchemaName") else None
            )
            ref_table = (
                fields.get("refTableName").description if fields.get("refTableName") else None
            )
            if not ref_catalog or not ref_schema or not ref_table:
                continue

            parsed_ns = self._resolver.parse_job_namespace(dataset.namespace)
            if not parsed_ns.metalake:
                continue

            measure_columns_field = fields.get("measureColumns")
            if measure_columns_field and measure_columns_field.description:
                try:
                    measure_columns = json.loads(measure_columns_field.description)
                    col_names = [c["name"] for c in measure_columns if "name" in c]
                    column_ids = [
                        generate_id(
                            "column", parsed_ns.metalake, ref_catalog, ref_schema, ref_table, n
                        )
                        for n in col_names
                    ]
                    if column_ids:
                        measures.append((metric_id, column_ids))
                except json.JSONDecodeError:
                    logger.warning("metric_lineage_measure_parse_error", metric_id=metric_id)

            filter_columns_field = fields.get("filterColumns")
            if filter_columns_field and filter_columns_field.description:
                try:
                    filter_columns = json.loads(filter_columns_field.description)
                    col_names = [c["name"] for c in filter_columns if "name" in c]
                    column_ids = [
                        generate_id(
                            "column", parsed_ns.metalake, ref_catalog, ref_schema, ref_table, n
                        )
                        for n in col_names
                    ]
                    if column_ids:
                        filters.append((metric_id, column_ids))
                except json.JSONDecodeError:
                    logger.warning("metric_lineage_filter_parse_error", metric_id=metric_id)

        return measures, filters

    def _col_domain_pairs(
        self, event: RunEvent
    ) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
        add_pairs: list[tuple[str, str]] = []
        remove_pairs: list[tuple[str, str]] = []

        for dataset in event.get_all_datasets():
            if not dataset.facets or "gravitinoTag" not in dataset.facets:
                continue

            tag_facet = dataset.facets["gravitinoTag"]
            object_type = tag_facet.get("objectType")
            if object_type != "COLUMN":
                continue

            parsed_ns = self._resolver.parse_job_namespace(dataset.namespace)
            if not parsed_ns.metalake or not parsed_ns.catalog:
                continue

            parsed_column = parse_table_column(dataset.name)
            if not parsed_column:
                continue

            schema_name, table_name, column_name = parsed_column
            column_id = generate_id(
                "column",
                parsed_ns.metalake,
                parsed_ns.catalog,
                schema_name,
                table_name,
                column_name,
            )

            tags_to_add = tag_facet.get("tagsToAdd", [])
            tags_to_remove = tag_facet.get("tagsToRemove", [])

            for t in tags_to_add:
                if isinstance(t, str) and t.startswith(self.VALUE_DOMAIN_TAG_PREFIX):
                    add_pairs.append((column_id, t[len(self.VALUE_DOMAIN_TAG_PREFIX) :]))

            for t in tags_to_remove:
                if isinstance(t, str) and t.startswith(self.VALUE_DOMAIN_TAG_PREFIX):
                    remove_pairs.append((column_id, t[len(self.VALUE_DOMAIN_TAG_PREFIX) :]))

        return add_pairs, remove_pairs
