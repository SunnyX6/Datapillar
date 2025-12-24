"""
血缘写入器

负责写入血缘关系：
- SQL 节点
- 表级血缘：Table -[:INPUT_OF]-> SQL, SQL -[:OUTPUT_TO]-> Table
- 列级血缘：Column -[:DERIVES_FROM]-> Column
- 指标列血缘：AtomicMetric -[:MEASURES]-> Column, AtomicMetric -[:FILTERS_BY]-> Column

血缘提取策略：
1. 优先使用 OpenLineage facets（columnLineage）
2. 当 facets 不存在时，用 SQL 解析补充列级血缘
3. 原子指标通过 ref 字段建立与列的血缘关系

支持的数据源：
- Gravitino: namespace 格式为 gravitino://metalake/catalog
- Spark OpenLineage: 使用 symlinks facet 获取逻辑表名
"""

import json
import re
from dataclasses import dataclass

import structlog
from neo4j import AsyncSession

from src.shared.utils.sql_lineage import SQLLineageAnalyzer
from src.modules.openlineage.schemas.events import InputDataset, OutputDataset, RunEvent
from src.modules.openlineage.schemas.facets import (
    ColumnLineageDatasetFacet,
    SchemaDatasetFacet,
    SQLJobFacet,
)
from src.modules.openlineage.schemas.neo4j import SQLNode, generate_id
from src.modules.openlineage.writers.base import BaseWriter

logger = structlog.get_logger()


@dataclass
class ParsedNamespace:
    """解析后的 namespace"""

    metalake: str | None = None
    catalog: str | None = None


@dataclass
class TableInfo:
    """表信息"""

    metalake: str
    catalog: str
    schema: str
    table: str
    physical_path: str = ""

    @property
    def id(self) -> str:
        """生成表 ID"""
        return generate_id("table", self.metalake, self.catalog, self.schema, self.table)

    def column_id(self, column_name: str) -> str:
        """生成列 ID"""
        return generate_id(
            "column", self.metalake, self.catalog, self.schema, self.table, column_name
        )


class LineageWriter(BaseWriter):
    """
    血缘写入器

    负责将血缘关系写入 Neo4j：
    - SQL: SQL 语句节点
    - 表级血缘：INPUT_OF / OUTPUT_TO
    - 列级血缘：DERIVES_FROM
    - 指标列血缘：MEASURES / FILTERS_BY
    """

    METRIC_OPERATIONS = {"register_metric"}

    def __init__(self) -> None:
        super().__init__()
        self._sql_written = 0
        self._table_lineage_written = 0
        self._column_lineage_written = 0
        self._metric_lineage_written = 0
        self._sql_analyzer = SQLLineageAnalyzer(dialect="hive")

    @property
    def name(self) -> str:
        return "lineage_writer"

    async def write(self, session: AsyncSession, event: RunEvent) -> None:
        """写入血缘"""
        job_name = event.job.name if event.job else ""
        operation = job_name.split(".")[-1] if "." in job_name else job_name

        # 处理指标血缘
        if operation in self.METRIC_OPERATIONS:
            await self._write_metric_column_lineage(session, event)
            return

        # 获取 SQL
        sql = self._get_sql(event)
        if not sql:
            return

        # 写入 SQL 节点
        sql_node = SQLNode.create(
            sql=sql,
            job_namespace=event.job.namespace,
            job_name=event.job.name,
            dialect=self._get_sql_dialect(event),
            engine=self._get_producer_type(event),
        )
        await self._write_sql(session, sql_node)

        # 写入表级血缘
        await self._write_table_lineage(session, event, sql_node.id)

        # 写入列级血缘
        await self._write_column_lineage(session, event)

    def _get_sql(self, event: RunEvent) -> str | None:
        """获取 SQL"""
        if event.job.facets and "sql" in event.job.facets:
            sql_facet = SQLJobFacet.from_dict(event.job.facets["sql"])
            return sql_facet.query
        return None

    def _get_sql_dialect(self, event: RunEvent) -> str | None:
        """获取 SQL 方言"""
        if event.job.facets and "sql" in event.job.facets:
            sql_facet = SQLJobFacet.from_dict(event.job.facets["sql"])
            return sql_facet.dialect
        return None

    def _get_producer_type(self, event: RunEvent) -> str | None:
        """获取生产者类型"""
        if event.producer:
            # 从 producer URL 提取类型
            # 例如: https://github.com/apache/gravitino/openlineage-listener -> gravitino
            if "gravitino" in event.producer.lower():
                return "gravitino"
            if "spark" in event.producer.lower():
                return "spark"
            if "flink" in event.producer.lower():
                return "flink"
        return None

    def _parse_job_namespace(self, job_namespace: str) -> ParsedNamespace:
        """
        解析 job namespace 获取 metalake/catalog

        格式: gravitino://metalake/catalog
        """
        result = ParsedNamespace()
        match = re.match(r"gravitino://([^/]+)/([^/]+)(?:/.*)?", job_namespace)
        if match:
            result.metalake = match.group(1)
            result.catalog = match.group(2)
        return result

    def _extract_table_info(
        self,
        dataset: InputDataset | OutputDataset,
        job_namespace: str,
    ) -> TableInfo | None:
        """
        从 dataset 提取表信息

        优先级：
        1. symlinks facet（Spark OpenLineage）
        2. gravitino:// namespace（Gravitino）
        """
        # 从 job_namespace 获取 metalake/catalog
        parsed = self._parse_job_namespace(job_namespace)
        if not parsed.metalake or not parsed.catalog:
            return None

        # 优先从 symlinks facet 提取表名（Spark OpenLineage）
        table_name = self._get_table_name_from_symlinks(dataset)

        # 如果没有 symlinks，尝试从 dataset.name 提取
        if not table_name:
            # 检查 namespace 是否是 gravitino:// 格式
            if dataset.namespace.startswith("gravitino://"):
                table_name = dataset.name
            else:
                # 非 gravitino 格式且无 symlinks，无法提取
                logger.debug(
                    "cannot_extract_table_info",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                return None

        # 解析 schema.table 格式
        parts = table_name.split(".", 1)
        if len(parts) < 2:
            logger.debug("invalid_table_name_format", table_name=table_name)
            return None

        return TableInfo(
            metalake=parsed.metalake,
            catalog=parsed.catalog,
            schema=parts[0],
            table=parts[1],
            physical_path=dataset.name,
        )

    def _get_table_name_from_symlinks(
        self, dataset: InputDataset | OutputDataset
    ) -> str | None:
        """
        从 symlinks facet 提取逻辑表名

        symlinks 格式：
        {
            "identifiers": [
                {"namespace": "hive://host:port", "name": "schema.table", "type": "TABLE"}
            ]
        }
        """
        if not dataset.facets or "symlinks" not in dataset.facets:
            return None

        symlinks = dataset.facets.get("symlinks", {})
        identifiers = symlinks.get("identifiers", [])

        if not identifiers:
            return None

        # 取第一个 identifier 的 name
        return identifiers[0].get("name")

    def _build_path_to_table_mapping(
        self,
        datasets: list[InputDataset],
        job_namespace: str,
    ) -> dict[str, TableInfo]:
        """
        建立物理路径到表信息的映射

        用于列级血缘：columnLineage.inputFields 使用物理路径
        """
        mapping: dict[str, TableInfo] = {}

        for ds in datasets:
            table_info = self._extract_table_info(ds, job_namespace)
            if table_info:
                # 用 dataset.name（物理路径）作为 key
                mapping[ds.name] = table_info

        return mapping

    async def _write_sql(self, session: AsyncSession, sql: SQLNode) -> None:
        """写入 SQL 节点"""
        query = """
        MERGE (s:SQL:Knowledge {id: $id})
        ON CREATE SET
            s.createdAt = datetime(),
            s.content = $content,
            s.dialect = $dialect,
            s.engine = $engine,
            s.jobNamespace = $jobNamespace,
            s.jobName = $jobName,
            s.executionCount = 1,
            s.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            s.updatedAt = datetime(),
            s.executionCount = COALESCE(s.executionCount, 0) + 1
        """

        await session.run(
            query,
            id=sql.id,
            content=sql.content,
            dialect=sql.dialect,
            engine=sql.engine,
            jobNamespace=sql.job_namespace,
            jobName=sql.job_name,
        )

        self._sql_written += 1
        logger.debug("sql_written", id=sql.id)

    async def _write_table_lineage(
        self, session: AsyncSession, event: RunEvent, sql_id: str
    ) -> None:
        """写入表级血缘（批量）"""
        job_namespace = event.job.namespace

        # 收集 INPUT 表
        input_table_ids = []
        for input_ds in event.inputs:
            table_info = self._extract_table_info(input_ds, job_namespace)
            if table_info:
                input_table_ids.append(table_info.id)

        # 收集 OUTPUT 表
        output_table_ids = []
        for output_ds in event.outputs:
            table_info = self._extract_table_info(output_ds, job_namespace)
            if table_info:
                output_table_ids.append(table_info.id)

        # 批量写入 INPUT 血缘
        if input_table_ids:
            query = """
            UNWIND $tableIds AS tableId
            MATCH (t:Table {id: tableId})
            MATCH (s:SQL {id: $sqlId})
            MERGE (t)-[r:INPUT_OF]->(s)
            ON CREATE SET r.createdAt = datetime()
            ON MATCH SET r.updatedAt = datetime()
            """
            await session.run(query, tableIds=input_table_ids, sqlId=sql_id)
            self._table_lineage_written += len(input_table_ids)
            logger.debug("table_input_lineage_batch_written", count=len(input_table_ids))

        # 批量写入 OUTPUT 血缘
        if output_table_ids:
            query = """
            UNWIND $tableIds AS tableId
            MATCH (s:SQL {id: $sqlId})
            MATCH (t:Table {id: tableId})
            MERGE (s)-[r:OUTPUT_TO]->(t)
            ON CREATE SET r.createdAt = datetime()
            ON MATCH SET r.updatedAt = datetime()
            """
            await session.run(query, tableIds=output_table_ids, sqlId=sql_id)
            self._table_lineage_written += len(output_table_ids)
            logger.debug("table_output_lineage_batch_written", count=len(output_table_ids))

    async def _write_column_lineage(
        self, session: AsyncSession, event: RunEvent
    ) -> None:
        """
        写入列级血缘

        策略：
        1. 优先使用 columnLineage facet（Spark 等提供）
        2. 当 facet 不存在时，用 SQL 解析补充（Flink 等场景）
        """
        job_namespace = event.job.namespace

        # 检查是否有 columnLineage facet
        has_column_lineage_facet = any(
            output_ds.facets and "columnLineage" in output_ds.facets
            for output_ds in event.outputs
        )

        if has_column_lineage_facet:
            # 使用 OpenLineage facet
            await self._write_column_lineage_from_facet(session, event)
        else:
            # 用 SQL 解析补充
            await self._write_column_lineage_from_sql(session, event)

    async def _write_column_lineage_from_facet(
        self, session: AsyncSession, event: RunEvent
    ) -> None:
        """从 columnLineage facet 批量写入列级血缘"""
        job_namespace = event.job.namespace

        # 建立物理路径到表信息的映射（用于解析 inputFields）
        path_to_table = self._build_path_to_table_mapping(event.inputs, job_namespace)

        # 收集所有列血缘关系
        lineage_data = []

        for output_ds in event.outputs:
            if not output_ds.facets or "columnLineage" not in output_ds.facets:
                continue

            # 获取输出表信息
            output_table_info = self._extract_table_info(output_ds, job_namespace)
            if not output_table_info:
                continue

            col_lineage = ColumnLineageDatasetFacet.from_dict(
                output_ds.facets["columnLineage"]
            )

            for output_col_name, lineage_info in col_lineage.fields.items():
                # 目标列 ID
                target_col_id = output_table_info.column_id(output_col_name)

                for input_field in lineage_info.inputFields:
                    # 从映射表获取源表信息
                    source_table_info = path_to_table.get(input_field.name)
                    if not source_table_info:
                        continue

                    # 源列 ID
                    source_col_id = source_table_info.column_id(input_field.field)

                    # 提取转换类型
                    transform_type = None
                    transform_subtype = None
                    if input_field.transformations:
                        first_transform = input_field.transformations[0]
                        transform_type = first_transform.type.value if first_transform.type else None
                        transform_subtype = first_transform.subtype

                    lineage_data.append({
                        "srcId": source_col_id,
                        "dstId": target_col_id,
                        "transformType": transform_type,
                        "transformSubtype": transform_subtype,
                    })

        # 批量写入
        if lineage_data:
            await self._write_column_lineage_batch(session, lineage_data)

    async def _write_column_lineage_batch(
        self, session: AsyncSession, lineage_data: list[dict]
    ) -> None:
        """批量写入列级血缘"""
        query = """
        UNWIND $lineageData AS item
        MATCH (src:Column {id: item.srcId})
        MATCH (dst:Column {id: item.dstId})
        MERGE (dst)-[r:DERIVES_FROM]->(src)
        ON CREATE SET
            r.createdAt = datetime(),
            r.transformationType = item.transformType,
            r.transformationSubtype = item.transformSubtype
        ON MATCH SET
            r.updatedAt = datetime()
        """

        await session.run(query, lineageData=lineage_data)

        self._column_lineage_written += len(lineage_data)
        logger.debug("column_lineage_batch_written", count=len(lineage_data))

    async def _write_column_lineage_from_sql(
        self, session: AsyncSession, event: RunEvent
    ) -> None:
        """
        从 SQL 解析写入列级血缘

        当 OpenLineage 不提供 columnLineage facet 时使用
        """
        sql = self._get_sql(event)
        if not sql:
            return

        job_namespace = event.job.namespace
        parsed = self._parse_job_namespace(job_namespace)
        if not parsed.metalake or not parsed.catalog:
            return

        try:
            result = self._sql_analyzer.analyze_sql(sql)
        except Exception as e:
            logger.warning("sql_column_lineage_analysis_failed", error=str(e))
            return

        # 收集所有列血缘关系
        lineage_data = []

        for col_lineage in result.column_lineages:
            # 构建源列 ID
            source_schema = col_lineage.source.table.schema or ""
            source_table = col_lineage.source.table.table
            if not source_schema and "." in source_table:
                parts = source_table.split(".", 1)
                source_schema, source_table = parts[0], parts[1]

            source_col_id = generate_id(
                "column",
                parsed.metalake,
                parsed.catalog,
                source_schema,
                source_table,
                col_lineage.source.column,
            )

            # 构建目标列 ID
            target_schema = col_lineage.target.table.schema or ""
            target_table = col_lineage.target.table.table
            if not target_schema and "." in target_table:
                parts = target_table.split(".", 1)
                target_schema, target_table = parts[0], parts[1]

            target_col_id = generate_id(
                "column",
                parsed.metalake,
                parsed.catalog,
                target_schema,
                target_table,
                col_lineage.target.column,
            )

            lineage_data.append({
                "srcId": source_col_id,
                "dstId": target_col_id,
                "transformType": col_lineage.transformation,
                "transformSubtype": None,
            })

        # 批量写入
        if lineage_data:
            await self._write_column_lineage_batch(session, lineage_data)

    async def _write_metric_column_lineage(
        self, session: AsyncSession, event: RunEvent
    ) -> None:
        """
        写入原子指标与列的血缘关系

        从 schema facet 解析 ref 字段：
        - refCatalogName, refSchemaName, refTableName: 引用的表
        - measureColumns: JSON 数组 [{name, type, comment}, ...]
        - filterColumns: JSON 数组 [{name, type, comment, values}, ...]

        建立关系：
        - AtomicMetric -[:MEASURES]-> Column
        - AtomicMetric -[:FILTERS_BY]-> Column
        """
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            # 获取指标 code 和 type
            code_field = fields.get("code")
            type_field = fields.get("type")
            if not code_field or not code_field.description:
                continue

            metric_type = type_field.description if type_field else "ATOMIC"
            if metric_type.upper() != "ATOMIC":
                # 只有原子指标才有列血缘
                continue

            metric_code = code_field.description
            metric_id = generate_id("metric", metric_code)

            # 解析 ref 字段
            ref_catalog = (
                fields.get("refCatalogName").description
                if fields.get("refCatalogName")
                else None
            )
            ref_schema = (
                fields.get("refSchemaName").description
                if fields.get("refSchemaName")
                else None
            )
            ref_table = (
                fields.get("refTableName").description
                if fields.get("refTableName")
                else None
            )

            if not ref_catalog or not ref_schema or not ref_table:
                logger.debug(
                    "metric_lineage_skip_no_ref",
                    metric_code=metric_code,
                    ref_catalog=ref_catalog,
                    ref_schema=ref_schema,
                    ref_table=ref_table,
                )
                continue

            # 从 dataset.namespace 解析 metalake
            # 格式: gravitino://metalake/catalog
            parsed_ns = self._parse_job_namespace(dataset.namespace)
            if not parsed_ns.metalake:
                logger.debug(
                    "metric_lineage_skip_no_metalake",
                    metric_code=metric_code,
                    namespace=dataset.namespace,
                )
                continue

            # 解析 measureColumns
            measure_columns_field = fields.get("measureColumns")
            if measure_columns_field and measure_columns_field.description:
                try:
                    measure_columns = json.loads(measure_columns_field.description)
                    measure_col_names = [col["name"] for col in measure_columns if "name" in col]
                    if measure_col_names:
                        await self._write_metric_measures_batch(
                            session,
                            metric_id,
                            parsed_ns.metalake,
                            ref_catalog,
                            ref_schema,
                            ref_table,
                            measure_col_names,
                        )
                except json.JSONDecodeError as e:
                    logger.warning(
                        "metric_lineage_measure_parse_error",
                        metric_code=metric_code,
                        error=str(e),
                    )

            # 解析 filterColumns
            filter_columns_field = fields.get("filterColumns")
            if filter_columns_field and filter_columns_field.description:
                try:
                    filter_columns = json.loads(filter_columns_field.description)
                    filter_col_names = [col["name"] for col in filter_columns if "name" in col]
                    if filter_col_names:
                        await self._write_metric_filters_batch(
                            session,
                            metric_id,
                            parsed_ns.metalake,
                            ref_catalog,
                            ref_schema,
                            ref_table,
                            filter_col_names,
                        )
                except json.JSONDecodeError as e:
                    logger.warning(
                        "metric_lineage_filter_parse_error",
                        metric_code=metric_code,
                        error=str(e),
                    )

    async def _write_metric_measures_batch(
        self,
        session: AsyncSession,
        metric_id: str,
        metalake: str,
        catalog: str,
        schema: str,
        table: str,
        column_names: list[str],
    ) -> None:
        """批量写入指标度量列关系"""
        lineage_data = [
            {
                "metricId": metric_id,
                "columnId": generate_id("column", metalake, catalog, schema, table, col_name),
            }
            for col_name in column_names
        ]

        query = """
        UNWIND $lineageData AS item
        MATCH (m:AtomicMetric {id: item.metricId})
        MATCH (c:Column {id: item.columnId})
        MERGE (m)-[r:MEASURES]->(c)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """

        await session.run(query, lineageData=lineage_data)
        self._metric_lineage_written += len(lineage_data)
        logger.debug("metric_measures_written", metric_id=metric_id, count=len(column_names))

    async def _write_metric_filters_batch(
        self,
        session: AsyncSession,
        metric_id: str,
        metalake: str,
        catalog: str,
        schema: str,
        table: str,
        column_names: list[str],
    ) -> None:
        """批量写入指标过滤列关系"""
        lineage_data = [
            {
                "metricId": metric_id,
                "columnId": generate_id("column", metalake, catalog, schema, table, col_name),
            }
            for col_name in column_names
        ]

        query = """
        UNWIND $lineageData AS item
        MATCH (m:AtomicMetric {id: item.metricId})
        MATCH (c:Column {id: item.columnId})
        MERGE (m)-[r:FILTERS_BY]->(c)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """

        await session.run(query, lineageData=lineage_data)
        self._metric_lineage_written += len(lineage_data)
        logger.debug("metric_filters_written", metric_id=metric_id, count=len(column_names))

    def get_detailed_stats(self) -> dict:
        """获取详细统计"""
        stats = self.get_stats().to_dict()
        stats["sql_written"] = self._sql_written
        stats["table_lineage_written"] = self._table_lineage_written
        stats["column_lineage_written"] = self._column_lineage_written
        stats["metric_lineage_written"] = self._metric_lineage_written
        return stats
