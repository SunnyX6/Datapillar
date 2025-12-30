"""
Neo4j 知识图谱统一数据访问层

结构说明：
1. DTOs - 数据传输对象
2. Cypher 查询 - 语义资产（词根、修饰符、单位、表上下文）
3. Cypher 查询 - 物理资产（表、列、血缘、SQL）
4. GraphRAG 向量检索 - 基于 neo4j_graphrag 的向量/混合检索
5. 知识写回 - 持久化用户确认的知识
"""

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from src.infrastructure.database import AsyncNeo4jClient, Neo4jClient, convert_neo4j_types
from src.shared.config import settings

logger = logging.getLogger(__name__)


# =============================================================================
# 1. DTOs - 数据传输对象
# =============================================================================

@dataclass
class WordRootDTO:
    """词根"""
    code: str
    name: str | None = None
    data_type: str | None = None
    description: str | None = None


@dataclass
class ModifierDTO:
    """修饰符"""
    code: str
    modifier_type: str | None = None
    description: str | None = None


@dataclass
class UnitDTO:
    """单位"""
    code: str
    name: str | None = None
    symbol: str | None = None
    description: str | None = None


@dataclass
class TableContextDTO:
    """表上下文"""
    catalog: str
    schema: str
    table: str
    description: str | None = None
    columns: list[dict[str, Any]] | None = None


@dataclass
class MetricDTO:
    """指标（精简版，用于 AI 上下文）"""
    code: str
    name: str | None = None
    description: str | None = None


@dataclass
class MetricContextDTO:
    """指标上下文（完整版，用于 AI 填写派生/复合指标）"""
    code: str
    name: str | None = None
    description: str | None = None
    metric_type: str | None = None  # AtomicMetric / DerivedMetric / CompositeMetric
    unit: str | None = None
    calculation_formula: str | None = None
    aggregation_logic: str | None = None


class Neo4jKGRepository:
    """Neo4j 知识图谱统一数据访问层"""

    # GraphRAG 向量检索器缓存
    _vector_retrievers: dict[str, Any] = {}

    # =========================================================================
    # 2. Cypher 查询 - 语义资产（词根、修饰符、单位、表上下文）
    # =========================================================================

    @classmethod
    async def get_word_roots(cls, limit: int = 100) -> list[WordRootDTO]:
        """获取词根列表"""
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (w:WordRoot)
        RETURN w.code AS code, w.name AS name, w.dataType AS dataType, w.description AS description
        ORDER BY w.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个词根")

        return [
            WordRootDTO(
                code=r["code"],
                name=r["name"],
                data_type=r["dataType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_modifiers(cls, limit: int = 100) -> list[ModifierDTO]:
        """获取修饰符列表"""
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (m:Modifier)
        RETURN m.code AS code, m.modifierType AS modifierType, m.description AS description
        ORDER BY m.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个修饰符")

        return [
            ModifierDTO(
                code=r["code"],
                modifier_type=r["modifierType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_units(cls, limit: int = 100) -> list[UnitDTO]:
        """获取单位列表"""
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (u:Unit)
        RETURN u.code AS code, u.name AS name, u.symbol AS symbol, u.description AS description
        ORDER BY u.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个单位")

        return [
            UnitDTO(
                code=r["code"],
                name=r["name"],
                symbol=r["symbol"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_table_context(
        cls, catalog: str, schema: str, table: str
    ) -> TableContextDTO | None:
        """
        获取表的上下文信息（描述、列信息、值域等）

        Args:
            catalog: 数据源名称
            schema: 数据库名称
            table: 表名

        Returns:
            表上下文，包含描述、列信息和值域
        """
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (col)-[:HAS_VALUE_DOMAIN]->(vd:ValueDomain)
        WITH t, col, vd
        ORDER BY col.name
        WITH t, collect({
            name: col.name,
            dataType: col.dataType,
            description: col.description,
            valueDomain: CASE WHEN vd IS NOT NULL THEN {
                code: vd.domainCode,
                name: vd.domainName,
                type: vd.domainType,
                items: vd.items
            } ELSE null END
        }) AS columns
        RETURN
            t.name AS name,
            t.description AS description,
            columns
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(
                query, catalog=catalog, schema=schema, table=table
            )
            record = await result.single()

        if not record:
            logger.debug(f"未找到表: {catalog}.{schema}.{table}")
            return None

        logger.debug(f"从 Neo4j 获取表上下文: {catalog}.{schema}.{table}")

        return TableContextDTO(
            catalog=catalog,
            schema=schema,
            table=record["name"],
            description=record["description"],
            columns=record["columns"],
        )

    # -------------------------------------------------------------------------
    # 同步版本（供 LangChain 工具调用）
    # -------------------------------------------------------------------------

    @classmethod
    def get_word_roots_sync(cls, limit: int = 100) -> list[WordRootDTO]:
        """获取词根列表（同步方法）"""
        driver = Neo4jClient.get_driver()

        query = """
        MATCH (w:WordRoot)
        RETURN w.code AS code, w.name AS name, w.dataType AS dataType, w.description AS description
        ORDER BY w.code
        LIMIT $limit
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = session.run(query, limit=limit)
            records = result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个词根")

        return [
            WordRootDTO(
                code=r["code"],
                name=r["name"],
                data_type=r["dataType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    def get_modifiers_sync(cls, limit: int = 100) -> list[ModifierDTO]:
        """获取修饰符列表（同步方法）"""
        driver = Neo4jClient.get_driver()

        query = """
        MATCH (m:Modifier)
        RETURN m.code AS code, m.modifierType AS modifierType, m.description AS description
        ORDER BY m.code
        LIMIT $limit
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = session.run(query, limit=limit)
            records = result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个修饰符")

        return [
            ModifierDTO(
                code=r["code"],
                modifier_type=r["modifierType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    def get_units_sync(cls, limit: int = 100) -> list[UnitDTO]:
        """获取单位列表（同步方法）"""
        driver = Neo4jClient.get_driver()

        query = """
        MATCH (u:Unit)
        RETURN u.code AS code, u.name AS name, u.symbol AS symbol, u.description AS description
        ORDER BY u.code
        LIMIT $limit
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = session.run(query, limit=limit)
            records = result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个单位")

        return [
            UnitDTO(
                code=r["code"],
                name=r["name"],
                symbol=r["symbol"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    def get_table_context_sync(
        cls, catalog: str, schema: str, table: str
    ) -> TableContextDTO | None:
        """获取表的上下文信息（同步方法）"""
        driver = Neo4jClient.get_driver()

        query = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (col)-[:HAS_VALUE_DOMAIN]->(vd:ValueDomain)
        WITH t, col, vd
        ORDER BY col.name
        WITH t, collect({
            name: col.name,
            dataType: col.dataType,
            description: col.description,
            valueDomain: CASE WHEN vd IS NOT NULL THEN {
                code: vd.domainCode,
                name: vd.domainName,
                type: vd.domainType,
                items: vd.items
            } ELSE null END
        }) AS columns
        RETURN
            t.name AS name,
            t.description AS description,
            columns
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = session.run(query, catalog=catalog, schema=schema, table=table)
            record = result.single()

        if not record:
            logger.debug(f"未找到表: {catalog}.{schema}.{table}")
            return None

        logger.debug(f"从 Neo4j 获取表上下文: {catalog}.{schema}.{table}")

        return TableContextDTO(
            catalog=catalog,
            schema=schema,
            table=record["name"],
            description=record["description"],
            columns=record["columns"],
        )

    @classmethod
    def get_metric_context_sync(cls, codes: list[str]) -> list[MetricContextDTO]:
        """
        根据指标 code 列表查询指标详情（同步方法）

        Args:
            codes: 指标 code 列表

        Returns:
            指标上下文列表
        """
        if not codes:
            return []

        driver = Neo4jClient.get_driver()

        query = """
        UNWIND $codes AS code
        OPTIONAL MATCH (m:AtomicMetric {code: code})
        OPTIONAL MATCH (d:DerivedMetric {code: code})
        OPTIONAL MATCH (c:CompositeMetric {code: code})
        WITH code,
             COALESCE(m, d, c) AS metric,
             CASE
                WHEN m IS NOT NULL THEN 'AtomicMetric'
                WHEN d IS NOT NULL THEN 'DerivedMetric'
                WHEN c IS NOT NULL THEN 'CompositeMetric'
                ELSE null
             END AS metricType
        WHERE metric IS NOT NULL
        RETURN
            metric.code AS code,
            metric.name AS name,
            metric.description AS description,
            metricType AS metricType,
            metric.unit AS unit,
            metric.calculationFormula AS calculationFormula,
            metric.aggregationLogic AS aggregationLogic
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = session.run(query, codes=codes)
            records = result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个指标上下文")

        return [
            MetricContextDTO(
                code=r["code"],
                name=r["name"],
                description=r["description"],
                metric_type=r["metricType"],
                unit=r["unit"],
                calculation_formula=r["calculationFormula"],
                aggregation_logic=r["aggregationLogic"],
            )
            for r in records
        ]

    # =========================================================================
    # 3. Cypher 查询 - 物理资产（表、列、血缘、SQL）
    # =========================================================================

    @classmethod
    async def load_catalog_hierarchy(cls) -> list[dict[str, Any]]:
        """加载 Catalog -> Schema -> Table 层级结构"""
        cypher = """
        MATCH (cat:Catalog)-[:HAS_SCHEMA]->(sch:Schema)-[:HAS_TABLE]->(t:Table)
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        WITH cat, sch, t, count(col) as column_count
        WITH cat, sch, collect({
            name: t.name,
            schema_name: sch.name,
            catalog: cat.name,
            tags: coalesce(t.tags, []),
            description: t.description,
            column_count: column_count
        }) as tables
        WITH cat, collect({
            name: sch.name,
            catalog: cat.name,
            description: sch.description,
            tables: tables
        }) as schemas
        RETURN
            cat.name as name,
            cat.metalake as metalake,
            schemas
        ORDER BY cat.name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 Catalog 层级结构失败: {e}")
            return []

    @classmethod
    async def load_table_lineage(cls) -> list[dict[str, Any]]:
        """加载表级血缘图"""
        cypher = """
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target:Table)
        WITH source, target, sql
        MATCH (source)<-[:HAS_TABLE]-(source_schema:Schema)
        MATCH (target)<-[:HAS_TABLE]-(target_schema:Schema)
        RETURN DISTINCT
            source_schema.name + '.' + source.name as source_table,
            target_schema.name + '.' + target.name as target_table,
            sql.id as sql_id
        ORDER BY source_table, target_table
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载表级血缘失败: {e}")
            return []

    @classmethod
    async def load_sql_patterns(cls, limit: int = 10) -> list[dict[str, Any]]:
        """加载热门 SQL 模式"""
        cypher = """
        MATCH (t:Table)-[:INPUT_OF]->(sql:SQL)
        WITH sql, collect(DISTINCT t.name) as tables
        RETURN
            coalesce(sql.summary, sql.name, 'unknown') as pattern,
            tables,
            coalesce(sql.useCount, 1) as frequency
        ORDER BY frequency DESC
        LIMIT $limit
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, limit=limit)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 SQL 模式失败: {e}")
            return []

    @classmethod
    async def get_table_columns(cls, table_name: str) -> list[dict[str, Any]]:
        """获取表的所有列详情"""
        parts = table_name.split(".", 1)
        if len(parts) == 2:
            schema_name, table_only = parts
            where_clause = "t.name = $table_name AND sch.name = $schema_name"
            params = {"table_name": table_only, "schema_name": schema_name}
        else:
            where_clause = "t.name = $table_name"
            params = {"table_name": table_name}

        cypher = f"""
        MATCH (sch:Schema)-[:HAS_TABLE]->(t:Table)-[:HAS_COLUMN]->(col:Column)
        WHERE {where_clause}
        RETURN
            col.name as name,
            col.dataType as data_type,
            col.description as description,
            col.nullable as nullable,
            coalesce(col.tags, []) as tags
        ORDER BY col.name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, params)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取表列详情失败: {e}")
            return []

    @classmethod
    async def get_column_lineage(
        cls, source_table: str, target_table: str
    ) -> list[dict[str, Any]]:
        """获取列级血缘"""
        source_parts = source_table.split(".", 1)
        target_parts = target_table.split(".", 1)

        if len(source_parts) < 2 or len(target_parts) < 2:
            logger.warning("列级血缘查询需要 schema.table 格式的表名")
            return []

        source_schema, source_name = source_parts
        target_schema, target_name = target_parts

        cypher_derives_from = """
        MATCH (source:Table {name: $source_name})<-[:HAS_TABLE]-(src_sch:Schema {name: $source_schema})
        MATCH (target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})
        MATCH (source)-[:HAS_COLUMN]->(src_col:Column)<-[lineage:DERIVES_FROM]-(tgt_col:Column)<-[:HAS_COLUMN]-(target)
        OPTIONAL MATCH (source)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
        RETURN
            sql.id as sql_id,
            sql.content as sql_content,
            collect(DISTINCT {
                source_column: src_col.name,
                target_column: tgt_col.name,
                transformation: coalesce(lineage.transformationType, 'direct')
            }) as column_mappings
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher_derives_from, {
                    "source_name": source_name,
                    "source_schema": source_schema,
                    "target_name": target_name,
                    "target_schema": target_schema,
                })
                records = await result.data()

                has_valid_lineage = False
                for record in records:
                    mappings = record.get("column_mappings", [])
                    if mappings and any(m.get("source_column") for m in mappings):
                        has_valid_lineage = True
                        break

                if has_valid_lineage:
                    return [convert_neo4j_types(r) for r in records]

                logger.info(f"未找到 DERIVES_FROM 关系，降级为同名列匹配: {source_table} → {target_table}")
                cypher_same_name = """
                MATCH (source:Table {name: $source_name})<-[:HAS_TABLE]-(src_sch:Schema {name: $source_schema})
                MATCH (target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})
                MATCH (source)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
                OPTIONAL MATCH (source)-[:HAS_COLUMN]->(src_col:Column)
                OPTIONAL MATCH (target)-[:HAS_COLUMN]->(tgt_col:Column)
                WHERE src_col.name = tgt_col.name
                RETURN
                    sql.id as sql_id,
                    sql.content as sql_content,
                    collect(DISTINCT {
                        source_column: src_col.name,
                        target_column: tgt_col.name,
                        transformation: 'direct'
                    }) as column_mappings
                """
                result = await session.run(cypher_same_name, {
                    "source_name": source_name,
                    "source_schema": source_schema,
                    "target_name": target_name,
                    "target_schema": target_schema,
                })
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]

        except Exception as e:
            logger.error(f"获取列级血缘失败: {e}")
            return []

    @classmethod
    async def search_sql_by_tables(
        cls, tables: list[str], limit: int = 5
    ) -> list[dict[str, Any]]:
        """根据表名搜索相关 SQL"""
        cypher = """
        MATCH (t:Table)-[:INPUT_OF]->(sql:SQL)
        WHERE t.name IN $tables
        WITH sql, collect(DISTINCT t.name) as related_tables
        OPTIONAL MATCH (sql)-[:OUTPUT_TO]->(out:Table)
        WITH sql, related_tables + collect(DISTINCT out.name) as related_tables
        RETURN
            sql.id as sql_id,
            sql.name as name,
            sql.content as content,
            sql.summary as summary,
            coalesce(sql.useCount, 0) as use_count,
            coalesce(sql.confidence, 0.5) as confidence,
            related_tables
        ORDER BY sql.confidence DESC, sql.useCount DESC
        LIMIT $limit
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"tables": tables, "limit": limit})
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"搜索相关 SQL 失败: {e}")
            return []

    @classmethod
    async def get_sql_by_lineage(
        cls, source_tables: list[str], target_table: str
    ) -> dict[str, Any] | None:
        """根据血缘关系精准查找 SQL"""
        target_parts = target_table.split(".", 1)
        if len(target_parts) == 2:
            target_schema, target_name = target_parts
            target_match = "(target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})"
        else:
            target_name = target_table
            target_schema = None
            target_match = "(target:Table {name: $target_name})"

        source_names = []
        for src in source_tables:
            parts = src.split(".", 1)
            source_names.append(parts[1] if len(parts) == 2 else src)

        cypher = f"""
        MATCH {target_match}
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
        WHERE source.name IN $source_names
        WITH sql, target, collect(DISTINCT source.name) as source_tables
        RETURN
            sql.id as sql_id,
            sql.name as name,
            sql.content as content,
            sql.summary as summary,
            sql.engine as engine,
            sql.dialect as dialect,
            coalesce(sql.useCount, 0) as use_count,
            coalesce(sql.confidence, 0.5) as confidence,
            source_tables,
            target.name as target_table
        ORDER BY confidence DESC, use_count DESC
        LIMIT 1
        """

        params = {
            "source_names": source_names,
            "target_name": target_name,
        }
        if target_schema:
            params["target_schema"] = target_schema

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, params)
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"根据血缘查找 SQL 失败: {e}")
            return None

    @classmethod
    async def get_table_detail(cls, table_name: str) -> dict[str, Any] | None:
        """获取单表详情及下游表"""
        cypher = """
        MATCH (source:Table {name: $table_name})
        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (source)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)
        WITH source,
             collect(DISTINCT {
                 name: col.name,
                 displayName: col.displayName,
                 dataType: col.dataType,
                 description: col.description
             }) as columns,
             collect(DISTINCT downstream.name) as downstream_tables
        RETURN
            source.name as table_name,
            source.displayName as display_name,
            source.description as description,
            columns,
            downstream_tables
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_name": table_name})
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表详情失败: {e}")
            return None

    @classmethod
    async def get_table_lineage_detail(cls, source_table: str, target_table: str) -> dict[str, Any] | None:
        """获取源表到目标表的列级血缘"""
        cypher = """
        MATCH (source:Table {name: $source_table})
        MATCH (target:Table {name: $target_table})
        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(source_col:Column)
        OPTIONAL MATCH (target)-[:HAS_COLUMN]->(target_col:Column)
        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(sc:Column)<-[:DERIVES_FROM]-(tc:Column)<-[:HAS_COLUMN]-(target)
        RETURN
            source.name as source_table_name,
            source.displayName as source_display_name,
            source.description as source_description,
            collect(DISTINCT {
                name: source_col.name,
                displayName: source_col.displayName,
                dataType: source_col.dataType,
                description: source_col.description
            }) as source_columns,
            target.name as target_table_name,
            target.displayName as target_display_name,
            target.description as target_description,
            collect(DISTINCT {
                name: target_col.name,
                displayName: target_col.displayName,
                dataType: target_col.dataType,
                description: target_col.description
            }) as target_columns,
            collect(DISTINCT {
                source_column: sc.name,
                target_column: tc.name,
                transformation_type: "direct"
            }) as column_lineage
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {
                    "source_table": source_table,
                    "target_table": target_table
                })
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表血缘详情失败: {e}")
            return None

    @classmethod
    async def search_tables_with_context(cls, table_ids: list[str]) -> list[dict[str, Any]]:
        """基于表 ID 列表，获取表详情及业务上下文"""
        cypher = """
        UNWIND $table_ids AS table_id
        MATCH (table:Table)
        WHERE elementId(table) = table_id
        OPTIONAL MATCH (table)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (table)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)
        MATCH (table)<-[:CONTAINS]-(sch:Schema)<-[:CONTAINS]-(subj:Subject)<-[:CONTAINS]-(cat:Catalog)<-[:CONTAINS]-(dom:Domain)
        WITH table, sch, subj, cat, dom,
             collect(DISTINCT {
                 name: col.name,
                 displayName: col.displayName,
                 dataType: col.dataType,
                 description: col.description
             }) as columns,
             collect(DISTINCT downstream.name) as downstream_tables
        RETURN
            elementId(table) as table_id,
            table.name as table_name,
            table.displayName as table_display_name,
            table.description as table_description,
            columns,
            downstream_tables,
            sch.layer as schema_layer,
            sch.displayName as schema_name,
            subj.displayName as subject_name,
            cat.displayName as catalog_name,
            dom.displayName as domain_name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_ids": table_ids})
                records = await result.data()
                return [convert_neo4j_types(record) for record in records]
        except Exception as e:
            logger.error(f"搜索表上下文失败: {e}")
            return []

    @staticmethod
    def get_initial_graph(limit: int = 50) -> list[dict[str, Any]]:
        """获取初始图数据（同步方法）"""
        query = """
        MATCH (n)
        WITH collect(n)[0..$limit] AS nodes
        UNWIND nodes AS n
        OPTIONAL MATCH (n)-[r]-(m)
        WHERE m IN nodes
        WITH nodes, collect(DISTINCT r) AS rels
        RETURN
            [n IN nodes | {id: id(n), type: labels(n)[0], properties: properties(n)}] AS nodes,
            [r IN rels WHERE r IS NOT NULL | {id: id(r), start: id(startNode(r)), end: id(endNode(r)), type: type(r), properties: properties(r)}] AS relationships
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = session.run(query, {"limit": limit})
                return [convert_neo4j_types(record.data()) for record in result]
        except Exception as e:
            logger.error(f"获取初始图数据失败: {e}")
            return []

    # =========================================================================
    # 4. GraphRAG 向量检索 - 基于 neo4j_graphrag 的向量/混合检索
    # =========================================================================

    # Table/Column 上下文查询 Cypher 模板
    _TABLE_COLUMN_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (n:Knowledge)
    WHERE elementId(n) = eid
    WITH n, labels(n) AS node_labels

    // Column: 获取 Table -> Schema -> Catalog 路径
    OPTIONAL MATCH (t:Table)-[:HAS_COLUMN]->(n)
    WHERE 'Column' IN node_labels
    OPTIONAL MATCH (s:Schema)-[:HAS_TABLE]->(t)
    OPTIONAL MATCH (c:Catalog)-[:HAS_SCHEMA]->(s)

    // Table: 获取 Schema -> Catalog 路径
    OPTIONAL MATCH (s2:Schema)-[:HAS_TABLE]->(n)
    WHERE 'Table' IN node_labels
    OPTIONAL MATCH (c2:Catalog)-[:HAS_SCHEMA]->(s2)

    RETURN
        elementId(n) AS element_id,
        CASE
            WHEN 'Column' IN node_labels THEN 'Column'
            WHEN 'Table' IN node_labels THEN 'Table'
            ELSE head(node_labels)
        END AS type,
        CASE
            WHEN 'Column' IN node_labels THEN c.name + '.' + s.name + '.' + t.name + '.' + n.name
            WHEN 'Table' IN node_labels THEN c2.name + '.' + s2.name + '.' + n.name
            ELSE n.name
        END AS path,
        n.name AS name,
        n.description AS description,
        CASE WHEN 'Column' IN node_labels THEN n.dataType ELSE null END AS dataType,
        CASE WHEN 'Column' IN node_labels THEN c.name + '.' + s.name + '.' + t.name ELSE null END AS table
    """

    # Metric 上下文查询 Cypher 模板
    _METRIC_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (n:Knowledge)
    WHERE elementId(n) = eid
    WITH n, labels(n) AS node_labels
    WHERE 'AtomicMetric' IN node_labels OR 'DerivedMetric' IN node_labels OR 'CompositeMetric' IN node_labels

    RETURN
        elementId(n) AS element_id,
        CASE
            WHEN 'AtomicMetric' IN node_labels THEN 'AtomicMetric'
            WHEN 'DerivedMetric' IN node_labels THEN 'DerivedMetric'
            ELSE 'CompositeMetric'
        END AS type,
        n.code AS code,
        n.name AS name,
        n.description AS description
    """

    @classmethod
    def _get_vector_retriever(cls, index_name: str = "kg_unified_vector_index"):
        """懒加载向量检索器（按索引名缓存）"""
        if index_name not in cls._vector_retrievers:
            from neo4j_graphrag.retrievers import VectorRetriever
            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            try:
                cls._vector_retrievers[index_name] = VectorRetriever(
                    driver=Neo4jClient.get_driver(),
                    index_name=index_name,
                    embedder=UnifiedEmbedder(),
                    return_properties=["id", "name", "displayName", "description"]
                )
                logger.info(f"VectorRetriever[{index_name}] 初始化成功")
            except Exception as e:
                logger.warning(f"VectorRetriever[{index_name}] 初始化失败: {e}")
                return None
        return cls._vector_retrievers.get(index_name)

    @classmethod
    def _search_with_context(
        cls,
        query: str,
        index_names: list[str],
        cypher_template: str,
        top_k: int = 3,
        min_score: float = 0.8
    ) -> list[dict[str, Any]]:
        """
        向量搜索 + Cypher 批量获取完整上下文（私有方法）

        先在多个向量索引中搜索，合并结果后用 Cypher 查询完整上下文。
        优化：
        1. 只调用一次 embedding API，复用向量在多个索引中搜索
        2. 使用 ThreadPoolExecutor 并行查询多个索引
        3. 过滤低于 min_score 阈值的结果
        """
        import time
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from src.infrastructure.llm.embeddings import UnifiedEmbedder
        start = time.time()

        # 使用单例 Embedder
        embedder = UnifiedEmbedder()

        embed_start = time.time()
        query_vector = embedder.embed_query(query)
        logger.debug(f"[向量搜索] embedding 耗时: {time.time() - embed_start:.3f}s")

        # 2. 使用线程池并行查询多个索引
        driver = Neo4jClient.get_driver()
        all_results = []

        def search_single_index(index_name: str) -> list[dict]:
            """单索引查询（用于并行执行）"""
            search_start = time.time()
            results = []
            try:
                with driver.session(database=settings.neo4j_database) as session:
                    cypher = """
                    CALL db.index.vector.queryNodes($index_name, $top_k, $vector)
                    YIELD node, score
                    WHERE score >= $min_score
                    RETURN elementId(node) AS element_id, score
                    """
                    result = session.run(cypher, {
                        "index_name": index_name,
                        "top_k": top_k,
                        "vector": query_vector,
                        "min_score": min_score
                    })
                    for record in result:
                        results.append({
                            "element_id": record["element_id"],
                            "score": record["score"]
                        })
                logger.debug(f"[向量搜索] {index_name} 耗时: {time.time() - search_start:.3f}s, 结果: {len(results)}")
            except Exception as e:
                logger.warning(f"向量搜索[{index_name}]失败: {e}")
            return results

        # 并行执行多索引查询
        parallel_start = time.time()
        with ThreadPoolExecutor(max_workers=len(index_names)) as executor:
            futures = {executor.submit(search_single_index, idx): idx for idx in index_names}
            for future in as_completed(futures):
                all_results.extend(future.result())
        logger.debug(f"[向量搜索] 并行查询耗时: {time.time() - parallel_start:.3f}s")

        if not all_results:
            return []

        # 3. 按 score 排序，取 top 5
        all_results.sort(key=lambda x: x.get("score", 0), reverse=True)
        all_results = all_results[:5]

        # 4. 提取 element_id 和 score
        element_ids = [r.get("element_id") for r in all_results if r.get("element_id")]
        score_map = {r.get("element_id"): r.get("score", 0) for r in all_results}

        if not element_ids:
            return []

        # 5. 单次 Cypher 批量查询完整上下文
        try:
            with driver.session(database=settings.neo4j_database) as session:
                result = session.run(cypher_template, {"element_ids": element_ids})
                records = result.data()

                recommendations = []
                for r in records:
                    eid = r.get("element_id")
                    r["score"] = round(score_map.get(eid, 0), 3)
                    del r["element_id"]
                    recommendations.append(r)

                logger.debug(f"[向量搜索] 总耗时: {time.time() - start:.3f}s")
                return recommendations
        except Exception as e:
            logger.error(f"搜索上下文失败: {e}")
            return []

    @classmethod
    def search_tables_columns(cls, query: str, top_k: int = 3) -> list[dict[str, Any]]:
        """
        搜索表和列（同步方法）

        向量搜索表和列索引，返回匹配的表和列及其完整路径。

        Args:
            query: 搜索文本
            top_k: 每个索引返回的数量

        Returns:
            推荐的表和列列表，包含 type, path, name, description, dataType, table, score
        """
        return cls._search_with_context(
            query=query,
            index_names=["table_embedding", "column_embedding"],
            cypher_template=cls._TABLE_COLUMN_CYPHER,
            top_k=top_k
        )

    @classmethod
    def search_metrics(cls, query: str, top_k: int = 3) -> list[dict[str, Any]]:
        """
        搜索指标（同步方法）

        向量搜索原子指标和派生指标索引，返回匹配的指标。

        Args:
            query: 搜索文本
            top_k: 每个索引返回的数量

        Returns:
            推荐的指标列表，包含 type, code, name, description, score
        """
        return cls._search_with_context(
            query=query,
            index_names=["atomic_metric_embedding", "derived_metric_embedding"],
            cypher_template=cls._METRIC_CYPHER,
            top_k=top_k
        )

    @classmethod
    def vector_search(
        cls,
        query: str,
        top_k: int = 10,
        index_name: str = "kg_unified_vector_index",
        filters: dict | None = None,
        min_score: float = 0.8
    ) -> list[dict[str, Any]]:
        """
        向量语义检索（同步方法）

        基于 neo4j_graphrag 的 VectorRetriever，使用向量相似度搜索知识图谱节点。

        Args:
            query: 搜索文本
            top_k: 返回数量
            index_name: 向量索引名称
            filters: 过滤条件
            min_score: 最小相似度阈值

        Returns:
            包含 element_id、content、score 的结果列表
        """
        retriever = cls._get_vector_retriever(index_name)
        if not retriever:
            return []

        try:
            results = retriever.search(query_text=query, top_k=top_k, filters=filters)
            return [
                {
                    "element_id": item.metadata.get("id") if item.metadata else None,
                    "content": item.content,
                    "score": item.metadata.get("score") if item.metadata else 0,
                }
                for item in results.items
                if item.metadata and item.metadata.get("score", 0) >= min_score
            ] if results.items else []
        except Exception as e:
            logger.error(f"向量检索失败[{index_name}]: {e}")
            return []

    # 语义资产 Cypher 模板
    _WORDROOT_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (w:WordRoot)
    WHERE elementId(w) = eid
    RETURN
        elementId(w) AS element_id,
        w.code AS code,
        w.name AS name,
        w.dataType AS dataType,
        w.description AS description
    """

    _MODIFIER_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (m:Modifier)
    WHERE elementId(m) = eid
    RETURN
        elementId(m) AS element_id,
        m.code AS code,
        m.modifierType AS modifierType,
        m.description AS description
    """

    _UNIT_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (u:Unit)
    WHERE elementId(u) = eid
    RETURN
        elementId(u) AS element_id,
        u.code AS code,
        u.name AS name,
        u.symbol AS symbol,
        u.description AS description
    """

    @classmethod
    def search_semantic_assets(
        cls,
        query: str,
        top_k: int = 10,
        min_score: float = 0.75
    ) -> dict[str, list]:
        """
        根据用户输入语义检索相关的词根、修饰符、单位（混合检索：向量+全文）

        Args:
            query: 用户输入文本
            top_k: 每种资产类型返回的数量
            min_score: 最小相似度阈值

        Returns:
            {
                "word_roots": [{"code": ..., "name": ..., "dataType": ..., "score": ...}],
                "modifiers": [{"code": ..., "modifierType": ..., "score": ...}],
                "units": [{"code": ..., "name": ..., "symbol": ..., "score": ...}]
            }
        """
        import time
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        # 1. 生成 query embedding（只调用一次）
        embedder = UnifiedEmbedder()
        query_vector = embedder.embed_query(query)

        driver = Neo4jClient.get_driver()

        # 2. 定义混合搜索配置
        search_configs = [
            {
                "name": "word_roots",
                "vector_index": "wordroot_embedding",
                "fulltext_index": "wordroot_fulltext",
                "cypher": cls._WORDROOT_CYPHER,
            },
            {
                "name": "modifiers",
                "vector_index": "modifier_embedding",
                "fulltext_index": "modifier_fulltext",
                "cypher": cls._MODIFIER_CYPHER,
            },
            {
                "name": "units",
                "vector_index": "unit_embedding",
                "fulltext_index": "unit_fulltext",
                "cypher": cls._UNIT_CYPHER,
            },
        ]

        def hybrid_search_single(config: dict) -> tuple[str, list]:
            """单资产类型混合搜索（支持降级）"""
            results = []
            score_map = {}

            try:
                with driver.session(database=settings.neo4j_database) as session:
                    # 向量搜索（先用高阈值）
                    vector_cypher = """
                    CALL db.index.vector.queryNodes($index_name, $top_k, $vector)
                    YIELD node, score
                    WHERE score >= $min_score
                    RETURN elementId(node) AS element_id, score, 'vector' AS source
                    """
                    vector_result = session.run(vector_cypher, {
                        "index_name": config["vector_index"],
                        "top_k": top_k,
                        "vector": query_vector,
                        "min_score": min_score
                    })
                    for record in vector_result:
                        eid = record["element_id"]
                        score_map[eid] = max(score_map.get(eid, 0), record["score"])

                    # 全文搜索
                    fulltext_cypher = """
                    CALL db.index.fulltext.queryNodes($index_name, $query)
                    YIELD node, score
                    WHERE score >= 0.5
                    RETURN elementId(node) AS element_id, score / 10.0 AS score, 'fulltext' AS source
                    LIMIT $top_k
                    """
                    fulltext_result = session.run(fulltext_cypher, {
                        "index_name": config["fulltext_index"],
                        "query": query,
                        "top_k": top_k
                    })
                    for record in fulltext_result:
                        eid = record["element_id"]
                        score_map[eid] = max(score_map.get(eid, 0), record["score"])

                    # 如果高阈值搜不到，降级用低阈值再搜
                    if not score_map:
                        fallback_min_score = 0.55
                        vector_result = session.run(vector_cypher, {
                            "index_name": config["vector_index"],
                            "top_k": top_k,
                            "vector": query_vector,
                            "min_score": fallback_min_score
                        })
                        for record in vector_result:
                            eid = record["element_id"]
                            score_map[eid] = max(score_map.get(eid, 0), record["score"])
                        if score_map:
                            logger.debug(f"[语义资产] {config['name']} 降级搜索(阈值={fallback_min_score}): {len(score_map)} 个")

                    if not score_map:
                        return (config["name"], [])

                    # 按分数排序取 top_k
                    sorted_items = sorted(score_map.items(), key=lambda x: x[1], reverse=True)[:top_k]
                    element_ids = [eid for eid, _ in sorted_items]

                    # 批量获取详情
                    detail_result = session.run(config["cypher"], {"element_ids": element_ids})
                    for record in detail_result:
                        data = dict(record)
                        eid = data.pop("element_id")
                        data["score"] = round(score_map.get(eid, 0), 3)
                        results.append(data)

                    # 按分数排序
                    results.sort(key=lambda x: x.get("score", 0), reverse=True)

            except Exception as e:
                logger.warning(f"语义资产搜索[{config['name']}]失败: {e}")

            return (config["name"], results)

        # 3. 并行执行三种资产类型的混合搜索
        result = {"word_roots": [], "modifiers": [], "units": []}

        with ThreadPoolExecutor(max_workers=3) as executor:
            futures = [executor.submit(hybrid_search_single, cfg) for cfg in search_configs]
            for future in as_completed(futures):
                name, items = future.result()
                result[name] = items

        logger.info(
            f"[语义资产检索] query={query[:20]}..., "
            f"词根={len(result['word_roots'])}, "
            f"修饰符={len(result['modifiers'])}, "
            f"单位={len(result['units'])}, "
            f"耗时={time.time() - start:.3f}s"
        )

        return result

    @classmethod
    def hybrid_search(
        cls,
        query: str,
        top_k: int = 10,
        vector_index: str = "kg_unified_vector_index",
        fulltext_index: str = "kg_unified_fulltext_index",
        min_score: float = 0.8
    ) -> list[dict[str, Any]]:
        """
        混合检索（向量 + 全文）（同步方法）

        结合向量语义检索和全文关键词检索，提高召回率。

        Args:
            query: 搜索文本
            top_k: 返回数量
            vector_index: 向量索引名称
            fulltext_index: 全文索引名称
            min_score: 最小相似度阈值

        Returns:
            包含 element_id、content、score 的结果列表
        """
        from neo4j_graphrag.retrievers import HybridRetriever
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        try:
            retriever = HybridRetriever(
                driver=Neo4jClient.get_driver(),
                vector_index_name=vector_index,
                fulltext_index_name=fulltext_index,
                embedder=UnifiedEmbedder(),
                return_properties=["name", "displayName", "description"]
            )
            results = retriever.search(query_text=query, top_k=top_k)
            return [
                {
                    "element_id": item.node.element_id if hasattr(item, 'node') else None,
                    "content": item.content,
                    "score": item.score
                }
                for item in results.items
                if item.score >= min_score
            ] if results.items else []
        except Exception as e:
            logger.error(f"混合检索失败: {e}")
            return []

    # =========================================================================
    # 5. 知识写回 - 持久化用户确认的知识
    # =========================================================================

    @classmethod
    async def persist_kg_updates(
        cls,
        updates: list[dict[str, Any]],
        user_id: str,
        session_id: str,
    ) -> int:
        """
        将用户确认过的结构化事实写回 Neo4j（事务性写入）

        使用显式事务确保原子性：要么全部成功，要么全部回滚。
        支持类型: table_role / lineage / col_map / join
        """
        if not updates:
            return 0

        ts = datetime.utcnow().isoformat()
        saved = 0

        driver = await AsyncNeo4jClient.get_driver()
        async with driver.session(database=settings.neo4j_database) as session:
            tx = await session.begin_transaction()
            try:
                for upd in updates:
                    upd_type = upd.get("type")
                    if upd_type == "table_role":
                        await tx.run(
                            """
                            MERGE (t:Table {name:$table})
                            MERGE (w:WorkflowSession {session_id:$session_id})
                            SET w.last_seen=$ts, w.user_id=$user_id
                            MERGE (t)-[r:ETL_ROLE {session_id:$session_id}]->(w)
                            SET r.type=$role, r.by_user=$user_id, r.ts=$ts, r.confidence=$confidence
                            """,
                            {
                                "table": upd.get("table"),
                                "role": upd.get("role"),
                                "user_id": user_id,
                                "session_id": session_id,
                                "ts": ts,
                                "confidence": float(upd.get("confidence", 0.5)),
                            },
                        )
                        saved += 1
                    elif upd_type == "lineage":
                        await tx.run(
                            """
                            MATCH (s:Table {name:$source_table})
                            MATCH (t:Table {name:$target_table})
                            MERGE (s)-[r:CONFIRMED_LINEAGE]->(t)
                            SET r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "col_map":
                        await tx.run(
                            """
                            MATCH (s:Table {name:$source_table})-[:HAS_COLUMN]->(sc:Column {name:$source_column})
                            MATCH (t:Table {name:$target_table})-[:HAS_COLUMN]->(tc:Column {name:$target_column})
                            MERGE (sc)-[r:CONFIRMED_MAP]->(tc)
                            SET r.transform=$transform, r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "source_column": upd.get("source_column"),
                                "target_column": upd.get("target_column"),
                                "transform": upd.get("transform", "direct"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "join":
                        await tx.run(
                            """
                            MATCH (l:Table {name:$left})
                            MATCH (r:Table {name:$right})
                            MERGE (l)-[j:JOIN_KEY]->(r)
                            SET j.on=$on, j.confidence=$confidence, j.by_user=$user_id, j.ts=$ts
                            """,
                            {
                                "left": upd.get("left"),
                                "right": upd.get("right"),
                                "on": upd.get("on") or [],
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    else:
                        logger.warning(f"[persist_kg_updates] 忽略未知类型: {upd_type}")

                await tx.commit()
                logger.info(f"[persist_kg_updates] 事务提交成功，写入 {saved} 条记录")

            except Exception as exc:
                await tx.rollback()
                logger.error(f"[persist_kg_updates] 事务回滚: {exc}")
                raise

        return saved

    @classmethod
    async def increment_sql_use_count(cls, sql_id: str) -> bool:
        """增加 SQL 节点的使用次数"""
        cypher = """
        MATCH (s:SQL {id: $sql_id})
        SET s.use_count = coalesce(s.use_count, 0) + 1,
            s.last_used = datetime()
        RETURN s.use_count as use_count
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"sql_id": sql_id})
                record = await result.single()
                if record:
                    logger.info(f"SQL 使用次数更新: {sql_id} -> {record['use_count']}")
                    return True
                return False
        except Exception as e:
            logger.error(f"更新 SQL 使用次数失败: {e}")
            return False
