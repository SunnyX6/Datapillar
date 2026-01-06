"""
关系写入器（LineageWriter）

职责边界：
- 负责写入 Neo4j 的"关系（边）"，以及血缘侧的 SQL 节点
- 不写入任何元数据资产节点（Catalog/Schema/Table/Column/Metric/语义资产等）
- 包括结构关系（HAS_*）与血缘关系（INPUT_OF/OUTPUT_TO/DERIVES_FROM/...）

实现方式：
- 采用与 MetadataWriter 一致的"编排层 + 子 writer"结构
"""

from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.modules.openlineage.core.sql_summary_processor import sql_summary_processor
from src.modules.openlineage.parsers.plans.types import LineageWritePlans
from src.modules.openlineage.writers.base import BaseWriter
from src.modules.openlineage.writers.lineage import (
    ColumnLineageWriter,
    HierarchyWriter,
    MetricColumnLineageWriter,
    MetricRelationshipWriter,
    SQLWriter,
    TableLineageWriter,
    TagRelationshipWriter,
    ValueDomainLineageWriter,
)

logger = structlog.get_logger()


class LineageWriter(BaseWriter):
    """
    关系写入器

    负责写入：
    - 结构层级关系：HAS_SCHEMA / HAS_TABLE / HAS_COLUMN / HAS_METRIC
    - SQL 节点相关关系：INPUT_OF / OUTPUT_TO
    - 列级血缘：DERIVES_FROM
    - 指标列血缘：MEASURES / FILTERS_BY
    - 列值域关系：HAS_VALUE_DOMAIN
    - 指标父子关系：DERIVED_FROM / COMPUTED_FROM
    - Tag 关系：HAS_TAG
    """

    def __init__(self) -> None:
        super().__init__()

        self._hierarchy_writer = HierarchyWriter()
        self._metric_relationship_writer = MetricRelationshipWriter()

        self._sql_writer = SQLWriter()
        self._table_lineage_writer = TableLineageWriter()
        self._column_lineage_writer = ColumnLineageWriter()
        self._metric_column_lineage_writer = MetricColumnLineageWriter()
        self._valuedomain_lineage_writer = ValueDomainLineageWriter()
        self._tag_relationship_writer = TagRelationshipWriter()

    @property
    def name(self) -> str:
        return "lineage_writer"

    async def write(self, session: AsyncSession, plans: LineageWritePlans) -> None:
        # 1) 结构层级关系（HAS_*）
        await self._hierarchy_writer.write(session, plans)

        # 2) 指标关系（Schema->Metric + Metric 父子）
        if plans.schema_metric_edges or plans.metric_parent_relationships:
            await self._metric_relationship_writer.write(session, plans)

        # 3) 指标列血缘（MEASURES / FILTERS_BY）
        for metric_id, column_ids in plans.metric_measures:
            await self._metric_column_lineage_writer.write_measures(
                session,
                metric_id=metric_id,
                column_ids=column_ids,
            )
        for metric_id, column_ids in plans.metric_filters:
            await self._metric_column_lineage_writer.write_filters(
                session,
                metric_id=metric_id,
                column_ids=column_ids,
            )

        # 4) 列值域关系（HAS_VALUE_DOMAIN）
        if plans.column_valuedomain_add or plans.column_valuedomain_remove:
            await self._valuedomain_lineage_writer.write(session, plans)

        # 5) Tag 关系（HAS_TAG）
        if plans.tag_update_plans:
            await self._tag_relationship_writer.write(session, plans.tag_update_plans)

        # 6) SQL 血缘（SQL 节点 + 表级 + 列级）
        if plans.sql_node:
            await self._sql_writer.write(session, plans.sql_node)
            await self._table_lineage_writer.write(
                session,
                sql=plans.sql_node,
                input_table_ids=plans.table_input_ids,
                output_table_ids=plans.table_output_ids,
            )
            # 将 SQL 摘要任务入队（异步批量处理）
            await sql_summary_processor.enqueue(
                sql_node_id=plans.sql_node.id,
                sql_content=plans.sql_node.content,
                input_tables=plans.table_input_names,
                output_tables=plans.table_output_names,
                dialect=plans.sql_node.dialect,
            )

        if plans.column_lineage_data:
            await self._column_lineage_writer.write(session, plans.column_lineage_data)

    def get_detailed_stats(self) -> dict:
        stats = self.get_stats().to_dict()
        stats["hierarchy_edges_written"] = self._hierarchy_writer.hierarchy_edges_written

        stats["sql_written"] = self._sql_writer.sql_written
        stats["table_lineage_written"] = self._table_lineage_writer.table_lineage_written
        stats["column_lineage_written"] = self._column_lineage_writer.column_lineage_written

        stats["metric_schema_edges_written"] = self._metric_relationship_writer.metric_schema_edges
        stats["metric_parent_edges_written"] = self._metric_relationship_writer.metric_parent_edges
        stats["metric_lineage_written"] = self._metric_column_lineage_writer.metric_lineage_written
        stats["column_valuedomain_lineage_written"] = (
            self._valuedomain_lineage_writer.col_domain_edges
        )
        stats["tag_edges_added"] = self._tag_relationship_writer.tag_edges_added
        stats["tag_edges_removed"] = self._tag_relationship_writer.tag_edges_removed
        return stats
