"""
元数据写入器

负责写入所有元数据节点和层级关系：
- Catalog -[:HAS_SCHEMA]-> Schema -[:HAS_TABLE]-> Table -[:HAS_COLUMN]-> Column
- Metric 节点
"""

import json
import re
from dataclasses import dataclass

import structlog
from neo4j import AsyncSession

from src.modules.openlineage.core.embedding_processor import embedding_processor
from src.modules.openlineage.schemas.events import Dataset, RunEvent
from src.modules.openlineage.schemas.facets import GravitinoDatasetFacet, SchemaDatasetFacet
from src.modules.openlineage.schemas.neo4j import (
    CatalogNode,
    ColumnNode,
    MetricNode,
    ModifierNode,
    SchemaNode,
    TableNode,
    UnitNode,
    ValueDomainNode,
    WordRootNode,
    generate_id,
)
from src.modules.openlineage.writers.base import BaseWriter

logger = structlog.get_logger()

GRAVITINO_FACET_KEY = "gravitino"


@dataclass
class ParsedNamespace:
    """解析后的 namespace"""

    metalake: str | None = None
    catalog: str | None = None
    raw: str = ""


class MetadataWriter(BaseWriter):
    """
    元数据写入器

    负责将所有元数据节点写入 Neo4j：
    - Catalog: 数据目录
    - Schema: 数据库/命名空间
    - Table: 数据表
    - Column: 字段
    - Metric: 指标

    写入后将 embedding 任务入队，由 EmbeddingProcessor 异步处理
    """

    def __init__(self) -> None:
        super().__init__()
        self._catalogs_written = 0
        self._schemas_written = 0
        self._tables_written = 0
        self._columns_written = 0
        self._metrics_written = 0
        self._embedding_tasks_queued = 0

    async def _queue_embedding_task(
        self, node_id: str, node_label: str, name: str,
        description: str | None = None, tags: list[str] | None = None
    ) -> None:
        """将 embedding 任务入队"""
        parts = [name]
        if description:
            parts.append(description)
        if tags:
            parts.extend(tags)
        text = " ".join(parts)
        if await embedding_processor.put(node_id, node_label, text):
            self._embedding_tasks_queued += 1

    @property
    def name(self) -> str:
        return "metadata_writer"

    # 表相关的操作
    TABLE_OPERATIONS = {"create_table", "alter_table", "load_table"}
    # Schema 相关的操作
    SCHEMA_OPERATIONS = {"create_schema", "alter_schema", "load_schema"}
    # Catalog 相关的操作
    CATALOG_OPERATIONS = {"create_catalog", "alter_catalog"}
    # 指标相关的操作
    METRIC_OPERATIONS = {"register_metric", "alter_metric"}
    # 语义层操作
    WORDROOT_OPERATIONS = {"create_wordroot", "alter_wordroot"}
    MODIFIER_OPERATIONS = {"create_modifier", "alter_modifier"}
    UNIT_OPERATIONS = {"create_unit", "alter_unit"}
    VALUEDOMAIN_OPERATIONS = {"create_valuedomain", "alter_valuedomain"}
    # 删除操作（统一用 drop）
    DROP_TABLE_OPERATIONS = {"drop_table"}
    DROP_SCHEMA_OPERATIONS = {"drop_schema"}
    DROP_CATALOG_OPERATIONS = {"drop_catalog"}
    DROP_METRIC_OPERATIONS = {"drop_metric"}
    DROP_WORDROOT_OPERATIONS = {"drop_wordroot"}
    DROP_MODIFIER_OPERATIONS = {"drop_modifier"}
    DROP_UNIT_OPERATIONS = {"drop_unit"}
    DROP_VALUEDOMAIN_OPERATIONS = {"drop_valuedomain"}
    # Tag 操作
    TAG_OPERATIONS = {"associate_tags"}
    VALUE_DOMAIN_TAG_PREFIX = "vd:"

    async def write(self, session: AsyncSession, event: RunEvent) -> None:
        """写入元数据"""
        job_name = event.job.name if event.job else ""

        # 提取操作类型：gravitino.create_table -> create_table
        operation = job_name.split(".")[-1] if "." in job_name else job_name

        if operation in self.TABLE_OPERATIONS:
            await self._write_table_metadata(session, event)
        elif operation in self.SCHEMA_OPERATIONS:
            await self._write_schema_metadata(session, event)
        elif operation in self.CATALOG_OPERATIONS:
            await self._write_catalog_metadata(session, event)
        elif operation in self.METRIC_OPERATIONS:
            await self._write_metric_from_event(session, event)
        elif operation in self.WORDROOT_OPERATIONS:
            await self._write_wordroot_from_event(session, event)
        elif operation in self.MODIFIER_OPERATIONS:
            await self._write_modifier_from_event(session, event)
        elif operation in self.UNIT_OPERATIONS:
            await self._write_unit_from_event(session, event)
        elif operation in self.VALUEDOMAIN_OPERATIONS:
            await self._write_valuedomain_from_event(session, event)
        elif operation in self.DROP_TABLE_OPERATIONS:
            await self._delete_table_metadata(session, event)
        elif operation in self.DROP_SCHEMA_OPERATIONS:
            await self._delete_schema_metadata(session, event)
        elif operation in self.DROP_CATALOG_OPERATIONS:
            await self._delete_catalog_metadata(session, event)
        elif operation in self.DROP_METRIC_OPERATIONS:
            await self._delete_metric_metadata(session, event)
        elif operation in self.DROP_WORDROOT_OPERATIONS:
            await self._delete_wordroot_metadata(session, event)
        elif operation in self.DROP_MODIFIER_OPERATIONS:
            await self._delete_modifier_metadata(session, event)
        elif operation in self.DROP_UNIT_OPERATIONS:
            await self._delete_unit_metadata(session, event)
        elif operation in self.DROP_VALUEDOMAIN_OPERATIONS:
            await self._delete_valuedomain_metadata(session, event)
        elif operation in self.TAG_OPERATIONS:
            await self._update_tags(session, event)
        else:
            logger.debug("skip_unsupported_operation", job_name=job_name, operation=operation)

    async def _write_table_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """写入表和列元数据"""
        for dataset in event.get_all_datasets():
            # 解析层级结构
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name, table_name = self._parse_dataset_name(dataset.name)

            # 如果无法解析出 metalake/catalog，跳过
            if not parsed_ns.metalake or not parsed_ns.catalog:
                logger.warning(
                    "skip_dataset_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 解析 Gravitino 自定义 facet
            gravitino_facet = self._parse_gravitino_facet(dataset)

            # 1. 写入 Catalog 节点
            catalog_node = CatalogNode.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            await self._write_catalog(session, catalog_node)

            # 2. 写入 Schema 节点 + Catalog->Schema 关系
            if schema_name:
                schema_node = SchemaNode.create(
                    metalake=parsed_ns.metalake,
                    catalog=parsed_ns.catalog,
                    schema_name=schema_name,
                )
                await self._write_schema(session, schema_node, catalog_node.id)

                # 3. 写入 Table 节点 + Schema->Table 关系
                table_node = self._build_table_node(
                    parsed_ns.metalake,
                    parsed_ns.catalog,
                    schema_name,
                    table_name,
                    event,
                    gravitino_facet,
                )
                await self._write_table(session, table_node, schema_node.id)

                # 4. 批量写入 Column 节点 + Table->Column 关系
                columns = self._parse_columns(
                    parsed_ns.metalake,
                    parsed_ns.catalog,
                    schema_name,
                    table_name,
                    dataset,
                    gravitino_facet,
                )
                if columns:
                    # 先删除不再存在的列（处理 alter_table 删除列的情况）
                    column_ids = [col.id for col in columns]
                    await self._delete_orphan_columns(session, table_node.id, column_ids)
                    await self._write_columns_batch(session, columns, table_node.id)

    async def _write_schema_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        写入 Schema 元数据

        Schema 事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name = dataset.name  # Schema 事件的 name 直接就是 schema 名

            if not parsed_ns.metalake or not parsed_ns.catalog:
                logger.warning(
                    "skip_schema_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 解析 Gravitino 自定义 facet（可能包含 description 等）
            gravitino_facet = self._parse_gravitino_facet(dataset)

            # 1. 写入 Catalog 节点
            catalog_node = CatalogNode.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            await self._write_catalog(session, catalog_node)

            # 2. 写入 Schema 节点 + Catalog->Schema 关系
            schema_node = SchemaNode.create(
                metalake=parsed_ns.metalake,
                catalog=parsed_ns.catalog,
                schema_name=schema_name,
            )
            # 如果有 Gravitino facet，补充 description
            if gravitino_facet and gravitino_facet.description:
                schema_node.description = gravitino_facet.description

            await self._write_schema(session, schema_node, catalog_node.id)

    async def _write_catalog_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        写入 Catalog 元数据

        Catalog 事件格式：
        - namespace: gravitino://{metalake}
        - name: {catalog}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            catalog_name = dataset.name  # Catalog 事件的 name 直接就是 catalog 名

            if not parsed_ns.metalake:
                logger.warning(
                    "skip_catalog_no_metalake",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 写入 Catalog 节点
            catalog_node = CatalogNode.create(
                metalake=parsed_ns.metalake,
                catalog_name=catalog_name,
            )
            await self._write_catalog(session, catalog_node)

    async def _delete_table_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        删除表元数据

        级联删除：Table + 所有 Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}.{table}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name, table_name = self._parse_dataset_name(dataset.name)

            if not parsed_ns.metalake or not parsed_ns.catalog or not schema_name:
                logger.warning(
                    "skip_delete_table_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 生成 Table ID
            table_id = generate_id(
                "table", parsed_ns.metalake, parsed_ns.catalog, schema_name, table_name
            )

            # 级联删除：Table + Column + 清理血缘边
            # 注意：DETACH DELETE 会自动删除节点的所有关系
            query = """
            MATCH (t:Table {id: $tableId})
            OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
            DETACH DELETE t, c
            """

            await session.run(query, tableId=table_id)
            logger.info(
                "table_deleted",
                table_id=table_id,
                table_name=f"{schema_name}.{table_name}",
            )

    async def _delete_schema_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        删除 Schema 元数据

        级联删除：Schema + 所有 Table/Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name = dataset.name

            if not parsed_ns.metalake or not parsed_ns.catalog:
                logger.warning(
                    "skip_delete_schema_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 生成 Schema ID
            schema_id = generate_id(
                "schema", parsed_ns.metalake, parsed_ns.catalog, schema_name
            )

            # 级联删除：Schema + Table + Column + Metric
            # 注意：DETACH DELETE 会自动删除节点的所有关系
            query = """
            MATCH (s:Schema {id: $schemaId})
            OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table)
            OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
            OPTIONAL MATCH (s)-[:HAS_METRIC]->(m)
            DETACH DELETE s, t, c, m
            """

            await session.run(query, schemaId=schema_id)
            logger.info("schema_deleted", schema_id=schema_id, schema_name=schema_name)

    async def _delete_catalog_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        删除 Catalog 元数据

        级联删除：Catalog + 所有 Schema/Table/Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}
        - name: {catalog}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            catalog_name = dataset.name

            if not parsed_ns.metalake:
                logger.warning(
                    "skip_delete_catalog_no_metalake",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 生成 Catalog ID
            catalog_id = generate_id("catalog", parsed_ns.metalake, catalog_name)

            # 级联删除：Catalog + Schema + Table + Column + Metric
            # 注意：DETACH DELETE 会自动删除节点的所有关系
            query = """
            MATCH (cat:Catalog {id: $catalogId})
            OPTIONAL MATCH (cat)-[:HAS_SCHEMA]->(s:Schema)
            OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table)
            OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
            OPTIONAL MATCH (s)-[:HAS_METRIC]->(m)
            DETACH DELETE cat, s, t, c, m
            """

            await session.run(query, catalogId=catalog_id)
            logger.info("catalog_deleted", catalog_id=catalog_id, catalog_name=catalog_name)

    async def _delete_metric_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """
        删除 Metric 元数据

        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}.{metric}
        """
        for dataset in event.get_all_datasets():
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name, metric_name = self._parse_dataset_name(dataset.name)

            if not parsed_ns.metalake or not parsed_ns.catalog:
                logger.warning(
                    "skip_delete_metric_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # Metric ID 基于 code 生成，需要从 facet 中获取
            # 如果没有 code，则使用 metric_name
            metric_code = metric_name
            if dataset.facets and "schema" in dataset.facets:
                schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
                for field in schema_facet.fields:
                    if field.name == "code" and field.description:
                        metric_code = field.description
                        break

            metric_id = generate_id("metric", metric_code)

            # 删除 Metric 节点及其关系
            query = """
            MATCH (m {id: $metricId})
            WHERE m:AtomicMetric OR m:DerivedMetric OR m:CompositeMetric
            DETACH DELETE m
            """

            await session.run(query, metricId=metric_id)
            logger.info("metric_deleted", metric_id=metric_id, metric_name=metric_name)

    async def _delete_wordroot_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """删除 WordRoot 元数据"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            code = None
            for field in schema_facet.fields:
                if field.name == "code" and field.description:
                    code = field.description
                    break

            if not code:
                code = dataset.name.split(".")[-1] if "." in dataset.name else dataset.name

            wordroot_id = generate_id("wordroot", code)
            query = """
            MATCH (w:WordRoot {id: $id})
            DETACH DELETE w
            """
            await session.run(query, id=wordroot_id)
            logger.info("wordroot_deleted", wordroot_id=wordroot_id, code=code)

    async def _delete_modifier_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """删除 Modifier 元数据"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            code = None
            for field in schema_facet.fields:
                if field.name == "code" and field.description:
                    code = field.description
                    break

            if not code:
                code = dataset.name.split(".")[-1] if "." in dataset.name else dataset.name

            modifier_id = generate_id("modifier", code)
            query = """
            MATCH (m:Modifier {id: $id})
            DETACH DELETE m
            """
            await session.run(query, id=modifier_id)
            logger.info("modifier_deleted", modifier_id=modifier_id, code=code)

    async def _delete_unit_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """删除 Unit 元数据"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            code = None
            for field in schema_facet.fields:
                if field.name == "code" and field.description:
                    code = field.description
                    break

            if not code:
                code = dataset.name.split(".")[-1] if "." in dataset.name else dataset.name

            unit_id = generate_id("unit", code)
            query = """
            MATCH (u:Unit {id: $id})
            DETACH DELETE u
            """
            await session.run(query, id=unit_id)
            logger.info("unit_deleted", unit_id=unit_id, code=code)

    async def _delete_valuedomain_metadata(self, session: AsyncSession, event: RunEvent) -> None:
        """删除 ValueDomain 元数据"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            domain_code = None
            for field in schema_facet.fields:
                if field.name == "domainCode" and field.description:
                    domain_code = field.description
                    break

            if not domain_code:
                continue

            valuedomain_id = generate_id("valuedomain", domain_code)
            query = """
            MATCH (v:ValueDomain {id: $id})
            DETACH DELETE v
            """
            await session.run(query, id=valuedomain_id)
            logger.info(
                "valuedomain_deleted",
                valuedomain_id=valuedomain_id,
                domain_code=domain_code,
            )

    async def _update_tags(self, session: AsyncSession, event: RunEvent) -> None:
        """
        更新节点的普通 Tag 标签

        注意：值域 tags（vd: 前缀）由 lineage_writer 处理，这里只处理普通 tags

        事件格式：
        - namespace: gravitino://{metalake}/{catalog} 或 gravitino://{metalake}
        - name: 根据 objectType 不同格式不同
        - facets.gravitinoTag: {objectType, tagsToAdd, tagsToRemove, associatedTags}
        """
        for dataset in event.get_all_datasets():
            # 解析 gravitinoTag facet
            if not dataset.facets or "gravitinoTag" not in dataset.facets:
                logger.warning("skip_tag_no_facet", name=dataset.name)
                continue

            tag_facet = dataset.facets["gravitinoTag"]
            object_type = tag_facet.get("objectType")
            associated_tags = tag_facet.get("associatedTags", [])

            if not object_type:
                logger.warning("skip_tag_no_object_type", name=dataset.name)
                continue

            # 解析 namespace 和 name，生成节点 ID
            parsed_ns = self._parse_namespace(dataset.namespace)
            node_id = self._generate_node_id_for_tag(
                parsed_ns, dataset.name, object_type
            )

            if not node_id:
                logger.warning(
                    "skip_tag_cannot_generate_id",
                    namespace=dataset.namespace,
                    name=dataset.name,
                    object_type=object_type,
                )
                continue

            # 根据 objectType 确定节点标签
            node_label = self._get_node_label_for_tag(object_type)

            # 过滤掉值域 tags（vd: 前缀），只保留普通 tags
            normal_tags = [
                t for t in associated_tags
                if not t.startswith(self.VALUE_DOMAIN_TAG_PREFIX)
            ]

            # 更新节点的 tags 属性，并返回 name 和 description 用于重新入队 embedding
            query = f"""
            MATCH (n:{node_label} {{id: $nodeId}})
            SET n.tags = $tags, n.updatedAt = datetime()
            RETURN n.id as id, n.name as name, n.description as description
            """

            result = await session.run(query, nodeId=node_id, tags=list(normal_tags))
            record = await result.single()

            if record:
                logger.info(
                    "tags_updated",
                    node_id=node_id,
                    object_type=object_type,
                    tags=normal_tags,
                )

                # 所有物理资产（Catalog/Schema/Table/Column）都需要重新入队 embedding
                if object_type in ("CATALOG", "SCHEMA", "TABLE", "COLUMN"):
                    await self._queue_embedding_task(
                        node_id, node_label,
                        record["name"], record["description"],
                        list(normal_tags)
                    )
            else:
                logger.warning(
                    "tags_update_node_not_found",
                    node_id=node_id,
                    object_type=object_type,
                )

    def _generate_node_id_for_tag(
        self, parsed_ns: ParsedNamespace, name: str, object_type: str
    ) -> str | None:
        """根据对象类型生成节点 ID"""
        if object_type == "CATALOG":
            # name = catalog
            if parsed_ns.metalake:
                return generate_id("catalog", parsed_ns.metalake, name)
        elif object_type == "SCHEMA":
            # name = schema
            if parsed_ns.metalake and parsed_ns.catalog:
                return generate_id("schema", parsed_ns.metalake, parsed_ns.catalog, name)
        elif object_type == "TABLE":
            # name = schema.table
            if parsed_ns.metalake and parsed_ns.catalog:
                parts = name.split(".", 1)
                if len(parts) == 2:
                    schema_name, table_name = parts
                    return generate_id(
                        "table", parsed_ns.metalake, parsed_ns.catalog, schema_name, table_name
                    )
        elif object_type == "COLUMN":
            # name = schema.table.column
            if parsed_ns.metalake and parsed_ns.catalog:
                parts = name.split(".", 2)
                if len(parts) == 3:
                    schema_name, table_name, column_name = parts
                    return generate_id(
                        "column",
                        parsed_ns.metalake,
                        parsed_ns.catalog,
                        schema_name,
                        table_name,
                        column_name,
                    )
        return None

    def _get_node_label_for_tag(self, object_type: str) -> str:
        """根据对象类型获取 Neo4j 节点标签"""
        label_map = {
            "CATALOG": "Catalog",
            "SCHEMA": "Schema",
            "TABLE": "Table",
            "COLUMN": "Column",
        }
        return label_map.get(object_type, "Knowledge")

    async def _write_metric_from_event(self, session: AsyncSession, event: RunEvent) -> None:
        """从 OpenLineage 事件中解析并写入指标"""
        for dataset in event.get_all_datasets():
            # 解析层级结构
            parsed_ns = self._parse_namespace(dataset.namespace)
            schema_name, metric_name = self._parse_dataset_name(dataset.name)

            if not parsed_ns.metalake or not parsed_ns.catalog:
                logger.warning(
                    "skip_metric_no_hierarchy",
                    namespace=dataset.namespace,
                    name=dataset.name,
                )
                continue

            # 从 schema facet 中解析指标信息
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            type_field = fields.get("type")
            comment_field = fields.get("comment")
            unit_field = fields.get("unit")
            aggregation_logic_field = fields.get("aggregationLogic")
            calculation_formula_field = fields.get("calculationFormula")
            parent_metric_codes_field = fields.get("parentMetricCodes")

            if not code_field or not type_field:
                logger.warning("skip_metric_missing_fields", dataset=dataset.name)
                continue

            # 解析 parentMetricCodes（逗号分隔的字符串）
            parent_metric_codes: list[str] = []
            if parent_metric_codes_field and parent_metric_codes_field.description:
                parent_metric_codes = [
                    code.strip()
                    for code in parent_metric_codes_field.description.split(",")
                    if code.strip()
                ]

            # 1. 写入 Catalog 节点
            catalog_node = CatalogNode.create(
                metalake=parsed_ns.metalake,
                catalog_name=parsed_ns.catalog,
            )
            await self._write_catalog(session, catalog_node)

            # 2. 写入 Schema 节点 + Catalog->Schema 关系
            if schema_name:
                schema_node = SchemaNode.create(
                    metalake=parsed_ns.metalake,
                    catalog=parsed_ns.catalog,
                    schema_name=schema_name,
                )
                await self._write_schema(session, schema_node, catalog_node.id)

                # 3. 创建并写入 Metric 节点 + Schema->Metric 关系
                metric_node = MetricNode.create(
                    code=code_field.description or metric_name,
                    name=metric_name,
                    metric_type=type_field.description or "ATOMIC",
                    description=comment_field.description if comment_field else None,
                    unit=unit_field.description if unit_field else None,
                    aggregation_logic=aggregation_logic_field.description
                    if aggregation_logic_field
                    else None,
                    calculation_formula=calculation_formula_field.description
                    if calculation_formula_field
                    else None,
                    parent_metric_codes=parent_metric_codes,
                )

                await self._write_metric_with_schema(session, metric_node, schema_node.id)

    async def _write_metric_with_schema(
        self, session: AsyncSession, metric: MetricNode, schema_id: str
    ) -> None:
        """写入 Metric 节点 + 关系"""
        label = self._get_metric_label(metric.metric_type)
        is_atomic = metric.metric_type.upper() == "ATOMIC"

        if is_atomic:
            # 原子指标：写入节点 + Schema->Metric 关系
            query = f"""
            MERGE (m:{label}:Knowledge {{id: $id}})
            ON CREATE SET
                m.createdAt = datetime(),
                m.name = $name,
                m.code = $code,
                m.description = $description,
                m.unit = $unit,
                m.aggregationLogic = $aggregationLogic,
                m.createdBy = 'OPENLINEAGE'
            ON MATCH SET
                m.updatedAt = datetime(),
                m.description = COALESCE($description, m.description)
            WITH m
            MATCH (s:Schema {{id: $schemaId}})
            MERGE (s)-[:HAS_METRIC]->(m)
            """
            await session.run(
                query,
                id=metric.id,
                name=metric.name,
                code=metric.code,
                description=metric.description,
                unit=metric.unit,
                aggregationLogic=metric.aggregation_logic,
                schemaId=schema_id,
            )
        else:
            # 派生/复合指标：只写入节点，不挂在 Schema 下
            query = f"""
            MERGE (m:{label}:Knowledge {{id: $id}})
            ON CREATE SET
                m.createdAt = datetime(),
                m.name = $name,
                m.code = $code,
                m.description = $description,
                m.unit = $unit,
                m.calculationFormula = $calculationFormula,
                m.createdBy = 'OPENLINEAGE'
            ON MATCH SET
                m.updatedAt = datetime(),
                m.description = COALESCE($description, m.description)
            """
            await session.run(
                query,
                id=metric.id,
                name=metric.name,
                code=metric.code,
                description=metric.description,
                unit=metric.unit,
                calculationFormula=metric.calculation_formula,
            )

            # 写入父子关系
            if metric.parent_metric_codes:
                rel_type = (
                    "DERIVED_FROM" if metric.metric_type.upper() == "DERIVED" else "COMPUTED_FROM"
                )
                await self._write_metric_parent_relationships(
                    session, metric.id, label, metric.parent_metric_codes, rel_type
                )

        # 将 Metric embedding 任务入队
        await self._queue_embedding_task(metric.id, label, metric.name, metric.description)

        self._metrics_written += 1
        logger.debug("metric_written", id=metric.id, name=metric.name)

    async def _write_metric_parent_relationships(
        self,
        session: AsyncSession,
        child_id: str,
        child_label: str,
        parent_codes: list[str],
        rel_type: str,
    ) -> None:
        """写入指标父子关系"""
        # 先删除该指标的旧父子关系（确保修改时数据一致）
        delete_query = f"""
        MATCH (child:{child_label} {{id: $childId}})-[r:{rel_type}]->()
        DELETE r
        """
        await session.run(delete_query, childId=child_id)

        for parent_code in parent_codes:
            # 根据 parent_code 生成父指标的 id
            parent_id = generate_id("metric", parent_code)

            query = f"""
            MATCH (child:{child_label} {{id: $childId}})
            MATCH (parent:AtomicMetric {{id: $parentId}})
            MERGE (child)-[r:{rel_type}]->(parent)
            ON CREATE SET r.createdAt = datetime()
            """

            await session.run(query, childId=child_id, parentId=parent_id)
            logger.debug(
                "metric_relationship_written",
                child=child_id,
                parent=parent_id,
                rel_type=rel_type,
            )

    async def _write_wordroot_from_event(self, session: AsyncSession, event: RunEvent) -> None:
        """从 OpenLineage 事件中解析并写入词根"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            if not code_field or not code_field.description:
                continue

            code = code_field.description
            name = fields.get("name").description if fields.get("name") else None
            data_type = fields.get("dataType").description if fields.get("dataType") else None
            description = fields.get("comment").description if fields.get("comment") else None

            wordroot_node = WordRootNode.create(
                code=code,
                name=name,
                data_type=data_type,
                description=description,
            )

            await self._write_wordroot(session, wordroot_node)

    async def _write_wordroot(self, session: AsyncSession, wordroot: WordRootNode) -> None:
        """写入 WordRoot 节点"""
        query = """
        MERGE (w:WordRoot:Knowledge {id: $id})
        ON CREATE SET
            w.createdAt = datetime(),
            w.code = $code,
            w.name = $name,
            w.dataType = $dataType,
            w.description = $description,
            w.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            w.updatedAt = datetime(),
            w.name = COALESCE($name, w.name),
            w.dataType = COALESCE($dataType, w.dataType),
            w.description = COALESCE($description, w.description)
        """

        await session.run(
            query,
            id=wordroot.id,
            code=wordroot.code,
            name=wordroot.name,
            dataType=wordroot.data_type,
            description=wordroot.description,
        )

        # 将 WordRoot embedding 任务入队
        await self._queue_embedding_task(
            wordroot.id, "WordRoot",
            wordroot.name or wordroot.code, wordroot.description
        )

        logger.debug("wordroot_written", id=wordroot.id, code=wordroot.code)

    async def _write_modifier_from_event(self, session: AsyncSession, event: RunEvent) -> None:
        """从 OpenLineage 事件中解析并写入修饰符"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            type_field = fields.get("type")
            if not code_field or not code_field.description or not type_field:
                continue

            code = code_field.description
            modifier_type = type_field.description or "PREFIX"
            description = fields.get("comment").description if fields.get("comment") else None

            modifier_node = ModifierNode.create(
                code=code,
                modifier_type=modifier_type,
                description=description,
            )

            await self._write_modifier(session, modifier_node)

    async def _write_modifier(self, session: AsyncSession, modifier: ModifierNode) -> None:
        """写入 Modifier 节点"""
        query = """
        MERGE (m:Modifier:Knowledge {id: $id})
        ON CREATE SET
            m.createdAt = datetime(),
            m.code = $code,
            m.modifierType = $modifierType,
            m.description = $description,
            m.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            m.updatedAt = datetime(),
            m.modifierType = COALESCE($modifierType, m.modifierType),
            m.description = COALESCE($description, m.description)
        """

        await session.run(
            query,
            id=modifier.id,
            code=modifier.code,
            modifierType=modifier.modifier_type,
            description=modifier.description,
        )

        # 将 Modifier embedding 任务入队
        await self._queue_embedding_task(
            modifier.id, "Modifier", modifier.code, modifier.description
        )

        logger.debug("modifier_written", id=modifier.id, code=modifier.code)

    async def _write_unit_from_event(self, session: AsyncSession, event: RunEvent) -> None:
        """从 OpenLineage 事件中解析并写入单位"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            code_field = fields.get("code")
            if not code_field or not code_field.description:
                continue

            code = code_field.description
            name = fields.get("name").description if fields.get("name") else None
            symbol = fields.get("symbol").description if fields.get("symbol") else None
            description = fields.get("comment").description if fields.get("comment") else None

            unit_node = UnitNode.create(
                code=code,
                name=name,
                symbol=symbol,
                description=description,
            )

            await self._write_unit(session, unit_node)

    async def _write_unit(self, session: AsyncSession, unit: UnitNode) -> None:
        """写入 Unit 节点"""
        query = """
        MERGE (u:Unit:Knowledge {id: $id})
        ON CREATE SET
            u.createdAt = datetime(),
            u.code = $code,
            u.name = $name,
            u.symbol = $symbol,
            u.description = $description,
            u.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            u.updatedAt = datetime(),
            u.name = COALESCE($name, u.name),
            u.symbol = COALESCE($symbol, u.symbol),
            u.description = COALESCE($description, u.description)
        """

        await session.run(
            query,
            id=unit.id,
            code=unit.code,
            name=unit.name,
            symbol=unit.symbol,
            description=unit.description,
        )

        # 将 Unit embedding 任务入队
        await self._queue_embedding_task(
            unit.id, "Unit",
            unit.name or unit.code, unit.description
        )

        logger.debug("unit_written", id=unit.id, code=unit.code)

    async def _write_valuedomain_from_event(self, session: AsyncSession, event: RunEvent) -> None:
        """从 OpenLineage 事件中解析并写入值域"""
        for dataset in event.get_all_datasets():
            if not dataset.facets or "schema" not in dataset.facets:
                continue

            schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
            fields = {f.name: f for f in schema_facet.fields}

            domain_code_field = fields.get("domainCode")
            domain_type_field = fields.get("domainType")
            domain_level_field = fields.get("domainLevel")
            if not domain_code_field or not domain_type_field or not domain_level_field:
                continue

            domain_code = domain_code_field.description
            domain_type = domain_type_field.description or "ENUM"
            domain_level = domain_level_field.description or "GLOBAL"
            domain_name = (
                fields.get("domainName").description if fields.get("domainName") else None
            )
            items = fields.get("items").description if fields.get("items") else None
            description = fields.get("comment").description if fields.get("comment") else None
            data_type = fields.get("dataType").description if fields.get("dataType") else None

            valuedomain_node = ValueDomainNode.create(
                domain_code=domain_code,
                domain_type=domain_type,
                domain_level=domain_level,
                domain_name=domain_name,
                items=items,
                description=description,
                data_type=data_type,
            )

            await self._write_valuedomain(session, valuedomain_node)

    async def _write_valuedomain(self, session: AsyncSession, valuedomain: ValueDomainNode) -> None:
        """写入 ValueDomain 节点"""
        query = """
        MERGE (v:ValueDomain:Knowledge {id: $id})
        ON CREATE SET
            v.createdAt = datetime(),
            v.domainCode = $domainCode,
            v.domainName = $domainName,
            v.domainType = $domainType,
            v.domainLevel = $domainLevel,
            v.items = $items,
            v.dataType = $dataType,
            v.description = $description,
            v.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            v.updatedAt = datetime(),
            v.domainName = COALESCE($domainName, v.domainName),
            v.domainLevel = COALESCE($domainLevel, v.domainLevel),
            v.items = COALESCE($items, v.items),
            v.dataType = COALESCE($dataType, v.dataType),
            v.description = COALESCE($description, v.description)
        """

        await session.run(
            query,
            id=valuedomain.id,
            domainCode=valuedomain.domain_code,
            domainName=valuedomain.domain_name,
            domainType=valuedomain.domain_type,
            domainLevel=valuedomain.domain_level,
            items=valuedomain.items,
            dataType=valuedomain.data_type,
            description=valuedomain.description,
        )

        # 将 ValueDomain embedding 任务入队（包含 items 信息）
        embedding_text = valuedomain.domain_name or valuedomain.domain_code
        if valuedomain.items:
            embedding_text += f" {valuedomain.items}"
        await self._queue_embedding_task(
            valuedomain.id, "ValueDomain", embedding_text, valuedomain.description
        )

        logger.debug("valuedomain_written", id=valuedomain.id, domainCode=valuedomain.domain_code)

    def _parse_namespace(self, namespace: str) -> ParsedNamespace:
        """
        解析 namespace

        格式1: gravitino://{metalake}/{catalog} (用于 table/schema 事件)
        格式2: gravitino://{metalake} (用于 catalog 事件)
        示例: gravitino://One Meta/tt -> metalake="One Meta", catalog="tt"
        示例: gravitino://One Meta -> metalake="One Meta", catalog=None
        """
        result = ParsedNamespace(raw=namespace)

        # 匹配 gravitino://{metalake}/{catalog}
        match = re.match(r"gravitino://([^/]+)/([^/]+)(?:/.*)?", namespace)
        if match:
            result.metalake = match.group(1)
            result.catalog = match.group(2)
            return result

        # 匹配 gravitino://{metalake} (catalog 事件格式)
        match = re.match(r"gravitino://([^/]+)$", namespace)
        if match:
            result.metalake = match.group(1)
            return result

        return result

    def _parse_dataset_name(self, name: str) -> tuple[str | None, str]:
        """
        解析 dataset name

        格式: {schema}.{table}
        示例: datapillar.ol_final_xxx -> schema="datapillar", table="ol_final_xxx"
        """
        parts = name.split(".", 1)
        if len(parts) >= 2:
            return parts[0], parts[1]
        return None, name

    def _parse_gravitino_facet(self, dataset: Dataset) -> GravitinoDatasetFacet | None:
        """解析 Gravitino 自定义 facet"""
        if not dataset.facets or GRAVITINO_FACET_KEY not in dataset.facets:
            return None
        return GravitinoDatasetFacet.from_dict(dataset.facets[GRAVITINO_FACET_KEY])

    def _build_table_node(
        self,
        metalake: str,
        catalog: str,
        schema: str,
        table_name: str,
        event: RunEvent,
        gravitino_facet: GravitinoDatasetFacet | None,
    ) -> TableNode:
        """构建 Table 节点"""
        table = TableNode.create(
            metalake=metalake,
            catalog=catalog,
            schema=schema,
            table_name=table_name,
            producer=event.producer,
        )

        # 从 Gravitino facet 补充扩展属性
        if gravitino_facet:
            table.description = gravitino_facet.description
            table.properties = gravitino_facet.properties
            table.partitions = gravitino_facet.partitions
            table.distribution = gravitino_facet.distribution
            table.sort_orders = gravitino_facet.sortOrders
            table.indexes = gravitino_facet.indexes
            table.creator = gravitino_facet.creator
            table.create_time = gravitino_facet.createTime
            table.last_modifier = gravitino_facet.lastModifier
            table.last_modified_time = gravitino_facet.lastModifiedTime

        return table

    def _parse_columns(
        self,
        metalake: str,
        catalog: str,
        schema: str,
        table: str,
        dataset: Dataset,
        gravitino_facet: GravitinoDatasetFacet | None,
    ) -> list[ColumnNode]:
        """解析 Column 节点"""
        if not dataset.facets or "schema" not in dataset.facets:
            return []

        schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
        columns: list[ColumnNode] = []

        for field_info in schema_facet.fields:
            column = ColumnNode.create(
                metalake=metalake,
                catalog=catalog,
                schema=schema,
                table=table,
                column_name=field_info.name,
                data_type=field_info.type,
                description=field_info.description,
            )

            # 从 Gravitino facet 补充列扩展元数据
            if gravitino_facet:
                col_meta = gravitino_facet.get_column_metadata(field_info.name)
                if col_meta:
                    column.nullable = col_meta.nullable
                    column.auto_increment = col_meta.autoIncrement
                    column.default_value = col_meta.defaultValue

            columns.append(column)

        return columns

    async def _write_catalog(self, session: AsyncSession, catalog: CatalogNode) -> None:
        """写入 Catalog 节点"""
        query = """
        MERGE (c:Catalog:Knowledge {id: $id})
        ON CREATE SET
            c.createdAt = datetime(),
            c.name = $name,
            c.metalake = $metalake,
            c.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            c.updatedAt = datetime()
        RETURN c.tags as tags
        """

        result = await session.run(
            query,
            id=catalog.id,
            name=catalog.name,
            metalake=catalog.metalake,
        )
        record = await result.single()
        tags = record["tags"] if record and record["tags"] else None

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(
            catalog.id, "Catalog", catalog.name, None, tags
        )

        self._catalogs_written += 1
        logger.debug("catalog_written", id=catalog.id, name=catalog.name)

    async def _write_schema(
        self, session: AsyncSession, schema: SchemaNode, catalog_id: str
    ) -> None:
        """写入 Schema 节点 + Catalog->Schema 关系"""
        query = """
        MERGE (s:Schema:Knowledge {id: $id})
        ON CREATE SET
            s.createdAt = datetime(),
            s.name = $name,
            s.description = $description,
            s.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            s.updatedAt = datetime(),
            s.description = COALESCE($description, s.description)
        WITH s
        MATCH (c:Catalog {id: $catalogId})
        MERGE (c)-[:HAS_SCHEMA]->(s)
        RETURN s.tags as tags
        """

        result = await session.run(
            query,
            id=schema.id,
            name=schema.name,
            description=schema.description,
            catalogId=catalog_id,
        )
        record = await result.single()
        tags = record["tags"] if record and record["tags"] else None

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(
            schema.id, "Schema", schema.name, schema.description, tags
        )

        self._schemas_written += 1
        logger.debug("schema_written", id=schema.id, name=schema.name)

    async def _write_table(
        self, session: AsyncSession, table: TableNode, schema_id: str
    ) -> None:
        """写入 Table 节点 + Schema->Table 关系"""
        properties_json = json.dumps(table.properties) if table.properties else None

        query = """
        MERGE (t:Table:Knowledge {id: $id})
        ON CREATE SET
            t.createdAt = datetime(),
            t.name = $name,
            t.producer = $producer,
            t.description = $description,
            t.properties = $properties,
            t.partitions = $partitions,
            t.distribution = $distribution,
            t.sortOrders = $sortOrders,
            t.indexes = $indexes,
            t.creator = $creator,
            t.createTime = $createTime,
            t.lastModifier = $lastModifier,
            t.lastModifiedTime = $lastModifiedTime,
            t.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            t.updatedAt = datetime(),
            t.producer = $producer,
            t.description = COALESCE($description, t.description),
            t.properties = COALESCE($properties, t.properties)
        WITH t
        MATCH (s:Schema {id: $schemaId})
        MERGE (s)-[:HAS_TABLE]->(t)
        RETURN t.tags as tags
        """

        result = await session.run(
            query,
            id=table.id,
            name=table.name,
            producer=table.producer,
            description=table.description,
            properties=properties_json,
            partitions=table.partitions,
            distribution=table.distribution,
            sortOrders=table.sort_orders,
            indexes=table.indexes,
            creator=table.creator,
            createTime=table.create_time,
            lastModifier=table.last_modifier,
            lastModifiedTime=table.last_modified_time,
            schemaId=schema_id,
        )
        record = await result.single()
        tags = record["tags"] if record and record["tags"] else None

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(table.id, "Table", table.name, table.description, tags)

        self._tables_written += 1
        logger.debug("table_written", id=table.id, name=table.name)

    async def _delete_orphan_columns(
        self, session: AsyncSession, table_id: str, valid_column_ids: list[str]
    ) -> None:
        """删除表下不再存在的列节点（处理 alter_table 删除列的情况）"""
        query = """
        MATCH (t:Table {id: $tableId})-[:HAS_COLUMN]->(c:Column)
        WHERE NOT c.id IN $validColumnIds
        DETACH DELETE c
        RETURN count(c) as deletedCount
        """
        result = await session.run(query, tableId=table_id, validColumnIds=valid_column_ids)
        record = await result.single()
        if record and record["deletedCount"] > 0:
            logger.info(
                "orphan_columns_deleted",
                table_id=table_id,
                deleted_count=record["deletedCount"],
            )

    async def _write_columns_batch(
        self, session: AsyncSession, columns: list[ColumnNode], table_id: str
    ) -> None:
        """批量写入 Column 节点 + Table->Column 关系"""
        # 构建批量数据
        column_data = [
            {
                "id": col.id,
                "name": col.name,
                "dataType": col.data_type,
                "description": col.description,
                "nullable": col.nullable,
                "autoIncrement": col.auto_increment,
                "defaultValue": col.default_value,
            }
            for col in columns
        ]

        # 使用 UNWIND 批量写入
        query = """
        UNWIND $columns AS col
        MERGE (c:Column:Knowledge {id: col.id})
        ON CREATE SET
            c.createdAt = datetime(),
            c.name = col.name,
            c.dataType = col.dataType,
            c.description = col.description,
            c.nullable = col.nullable,
            c.autoIncrement = col.autoIncrement,
            c.defaultValue = col.defaultValue,
            c.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            c.updatedAt = datetime(),
            c.dataType = COALESCE(col.dataType, c.dataType),
            c.description = COALESCE(col.description, c.description)
        WITH c
        MATCH (t:Table {id: $tableId})
        MERGE (t)-[:HAS_COLUMN]->(c)
        RETURN c.id as id, c.tags as tags
        """

        result = await session.run(query, columns=column_data, tableId=table_id)
        records = [record async for record in result]

        # 构建 id -> tags 映射
        tags_map = {r["id"]: r["tags"] for r in records if r["id"]}

        # 将 Column embedding 任务入队（带 tags）
        for col in columns:
            tags = tags_map.get(col.id)
            await self._queue_embedding_task(col.id, "Column", col.name, col.description, tags)

        self._columns_written += len(columns)
        logger.debug("columns_batch_written", count=len(columns), table_id=table_id)

    async def _write_column(
        self, session: AsyncSession, column: ColumnNode, table_id: str
    ) -> None:
        """写入 Column 节点 + Table->Column 关系"""
        query = """
        MERGE (c:Column:Knowledge {id: $id})
        ON CREATE SET
            c.createdAt = datetime(),
            c.name = $name,
            c.dataType = $dataType,
            c.description = $description,
            c.nullable = $nullable,
            c.autoIncrement = $autoIncrement,
            c.defaultValue = $defaultValue,
            c.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            c.updatedAt = datetime(),
            c.dataType = COALESCE($dataType, c.dataType),
            c.description = COALESCE($description, c.description)
        WITH c
        MATCH (t:Table {id: $tableId})
        MERGE (t)-[:HAS_COLUMN]->(c)
        """

        await session.run(
            query,
            id=column.id,
            name=column.name,
            dataType=column.data_type,
            description=column.description,
            nullable=column.nullable,
            autoIncrement=column.auto_increment,
            defaultValue=column.default_value,
            tableId=table_id,
        )

        # 将 Column embedding 任务入队
        await self._queue_embedding_task(column.id, "Column", column.name, column.description)

        self._columns_written += 1
        logger.debug("column_written", id=column.id, name=column.name)

    async def write_metric(self, session: AsyncSession, metric: MetricNode) -> None:
        """写入 Metric 节点"""
        label = self._get_metric_label(metric.metric_type)

        query = f"""
        MERGE (m:{label}:Knowledge {{id: $id}})
        ON CREATE SET
            m.createdAt = datetime(),
            m.name = $name,
            m.code = $code,
            m.description = $description,
            m.unit = $unit,
            m.aggregationLogic = $aggregationLogic,
            m.calculationFormula = $calculationFormula,
            m.createdBy = 'OPENLINEAGE'
        ON MATCH SET
            m.updatedAt = datetime(),
            m.description = COALESCE($description, m.description)
        """

        await session.run(
            query,
            id=metric.id,
            name=metric.name,
            code=metric.code,
            description=metric.description,
            unit=metric.unit,
            aggregationLogic=metric.aggregation_logic,
            calculationFormula=metric.calculation_formula,
        )

        # 将 Metric embedding 任务入队
        await self._queue_embedding_task(metric.id, label, metric.name, metric.description)

        # 写入指标依赖关系
        if metric.parent_metric_ids:
            await self._write_metric_relationships(session, metric)

        self._metrics_written += 1
        logger.debug("metric_written", id=metric.id, name=metric.name)

    def _get_metric_label(self, metric_type: str) -> str:
        """获取指标节点标签"""
        label_map = {
            "ATOMIC": "AtomicMetric",
            "DERIVED": "DerivedMetric",
            "COMPOSITE": "CompositeMetric",
        }
        return label_map.get(metric_type.upper(), "AtomicMetric")

    async def _write_metric_relationships(
        self, session: AsyncSession, metric: MetricNode
    ) -> None:
        """写入指标依赖关系"""
        label = self._get_metric_label(metric.metric_type)
        rel_type = "DERIVED_FROM" if metric.metric_type.upper() == "DERIVED" else "COMPUTED_FROM"

        for parent_id in metric.parent_metric_ids:
            query = f"""
            MATCH (child:{label} {{id: $childId}})
            MATCH (parent:AtomicMetric {{id: $parentId}})
            MERGE (child)-[r:{rel_type}]->(parent)
            ON CREATE SET r.createdAt = datetime()
            """

            await session.run(
                query,
                childId=metric.id,
                parentId=parent_id,
            )

    def get_detailed_stats(self) -> dict:
        """获取详细统计"""
        stats = self.get_stats().to_dict()
        stats["catalogs_written"] = self._catalogs_written
        stats["schemas_written"] = self._schemas_written
        stats["tables_written"] = self._tables_written
        stats["columns_written"] = self._columns_written
        stats["metrics_written"] = self._metrics_written
        stats["embedding_tasks_queued"] = self._embedding_tasks_queued
        return stats
