"""
Gravitino 元数据同步服务

启动时从 Gravitino 数据库全量同步元数据到 Neo4j
确保 Neo4j 知识图谱数据与 Gravitino 保持一致
"""

import json
import structlog
from neo4j import AsyncSession

from src.shared.config import settings
from src.infrastructure.database.gravitino import GravitinoDBClient
from src.infrastructure.database.neo4j import AsyncNeo4jClient
from src.modules.openlineage.core.embedding_processor import embedding_processor
from src.modules.openlineage.schemas.neo4j import generate_id

logger = structlog.get_logger()


class GravitinoSyncService:
    """Gravitino 元数据同步服务"""

    def __init__(self):
        self._metalake_name = settings.gravitino_sync_metalake
        self._metalake_id: int | None = None
        self._stats = {
            "catalogs": 0,
            "schemas": 0,
            "tables": 0,
            "columns": 0,
            "metrics": 0,
            "wordroots": 0,
            "modifiers": 0,
            "units": 0,
            "valuedomains": 0,
            "metric_column_lineage": 0,
            "column_valuedomain_lineage": 0,
            "tags_synced": 0,
            "embedding_tasks_queued": 0,
            "embedding_tasks_skipped": 0,
        }
        # 已有正确 embedding 的节点 ID 集合（provider 匹配才算有效）
        self._valid_embeddings: set[str] = set()
        # 当前 embedding provider 标识
        self._current_embedding_provider: str = ""

    async def _load_valid_embeddings(self, session) -> None:
        """
        加载 Neo4j 中 embedding provider 与当前配置匹配的节点 ID

        只有 provider 匹配的节点才跳过向量化，避免：
        1. 重复向量化浪费 API 费用
        2. 切换模型后数据不一致
        """
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        try:
            embedder = UnifiedEmbedder()
            self._current_embedding_provider = f"{embedder.provider}/{embedder.model_name}"
        except Exception as e:
            logger.warning("gravitino_sync_embedder_init_failed", error=str(e))
            self._current_embedding_provider = ""
            return

        query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND n.embeddingProvider = $provider
        RETURN n.id AS id
        """
        result = await session.run(query, provider=self._current_embedding_provider)
        records = await result.data()
        self._valid_embeddings = {r["id"] for r in records}

        # 统计不匹配的节点数量（将被重新向量化）
        count_query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND (n.embeddingProvider IS NULL OR n.embeddingProvider <> $provider)
        RETURN count(n) AS count
        """
        count_result = await session.run(count_query, provider=self._current_embedding_provider)
        count_record = await count_result.single()
        stale_count = count_record["count"] if count_record else 0

        logger.info(
            "gravitino_sync_embedding_check",
            current_provider=self._current_embedding_provider,
            valid_count=len(self._valid_embeddings),
            stale_count=stale_count,
        )

    async def _queue_embedding_task(
        self, node_id: str, node_label: str, name: str,
        description: str | None = None, tags: list[str] | None = None
    ) -> None:
        """将 embedding 任务入队（跳过无描述或 provider 匹配的节点）"""
        # 没有描述的节点不做 embedding，避免低质量向量误导检索
        if not description or not description.strip():
            self._stats["embedding_tasks_skipped"] += 1
            return

        # 跳过已有正确 embedding 的节点（provider 匹配）
        if node_id in self._valid_embeddings:
            self._stats["embedding_tasks_skipped"] += 1
            return

        parts = [name, description]
        if tags:
            parts.extend(tags)
        text = " ".join(parts)
        if await embedding_processor.put(node_id, node_label, text):
            self._stats["embedding_tasks_queued"] += 1

    async def sync_all(self) -> dict:
        """
        全量同步 Gravitino 元数据到 Neo4j

        返回同步统计信息
        """
        logger.info("gravitino_sync_start", metalake=self._metalake_name)

        # 1. 获取 metalake_id
        self._metalake_id = self._get_metalake_id()
        if not self._metalake_id:
            logger.error("gravitino_sync_metalake_not_found", metalake=self._metalake_name)
            raise ValueError(f"Metalake '{self._metalake_name}' not found in Gravitino")

        logger.info("gravitino_sync_metalake_found", metalake_id=self._metalake_id)

        # 2. 获取 Neo4j 驱动
        driver = await AsyncNeo4jClient.get_driver()

        async with driver.session(database=settings.neo4j_database) as session:
            # 3. 加载已有正确 embedding 的节点（避免重复向量化）
            await self._load_valid_embeddings(session)

            # 4. 同步各类元数据
            await self._sync_catalogs(session)
            await self._sync_schemas(session)
            await self._sync_tables_and_columns(session)
            await self._sync_metrics(session)
            await self._sync_wordroots(session)
            await self._sync_modifiers(session)
            await self._sync_units(session)
            await self._sync_valuedomains(session)

            # 4. 同步血缘关系
            await self._sync_metric_column_lineage(session)
            await self._sync_column_valuedomain_lineage(session)

            # 5. 同步普通 tags（非 vd: 前缀）
            await self._sync_tags(session)

        logger.info("gravitino_sync_complete", stats=self._stats)
        return self._stats

    def _get_metalake_id(self) -> int | None:
        """获取 metalake ID"""
        query = """
        SELECT metalake_id
        FROM metalake_meta
        WHERE metalake_name = :name AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"name": self._metalake_name})
        return rows[0]["metalake_id"] if rows else None

    async def _sync_catalogs(self, session: AsyncSession) -> None:
        """同步 Catalog"""
        query = """
        SELECT cm.catalog_id, cm.catalog_name, cm.type, cm.provider, cm.catalog_comment, cm.properties,
               GROUP_CONCAT(t.tag_name) as tags
        FROM catalog_meta cm
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = cm.catalog_id
            AND tr.metadata_object_type = 'CATALOG'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta t ON tr.tag_id = t.tag_id
            AND t.deleted_at = 0
            AND t.tag_name NOT LIKE 'vd:%%'
        WHERE cm.metalake_id = :metalake_id AND cm.deleted_at = 0
        GROUP BY cm.catalog_id, cm.catalog_name, cm.type, cm.provider, cm.catalog_comment, cm.properties
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            catalog_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            properties = json.loads(row["properties"]) if row["properties"] else None
            tags = row["tags"].split(",") if row["tags"] else None

            cypher = """
            MERGE (c:Catalog:Knowledge {id: $id})
            ON CREATE SET
                c.createdAt = datetime(),
                c.name = $name,
                c.metalake = $metalake,
                c.catalogType = $catalogType,
                c.provider = $provider,
                c.description = $description,
                c.properties = $properties,
                c.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                c.updatedAt = datetime(),
                c.catalogType = $catalogType,
                c.provider = $provider,
                c.description = COALESCE($description, c.description),
                c.properties = COALESCE($properties, c.properties)
            """
            await session.run(
                cypher,
                id=catalog_id,
                name=row["catalog_name"],
                metalake=self._metalake_name,
                catalogType=row["type"],
                provider=row["provider"],
                description=row["catalog_comment"],
                properties=json.dumps(properties) if properties else None,
            )
            self._stats["catalogs"] += 1

            # 入队 embedding（带 tags）
            await self._queue_embedding_task(
                catalog_id, "Catalog", row["catalog_name"], row["catalog_comment"], tags
            )

        logger.debug("gravitino_sync_catalogs", count=self._stats["catalogs"])

    async def _sync_schemas(self, session: AsyncSession) -> None:
        """同步 Schema"""
        query = """
        SELECT s.schema_id, s.schema_name, s.schema_comment, s.properties,
               c.catalog_name,
               GROUP_CONCAT(t.tag_name) as tags
        FROM schema_meta s
        JOIN catalog_meta c ON s.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = s.schema_id
            AND tr.metadata_object_type = 'SCHEMA'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta t ON tr.tag_id = t.tag_id
            AND t.deleted_at = 0
            AND t.tag_name NOT LIKE 'vd:%%'
        WHERE s.metalake_id = :metalake_id AND s.deleted_at = 0
        GROUP BY s.schema_id, s.schema_name, s.schema_comment, s.properties, c.catalog_name
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            schema_id = generate_id("schema", self._metalake_name, row["catalog_name"], row["schema_name"])
            catalog_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            properties = json.loads(row["properties"]) if row["properties"] else None
            tags = row["tags"].split(",") if row["tags"] else None

            cypher = """
            MERGE (s:Schema:Knowledge {id: $id})
            ON CREATE SET
                s.createdAt = datetime(),
                s.name = $name,
                s.description = $description,
                s.properties = $properties,
                s.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                s.updatedAt = datetime(),
                s.description = COALESCE($description, s.description),
                s.properties = COALESCE($properties, s.properties)
            WITH s
            MATCH (c:Catalog {id: $catalogId})
            MERGE (c)-[:HAS_SCHEMA]->(s)
            """
            await session.run(
                cypher,
                id=schema_id,
                name=row["schema_name"],
                description=row["schema_comment"],
                properties=json.dumps(properties) if properties else None,
                catalogId=catalog_id,
            )
            self._stats["schemas"] += 1

            # 入队 embedding（带 tags）
            await self._queue_embedding_task(
                schema_id, "Schema", row["schema_name"], row["schema_comment"], tags
            )

        logger.debug("gravitino_sync_schemas", count=self._stats["schemas"])

    async def _sync_tables_and_columns(self, session: AsyncSession) -> None:
        """同步 Table 和 Column"""
        # 查询表（带 tags）
        table_query = """
        SELECT t.table_id, t.table_name, t.table_comment, t.current_version,
               s.schema_name, c.catalog_name,
               GROUP_CONCAT(tag.tag_name) as tags
        FROM table_meta t
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = t.table_id
            AND tr.metadata_object_type = 'TABLE'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta tag ON tr.tag_id = tag.tag_id
            AND tag.deleted_at = 0
            AND tag.tag_name NOT LIKE 'vd:%%'
        WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
        GROUP BY t.table_id, t.table_name, t.table_comment, t.current_version,
                 s.schema_name, c.catalog_name
        """
        tables = GravitinoDBClient.execute_query(table_query, {"metalake_id": self._metalake_id})

        for table in tables:
            table_id = generate_id(
                "table", self._metalake_name, table["catalog_name"],
                table["schema_name"], table["table_name"]
            )
            schema_id = generate_id(
                "schema", self._metalake_name, table["catalog_name"], table["schema_name"]
            )
            table_tags = table["tags"].split(",") if table["tags"] else None

            # 写入 Table 节点
            table_cypher = """
            MERGE (t:Table:Knowledge {id: $id})
            ON CREATE SET
                t.createdAt = datetime(),
                t.name = $name,
                t.description = $description,
                t.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                t.updatedAt = datetime(),
                t.description = COALESCE($description, t.description)
            WITH t
            MATCH (s:Schema {id: $schemaId})
            MERGE (s)-[:HAS_TABLE]->(t)
            """
            await session.run(
                table_cypher,
                id=table_id,
                name=table["table_name"],
                description=table.get("table_comment"),
                schemaId=schema_id,
            )
            self._stats["tables"] += 1

            # 将 Table embedding 任务入队（带 tags）
            await self._queue_embedding_task(
                table_id, "Table", table["table_name"], table.get("table_comment"), table_tags
            )

            # 查询并写入列（带 tags）
            column_query = """
            SELECT col.column_id, col.column_name, col.column_type, col.column_comment,
                   col.column_nullable, col.column_auto_increment, col.column_default_value,
                   GROUP_CONCAT(tag.tag_name) as tags
            FROM table_column_version_info col
            LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = col.column_id
                AND tr.metadata_object_type = 'COLUMN'
                AND tr.deleted_at = 0
            LEFT JOIN tag_meta tag ON tr.tag_id = tag.tag_id
                AND tag.deleted_at = 0
                AND tag.tag_name NOT LIKE 'vd:%%'
            WHERE col.table_id = :table_id AND col.table_version = :version
                  AND col.deleted_at = 0 AND col.column_op_type != 3
            GROUP BY col.column_id, col.column_name, col.column_type, col.column_comment,
                     col.column_nullable, col.column_auto_increment, col.column_default_value,
                     col.column_position
            ORDER BY col.column_position
            """
            columns = GravitinoDBClient.execute_query(
                column_query,
                {"table_id": table["table_id"], "version": table["current_version"]}
            )

            for col in columns:
                column_id = generate_id(
                    "column", self._metalake_name, table["catalog_name"],
                    table["schema_name"], table["table_name"], col["column_name"]
                )
                col_tags = col["tags"].split(",") if col["tags"] else None

                column_cypher = """
                MERGE (col:Column:Knowledge {id: $id})
                ON CREATE SET
                    col.createdAt = datetime(),
                    col.name = $name,
                    col.dataType = $dataType,
                    col.description = $description,
                    col.nullable = $nullable,
                    col.autoIncrement = $autoIncrement,
                    col.defaultValue = $defaultValue,
                    col.createdBy = 'GRAVITINO_SYNC'
                ON MATCH SET
                    col.updatedAt = datetime(),
                    col.dataType = $dataType,
                    col.description = COALESCE($description, col.description),
                    col.nullable = $nullable,
                    col.autoIncrement = $autoIncrement,
                    col.defaultValue = $defaultValue
                WITH col
                MATCH (t:Table {id: $tableId})
                MERGE (t)-[:HAS_COLUMN]->(col)
                """
                await session.run(
                    column_cypher,
                    id=column_id,
                    name=col["column_name"],
                    dataType=col["column_type"],
                    description=col["column_comment"],
                    nullable=bool(col["column_nullable"]),
                    autoIncrement=bool(col["column_auto_increment"]),
                    defaultValue=col["column_default_value"],
                    tableId=table_id,
                )
                self._stats["columns"] += 1

                # 将 Column embedding 任务入队（带 tags）
                await self._queue_embedding_task(
                    column_id, "Column", col["column_name"], col["column_comment"], col_tags
                )

        logger.debug(
            "gravitino_sync_tables_columns",
            tables=self._stats["tables"],
            columns=self._stats["columns"]
        )

    async def _sync_metrics(self, session: AsyncSession) -> None:
        """同步 Metric"""
        # 查询指标主表和当前版本信息
        query = """
        SELECT m.metric_id, m.metric_name, m.metric_code, m.metric_type,
               m.data_type, m.metric_comment, m.current_version,
               v.metric_unit, v.calculation_formula, v.parent_metric_codes,
               v.ref_catalog_name, v.ref_schema_name, v.ref_table_name,
               v.measure_columns, v.filter_columns,
               s.schema_name, c.catalog_name
        FROM metric_meta m
        JOIN schema_meta s ON m.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON m.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN metric_version_info v ON m.metric_id = v.metric_id
            AND m.current_version = v.version AND v.deleted_at = 0
        WHERE m.metalake_id = :metalake_id AND m.deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            metric_id = generate_id("metric", row["metric_code"])
            metric_type = row["metric_type"].upper()
            label = {
                "ATOMIC": "AtomicMetric",
                "DERIVED": "DerivedMetric",
                "COMPOSITE": "CompositeMetric",
            }.get(metric_type, "AtomicMetric")

            schema_id = generate_id(
                "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
            )

            cypher = f"""
            MERGE (m:{label}:Knowledge {{id: $id}})
            ON CREATE SET
                m.createdAt = datetime(),
                m.name = $name,
                m.code = $code,
                m.description = $description,
                m.unit = $unit,
                m.calculationFormula = $calculationFormula,
                m.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                m.updatedAt = datetime(),
                m.name = $name,
                m.description = COALESCE($description, m.description),
                m.unit = COALESCE($unit, m.unit),
                m.calculationFormula = COALESCE($calculationFormula, m.calculationFormula)
            """

            # 原子指标挂在 Schema 下
            if metric_type == "ATOMIC":
                cypher += """
                WITH m
                MATCH (s:Schema {id: $schemaId})
                MERGE (s)-[:HAS_METRIC]->(m)
                """

            await session.run(
                cypher,
                id=metric_id,
                name=row["metric_name"],
                code=row["metric_code"],
                description=row["metric_comment"],
                unit=row["metric_unit"],
                calculationFormula=row["calculation_formula"],
                schemaId=schema_id,
            )

            # 将 Metric embedding 任务入队
            await self._queue_embedding_task(
                metric_id, label, row["metric_name"], row["metric_comment"]
            )

            # 处理派生/复合指标的父子关系
            if row["parent_metric_codes"] and metric_type in ("DERIVED", "COMPOSITE"):
                # parent_metric_codes 是逗号分隔的字符串
                parent_codes = [
                    code.strip()
                    for code in row["parent_metric_codes"].split(",")
                    if code.strip()
                ]
                rel_type = "DERIVED_FROM" if metric_type == "DERIVED" else "COMPUTED_FROM"

                # 先删除旧的父子关系，确保数据一致
                delete_cypher = f"""
                MATCH (child:{label} {{id: $childId}})-[r:{rel_type}]->()
                DELETE r
                """
                await session.run(delete_cypher, childId=metric_id)

                for parent_code in parent_codes:
                    parent_metric_id = generate_id("metric", parent_code)
                    rel_cypher = f"""
                    MATCH (child:{label} {{id: $childId}})
                    MATCH (parent:AtomicMetric {{id: $parentId}})
                    MERGE (child)-[r:{rel_type}]->(parent)
                    ON CREATE SET r.createdAt = datetime()
                    """
                    await session.run(
                        rel_cypher,
                        childId=metric_id,
                        parentId=parent_metric_id,
                    )

            self._stats["metrics"] += 1

        logger.debug("gravitino_sync_metrics", count=self._stats["metrics"])

    async def _sync_wordroots(self, session: AsyncSession) -> None:
        """同步 WordRoot"""
        query = """
        SELECT root_code, root_name, data_type, root_comment
        FROM wordroot_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            wordroot_id = generate_id("wordroot", row["root_code"])

            cypher = """
            MERGE (w:WordRoot:Knowledge {id: $id})
            ON CREATE SET
                w.createdAt = datetime(),
                w.code = $code,
                w.name = $name,
                w.dataType = $dataType,
                w.description = $description,
                w.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                w.updatedAt = datetime(),
                w.name = COALESCE($name, w.name),
                w.dataType = COALESCE($dataType, w.dataType),
                w.description = COALESCE($description, w.description)
            """
            await session.run(
                cypher,
                id=wordroot_id,
                code=row["root_code"],
                name=row["root_name"],
                dataType=row["data_type"],
                description=row["root_comment"],
            )

            # 将 WordRoot embedding 任务入队
            await self._queue_embedding_task(
                wordroot_id, "WordRoot",
                row["root_name"] or row["root_code"], row["root_comment"]
            )

            self._stats["wordroots"] += 1

        logger.debug("gravitino_sync_wordroots", count=self._stats["wordroots"])

    async def _sync_modifiers(self, session: AsyncSession) -> None:
        """同步 Modifier"""
        query = """
        SELECT modifier_code, modifier_name, modifier_type, modifier_comment
        FROM metric_modifier_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            modifier_id = generate_id("modifier", row["modifier_code"])

            cypher = """
            MERGE (m:Modifier:Knowledge {id: $id})
            ON CREATE SET
                m.createdAt = datetime(),
                m.code = $code,
                m.modifierType = $modifierType,
                m.description = $description,
                m.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                m.updatedAt = datetime(),
                m.modifierType = COALESCE($modifierType, m.modifierType),
                m.description = COALESCE($description, m.description)
            """
            await session.run(
                cypher,
                id=modifier_id,
                code=row["modifier_code"],
                modifierType=row["modifier_type"],
                description=row["modifier_comment"],
            )

            # 将 Modifier embedding 任务入队
            await self._queue_embedding_task(
                modifier_id, "Modifier", row["modifier_code"], row["modifier_comment"]
            )

            self._stats["modifiers"] += 1

        logger.debug("gravitino_sync_modifiers", count=self._stats["modifiers"])

    async def _sync_units(self, session: AsyncSession) -> None:
        """同步 Unit"""
        query = """
        SELECT unit_code, unit_name, unit_symbol, unit_comment
        FROM unit_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            unit_id = generate_id("unit", row["unit_code"])

            cypher = """
            MERGE (u:Unit:Knowledge {id: $id})
            ON CREATE SET
                u.createdAt = datetime(),
                u.code = $code,
                u.name = $name,
                u.symbol = $symbol,
                u.description = $description,
                u.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                u.updatedAt = datetime(),
                u.name = COALESCE($name, u.name),
                u.symbol = COALESCE($symbol, u.symbol),
                u.description = COALESCE($description, u.description)
            """
            await session.run(
                cypher,
                id=unit_id,
                code=row["unit_code"],
                name=row["unit_name"],
                symbol=row["unit_symbol"],
                description=row["unit_comment"],
            )

            # 将 Unit embedding 任务入队
            await self._queue_embedding_task(
                unit_id, "Unit",
                row["unit_name"] or row["unit_code"], row["unit_comment"]
            )

            self._stats["units"] += 1

        logger.debug("gravitino_sync_units", count=self._stats["units"])

    async def _sync_valuedomains(self, session: AsyncSession) -> None:
        """同步 ValueDomain"""
        query = """
        SELECT domain_code, domain_name, domain_type, domain_level,
               items, data_type, domain_comment
        FROM value_domain_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            valuedomain_id = generate_id("valuedomain", row["domain_code"])

            cypher = """
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
                v.createdBy = 'GRAVITINO_SYNC'
            ON MATCH SET
                v.updatedAt = datetime(),
                v.domainName = COALESCE($domainName, v.domainName),
                v.domainType = COALESCE($domainType, v.domainType),
                v.domainLevel = COALESCE($domainLevel, v.domainLevel),
                v.items = COALESCE($items, v.items),
                v.dataType = COALESCE($dataType, v.dataType),
                v.description = COALESCE($description, v.description)
            """
            await session.run(
                cypher,
                id=valuedomain_id,
                domainCode=row["domain_code"],
                domainName=row["domain_name"],
                domainType=row["domain_type"],
                domainLevel=row["domain_level"],
                items=row["items"],
                dataType=row["data_type"],
                description=row["domain_comment"],
            )

            # 将 ValueDomain embedding 任务入队（包含 items 信息）
            embedding_text = row["domain_name"] or row["domain_code"]
            if row["items"]:
                embedding_text += f" {row['items']}"
            await self._queue_embedding_task(
                valuedomain_id, "ValueDomain", embedding_text, row["domain_comment"]
            )

            self._stats["valuedomains"] += 1

        logger.debug("gravitino_sync_valuedomains", count=self._stats["valuedomains"])

    async def _sync_metric_column_lineage(self, session: AsyncSession) -> None:
        """同步原子指标与列的血缘关系 (MEASURES, FILTERS_BY)"""
        # 查询原子指标的版本信息，包含 ref 表和列信息
        query = """
        SELECT m.metric_code, m.metric_type,
               v.ref_catalog_name, v.ref_schema_name, v.ref_table_name,
               v.measure_columns, v.filter_columns
        FROM metric_meta m
        JOIN metric_version_info v ON m.metric_id = v.metric_id
            AND m.current_version = v.version AND v.deleted_at = 0
        WHERE m.metalake_id = :metalake_id AND m.deleted_at = 0
            AND m.metric_type = 'ATOMIC'
            AND v.ref_catalog_name IS NOT NULL
            AND v.ref_schema_name IS NOT NULL
            AND v.ref_table_name IS NOT NULL
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            metric_id = generate_id("metric", row["metric_code"])
            ref_catalog = row["ref_catalog_name"]
            ref_schema = row["ref_schema_name"]
            ref_table = row["ref_table_name"]

            # 处理 measure_columns
            if row["measure_columns"]:
                try:
                    measure_cols = json.loads(row["measure_columns"])
                    for col in measure_cols:
                        col_name = col.get("name")
                        if not col_name:
                            continue
                        column_id = generate_id(
                            "column", self._metalake_name, ref_catalog,
                            ref_schema, ref_table, col_name
                        )
                        cypher = """
                        MATCH (m:AtomicMetric {id: $metricId})
                        MATCH (c:Column {id: $columnId})
                        MERGE (m)-[r:MEASURES]->(c)
                        ON CREATE SET r.createdAt = datetime()
                        """
                        await session.run(cypher, metricId=metric_id, columnId=column_id)
                        self._stats["metric_column_lineage"] += 1
                except json.JSONDecodeError:
                    pass

            # 处理 filter_columns
            if row["filter_columns"]:
                try:
                    filter_cols = json.loads(row["filter_columns"])
                    for col in filter_cols:
                        col_name = col.get("name")
                        if not col_name:
                            continue
                        column_id = generate_id(
                            "column", self._metalake_name, ref_catalog,
                            ref_schema, ref_table, col_name
                        )
                        cypher = """
                        MATCH (m:AtomicMetric {id: $metricId})
                        MATCH (c:Column {id: $columnId})
                        MERGE (m)-[r:FILTERS_BY]->(c)
                        ON CREATE SET r.createdAt = datetime()
                        """
                        await session.run(cypher, metricId=metric_id, columnId=column_id)
                        self._stats["metric_column_lineage"] += 1
                except json.JSONDecodeError:
                    pass

        logger.debug("gravitino_sync_metric_column_lineage", count=self._stats["metric_column_lineage"])

    async def _sync_column_valuedomain_lineage(self, session: AsyncSession) -> None:
        """同步列与值域的血缘关系 (HAS_VALUE_DOMAIN)"""
        # 查询 vd: 前缀的 tag 关联（值域与列的关系）
        # 只查询当前版本的列，避免旧版本数据覆盖新版本
        query = """
        SELECT t.tag_name, tr.metadata_object_id, tr.metadata_object_type
        FROM tag_meta t
        JOIN tag_relation_meta tr ON t.tag_id = tr.tag_id AND tr.deleted_at = 0
        JOIN table_column_version_info col ON tr.metadata_object_id = col.column_id
            AND col.deleted_at = 0 AND col.column_op_type != 3
        JOIN table_meta tbl ON col.table_id = tbl.table_id
            AND col.table_version = tbl.current_version
            AND tbl.deleted_at = 0
        WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
            AND t.tag_name LIKE 'vd:%%'
            AND tr.metadata_object_type = 'COLUMN'
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            # 提取 domain_code（去掉 vd: 前缀）
            domain_code = row["tag_name"][3:]  # 'vd:xxx' -> 'xxx'
            column_object_id = row["metadata_object_id"]

            # 查询列的详细信息以生成 column_id
            column_query = """
            SELECT col.column_name, t.table_name, s.schema_name, c.catalog_name
            FROM table_column_version_info col
            JOIN table_meta t ON col.table_id = t.table_id AND t.deleted_at = 0
            JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
            JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
            WHERE col.column_id = :column_id AND col.deleted_at = 0
                AND col.column_op_type != 3
            LIMIT 1
            """
            col_rows = GravitinoDBClient.execute_query(
                column_query, {"column_id": column_object_id}
            )

            if not col_rows:
                continue

            col_info = col_rows[0]
            column_id = generate_id(
                "column", self._metalake_name, col_info["catalog_name"],
                col_info["schema_name"], col_info["table_name"], col_info["column_name"]
            )

            cypher = """
            MATCH (c:Column {id: $columnId})
            MATCH (v:ValueDomain {domainCode: $domainCode})
            // 先删除该列上已有的值域关系（一个列只能有一个值域）
            OPTIONAL MATCH (c)-[old:HAS_VALUE_DOMAIN]->()
            DELETE old
            WITH c, v
            MERGE (c)-[r:HAS_VALUE_DOMAIN]->(v)
            ON CREATE SET r.createdAt = datetime()
            """
            await session.run(cypher, columnId=column_id, domainCode=domain_code)
            self._stats["column_valuedomain_lineage"] += 1

        logger.debug("gravitino_sync_column_valuedomain_lineage", count=self._stats["column_valuedomain_lineage"])

    async def _sync_tags(self, session: AsyncSession) -> None:
        """
        同步普通 tags（非 vd: 前缀）

        将 tag_relation_meta 中的普通 tags 同步到对应节点的 tags 属性
        """
        # 查询所有非 vd: 前缀的 tag 关联，按对象分组
        query = """
        SELECT tr.metadata_object_id, tr.metadata_object_type,
               GROUP_CONCAT(t.tag_name) as tags
        FROM tag_meta t
        JOIN tag_relation_meta tr ON t.tag_id = tr.tag_id AND tr.deleted_at = 0
        WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
            AND t.tag_name NOT LIKE 'vd:%%'
        GROUP BY tr.metadata_object_id, tr.metadata_object_type
        """
        rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

        for row in rows:
            object_type = row["metadata_object_type"]
            object_id = row["metadata_object_id"]
            tags = row["tags"].split(",") if row["tags"] else []

            if not tags:
                continue

            # 根据对象类型查询详细信息并更新 Neo4j
            if object_type == "TABLE":
                await self._update_table_tags(session, object_id, tags)
            elif object_type == "COLUMN":
                await self._update_column_tags(session, object_id, tags)
            elif object_type == "SCHEMA":
                await self._update_schema_tags(session, object_id, tags)
            elif object_type == "CATALOG":
                await self._update_catalog_tags(session, object_id, tags)

            self._stats["tags_synced"] += len(tags)

        logger.debug("gravitino_sync_tags", count=self._stats["tags_synced"])

    async def _update_table_tags(
        self, session: AsyncSession, table_id: int, tags: list[str]
    ) -> None:
        """更新 Table 节点的 tags（同步时已入队 embedding，这里只写属性）"""
        # 查询表信息
        query = """
        SELECT t.table_name, s.schema_name, c.catalog_name
        FROM table_meta t
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE t.table_id = :table_id AND t.deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"table_id": table_id})
        if not rows:
            return

        row = rows[0]
        node_id = generate_id(
            "table", self._metalake_name, row["catalog_name"],
            row["schema_name"], row["table_name"]
        )

        cypher = """
        MATCH (n:Table {id: $nodeId})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        await session.run(cypher, nodeId=node_id, tags=tags)

    async def _update_column_tags(
        self, session: AsyncSession, column_id: int, tags: list[str]
    ) -> None:
        """更新 Column 节点的 tags（同步时已入队 embedding，这里只写属性）"""
        # 查询列信息（当前版本）
        query = """
        SELECT col.column_name, t.table_name, s.schema_name, c.catalog_name
        FROM table_column_version_info col
        JOIN table_meta t ON col.table_id = t.table_id
            AND col.table_version = t.current_version
            AND t.deleted_at = 0
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE col.column_id = :column_id AND col.deleted_at = 0
            AND col.column_op_type != 3
        LIMIT 1
        """
        rows = GravitinoDBClient.execute_query(query, {"column_id": column_id})
        if not rows:
            return

        row = rows[0]
        node_id = generate_id(
            "column", self._metalake_name, row["catalog_name"],
            row["schema_name"], row["table_name"], row["column_name"]
        )

        cypher = """
        MATCH (n:Column {id: $nodeId})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        await session.run(cypher, nodeId=node_id, tags=tags)

    async def _update_schema_tags(
        self, session: AsyncSession, schema_id: int, tags: list[str]
    ) -> None:
        """更新 Schema 节点的 tags"""
        query = """
        SELECT s.schema_name, c.catalog_name
        FROM schema_meta s
        JOIN catalog_meta c ON s.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE s.schema_id = :schema_id AND s.deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"schema_id": schema_id})
        if not rows:
            return

        row = rows[0]
        node_id = generate_id(
            "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
        )

        cypher = """
        MATCH (n:Schema {id: $nodeId})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        await session.run(cypher, nodeId=node_id, tags=tags)

    async def _update_catalog_tags(
        self, session: AsyncSession, catalog_id: int, tags: list[str]
    ) -> None:
        """更新 Catalog 节点的 tags"""
        query = """
        SELECT catalog_name
        FROM catalog_meta
        WHERE catalog_id = :catalog_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"catalog_id": catalog_id})
        if not rows:
            return

        row = rows[0]
        node_id = generate_id("catalog", self._metalake_name, row["catalog_name"])

        cypher = """
        MATCH (n:Catalog {id: $nodeId})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        await session.run(cypher, nodeId=node_id, tags=tags)


async def sync_gravitino_metadata() -> dict:
    """执行 Gravitino 元数据同步"""
    service = GravitinoSyncService()
    return await service.sync_all()
