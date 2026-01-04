"""
        Neo4j 知识图谱统一数据访问层（标准查询与写回）

结构说明：
1. Cypher 查询 - 语义资产（词根、修饰符、单位、表上下文）
2. Cypher 查询 - 物理资产（表、列、血缘、SQL）

注意：
- 查询与写回必须隔离：写回在 `kg/writeback.py`
"""

import logging
from typing import Any

from src.infrastructure.database import AsyncNeo4jClient, Neo4jClient, convert_neo4j_types
from src.infrastructure.database.cypher import arun_cypher, run_cypher
from src.infrastructure.repository.kg.dto import (
    MetricContextDTO,
    ModifierDTO,
    TableContextDTO,
    UnitDTO,
    WordRootDTO,
)
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


# =============================================================================
# DTOs 在 dto.py
# =============================================================================


class Neo4jKGRepositoryQueries:
    """Neo4j 知识图谱统一数据访问层"""

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
            result = await arun_cypher(session, query, limit=limit)
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
            result = await arun_cypher(session, query, limit=limit)
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
            result = await arun_cypher(session, query, limit=limit)
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
            result = await arun_cypher(session, query, catalog=catalog, schema=schema, table=table)
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
    def get_wordroots_sync(cls, limit: int = 100) -> list[WordRootDTO]:
        """获取词根列表（同步方法）"""
        driver = Neo4jClient.get_driver()

        query = """
        MATCH (w:WordRoot)
        RETURN w.code AS code, w.name AS name, w.dataType AS dataType, w.description AS description
        ORDER BY w.code
        LIMIT $limit
        """

        with driver.session(database=settings.neo4j_database) as session:
            result = run_cypher(session, query, limit=limit)
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
            result = run_cypher(session, query, limit=limit)
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
            result = run_cypher(session, query, limit=limit)
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
    def get_tablecontext_sync(cls, catalog: str, schema: str, table: str) -> TableContextDTO | None:
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
            result = run_cypher(session, query, catalog=catalog, schema=schema, table=table)
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
    def get_metriccontext_sync(cls, codes: list[str]) -> list[MetricContextDTO]:
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
            result = run_cypher(session, query, codes=codes)
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
    async def load_catalog_nav(cls) -> list[dict[str, Any]]:
        """
        加载 Catalog -> Schema 导航（禁止包含表明细）

        返回结构：
        [
          { "name": "catalog", "metalake": "ml", "schemas": [{ "name": "schema", "table_count": 123 }] }
        ]
        """
        cypher = """
        MATCH (cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)
        OPTIONAL MATCH (sch)-[:HAS_TABLE]->(t:Table:Knowledge)
        WITH cat, sch, count(t) AS table_count
        WITH cat, collect({
            name: sch.name,
            table_count: table_count
        }) AS schemas
        RETURN
            cat.name AS name,
            cat.metalake AS metalake,
            schemas
        ORDER BY cat.name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 Catalog/Schema 导航失败: {e}")
            return []

    @classmethod
    async def load_tag_nav(
        cls,
        *,
        limit_tags: int = 12,
        tables_per_tag: int = 8,
    ) -> list[dict[str, Any]]:
        """
        加载 tag 导航（基于 Table.tags 的自由标签）

        关键事实：
        - tag 是用户可自由维护的“标签”，不要求任何前缀规范（例如：ods、交易域）
        - 该接口用于 no-hit 场景下给用户做“我能帮你做什么”的引导（非指针、无 element_id）

        返回结构：
        [
          {
            "tag": "交易域",
            "table_count": 123,
            "schemas": ["ods", "dwd"],
            "sample_tables": [
              {"schema_name": "ods", "table_name": "t_order", "display_name": "订单", "description": "...", "tags": ["ods", "交易域"]}
            ]
          }
        ]
        """
        safe_limit_tags = max(1, min(int(limit_tags), 50))
        safe_tables_per_tag = max(1, min(int(tables_per_tag), 30))

        cypher = """
        MATCH (t:Table:Knowledge)
        WITH t, coalesce(t.tags, []) AS tags
        UNWIND tags AS tag
        WITH tag, t
        WHERE tag IS NOT NULL AND trim(toString(tag)) <> ''
        OPTIONAL MATCH (t)<-[:HAS_TABLE]-(sch:Schema:Knowledge)
        WITH tag, sch, t
        WITH
            tag,
            collect(DISTINCT sch.name) AS schemas,
            count(DISTINCT t) AS table_count,
            collect(DISTINCT {
                schema_name: sch.name,
                table_name: t.name,
                display_name: coalesce(t.displayName, t.name),
                description: coalesce(t.description, ''),
                tags: coalesce(t.tags, [])
            }) AS tables
        RETURN
            tag,
            table_count,
            [s IN schemas WHERE s IS NOT NULL] AS schemas,
            tables[0..$tables_per_tag] AS sample_tables
        ORDER BY table_count DESC, tag ASC
        LIMIT $limit_tags
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(
                    session,
                    cypher,
                    {"limit_tags": safe_limit_tags, "tables_per_tag": safe_tables_per_tag},
                )
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 tag 导航失败: {e}")
            return []

    @classmethod
    async def count_lineage_edges(cls) -> int:
        """统计表级血缘边数量（禁止返回边明细）"""
        cypher = """
        MATCH (source:Table:Knowledge)-[:INPUT_OF]->(sql:SQL:Knowledge)-[:OUTPUT_TO]->(target:Table:Knowledge)
        RETURN count(DISTINCT (source.id + '|' + sql.id + '|' + target.id)) AS edge_count
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher)
                record = await result.single()
                if not record:
                    return 0
                edge_count = record["edge_count"]
                return int(edge_count) if edge_count is not None else 0
        except Exception as e:
            logger.error(f"统计表级血缘边数量失败: {e}")
            return 0

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
                result = await arun_cypher(session, cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 Catalog 层级结构失败: {e}")
            return []

    @classmethod
    async def load_table_lineage(cls) -> list[dict[str, Any]]:
        """加载表级血缘图"""
        cypher = cls._TABLE_LINEAGE_CYPHER

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载表级血缘失败: {e}")
            return []

    _TABLE_LINEAGE_CYPHER = """
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

    @classmethod
    def load_lineage_sync(cls) -> list[dict[str, Any]]:
        """加载表级血缘图（同步方法）"""
        cypher = cls._TABLE_LINEAGE_CYPHER
        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher)
                records = result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载表级血缘失败(同步): {e}")
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
                result = await arun_cypher(session, cypher, limit=limit)
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
                result = await arun_cypher(session, cypher, params)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取表列详情失败: {e}")
            return []

    @classmethod
    async def get_column_domains(
        cls,
        column_element_id: str,
    ) -> dict[str, Any] | None:
        """
        根据 Column 的 elementId 精确获取其关联的 ValueDomain（同步列-值域关系）

        返回（不解析 items，仅返回原始字段）：
        - column_element_id, column_id, column_name, schema_name, table_name
        - value_domains: [{element_id, domain_code, domain_name, domain_type, domain_level, data_type, description, items}]
        """
        if not column_element_id:
            return None

        cypher = """
        MATCH (c:Column:Knowledge)
        WHERE elementId(c) = $column_eid
        OPTIONAL MATCH (t:Table:Knowledge)-[:HAS_COLUMN]->(c)
        OPTIONAL MATCH (s:Schema:Knowledge)-[:HAS_TABLE]->(t)
        OPTIONAL MATCH (c)-[:HAS_VALUE_DOMAIN]->(vd:ValueDomain:Knowledge)
        WITH c, t, s, collect(DISTINCT {
            element_id: elementId(vd),
            domain_code: vd.domainCode,
            domain_name: vd.domainName,
            domain_type: vd.domainType,
            domain_level: vd.domainLevel,
            data_type: vd.dataType,
            description: vd.description,
            items: vd.items
        }) AS value_domains
        RETURN
            elementId(c) AS column_element_id,
            c.id AS column_id,
            c.name AS column_name,
            s.name AS schema_name,
            t.name AS table_name,
            value_domains
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher, {"column_eid": column_element_id})
                record = await result.single()
                if not record:
                    return None
                data = convert_neo4j_types(record.data())
                value_domains = data.get("value_domains") or []
                data["value_domains"] = [
                    vd for vd in value_domains if isinstance(vd, dict) and vd.get("element_id")
                ]
                return data
        except Exception as e:
            logger.error(f"获取列值域失败: {e}")
            return None

    @classmethod
    async def get_column_lineage(cls, source_table: str, target_table: str) -> list[dict[str, Any]]:
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
                result = await arun_cypher(
                    session,
                    cypher_derives_from,
                    {
                        "source_name": source_name,
                        "source_schema": source_schema,
                        "target_name": target_name,
                        "target_schema": target_schema,
                    },
                )
                records = await result.data()

                has_valid_lineage = False
                for record in records:
                    mappings = record.get("column_mappings", [])
                    if mappings and any(m.get("source_column") for m in mappings):
                        has_valid_lineage = True
                        break

                if has_valid_lineage:
                    return [convert_neo4j_types(r) for r in records]

                logger.info(
                    f"未找到 DERIVES_FROM 关系，降级为同名列匹配: {source_table} → {target_table}"
                )
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
                result = await arun_cypher(
                    session,
                    cypher_same_name,
                    {
                        "source_name": source_name,
                        "source_schema": source_schema,
                        "target_name": target_name,
                        "target_schema": target_schema,
                    },
                )
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]

        except Exception as e:
            logger.error(f"获取列级血缘失败: {e}")
            return []

    @classmethod
    async def search_sql(cls, tables: list[str], limit: int = 5) -> list[dict[str, Any]]:
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
                result = await arun_cypher(session, cypher, {"tables": tables, "limit": limit})
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"搜索相关 SQL 失败: {e}")
            return []

    @classmethod
    async def find_sql(cls, source_tables: list[str], target_table: str) -> dict[str, Any] | None:
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
                result = await arun_cypher(session, cypher, params)
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
                result = await arun_cypher(session, cypher, {"table_name": table_name})
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表详情失败: {e}")
            return None

    @classmethod
    async def get_lineage_detail(
        cls, source_table: str, target_table: str
    ) -> dict[str, Any] | None:
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
                result = await arun_cypher(
                    session, cypher, {"source_table": source_table, "target_table": target_table}
                )
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表血缘详情失败: {e}")
            return None

    @classmethod
    async def search_tables(cls, table_ids: list[str]) -> list[dict[str, Any]]:
        """
        基于 Table.id 列表，获取表的轻量上下文（用于 search_assets）

        重要：兼容两种 ID：
        - table.id（节点属性 id，由 generate_id 生成的稳定 ID）
        - elementId(table)（Neo4j 内部 elementId）
        """
        cypher = """
        UNWIND $table_ids AS table_id
        MATCH (table:Table:Knowledge)
        WHERE table.id = table_id OR elementId(table) = table_id
        OPTIONAL MATCH (table)<-[:HAS_TABLE]-(sch:Schema:Knowledge)<-[:HAS_SCHEMA]-(cat:Catalog:Knowledge)
        OPTIONAL MATCH (table)-[:HAS_COLUMN]->(col:Column:Knowledge)
        WITH table, sch, cat, count(col) AS column_count
        RETURN
            table.id AS table_id,
            elementId(table) AS element_id,
            table.name AS table_name,
            coalesce(table.displayName, table.name) AS table_display_name,
            table.description AS table_description,
            column_count,
            sch.name AS schema_name,
            cat.name AS catalog_name,
            coalesce(table.tags, []) AS table_tags,
            head([t IN coalesce(sch.tags, []) WHERE t STARTS WITH 'layer:']) AS schema_layer_tag
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher, {"table_ids": table_ids})
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
                result = run_cypher(session, query, limit=limit)
                return [convert_neo4j_types(record.data()) for record in result]
        except Exception as e:
            logger.error(f"获取初始图数据失败: {e}")
            return []

    @staticmethod
    def get_graph(element_ids: list[str]) -> dict[str, list]:
        """
        基于 elementId(n) 批量扩展子图（同步方法）

        用途：
        - 前端高亮/搜索命中后，拉取命中节点及其一跳关系子图
        """
        if not element_ids:
            return {"nodes": [], "relationships": []}

        query = """
        UNWIND $element_ids AS eid
        MATCH (n) WHERE elementId(n) = eid
        OPTIONAL MATCH (n)-[r]-(m)
        WITH collect(DISTINCT n) + collect(DISTINCT m) AS all_nodes, collect(DISTINCT r) AS rels
        RETURN
            [n IN all_nodes WHERE n IS NOT NULL | {id: id(n), type: labels(n)[0], properties: properties(n)}] AS nodes,
            [r IN rels WHERE r IS NOT NULL | {id: id(r), start: id(startNode(r)), end: id(endNode(r)), type: type(r), properties: properties(r)}] AS relationships
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, query, {"element_ids": element_ids})
                record = result.single()
                if not record:
                    return {"nodes": [], "relationships": []}
                return convert_neo4j_types(record.data())
        except Exception as e:
            logger.error(f"扩展图数据失败: {e}")
            return {"nodes": [], "relationships": []}

    @classmethod
    def get_nodes_context(
        cls,
        element_ids: list[str],
    ) -> list[dict[str, Any]]:
        """
        批量获取 Knowledge 节点上下文（同步方法）

        设计目标：
        - 给 KnowledgeAgent 构造“指针（pointer）”使用：必须可定位、可再解析
        - 不返回列明细/大字段，只返回定位与导航信息

        返回字段（按节点类型尽可能填充）：
        - element_id, labels, primary_label, node_id, code, name, display_name, description, tags
        - catalog_name, schema_name, table_name, path, qualified_name
        """
        if not element_ids:
            return []

        cypher = """
        UNWIND $element_ids AS eid
        MATCH (n:Knowledge)
        WHERE elementId(n) = eid
        WITH n, labels(n) AS labels

        WITH
            n,
            labels,
            head([x IN labels WHERE x <> 'Knowledge']) AS primary_label,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | cat.name]) AS table_catalog,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | sch.name]) AS table_schema,
            head([(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | n.name]) AS table_name,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(n) | cat.name]) AS schema_catalog,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | cat.name]) AS column_catalog,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | sch.name]) AS column_schema,
            head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | t.name]) AS column_table

        RETURN
            elementId(n) AS element_id,
            labels AS labels,
            primary_label AS primary_label,
            coalesce(n.id, null) AS node_id,
            CASE
                WHEN 'ValueDomain' IN labels THEN coalesce(n.domainCode, null)
                ELSE coalesce(n.code, null)
            END AS code,
            CASE
                WHEN 'ValueDomain' IN labels THEN coalesce(n.domainName, n.domainCode, null)
                ELSE n.name
            END AS name,
            CASE
                WHEN 'ValueDomain' IN labels THEN coalesce(n.domainName, null)
                ELSE coalesce(n.displayName, null)
            END AS display_name,
            coalesce(n.description, null) AS description,
            coalesce(n.tags, []) AS tags,
            coalesce(table_catalog, schema_catalog, column_catalog, null) AS catalog_name,
            coalesce(table_schema, column_schema, null) AS schema_name,
            coalesce(table_name, column_table, null) AS table_name,
            CASE
                WHEN 'Catalog' IN labels THEN n.name
                WHEN 'Schema' IN labels THEN coalesce(schema_catalog, '') + '.' + n.name
                WHEN 'Table' IN labels THEN coalesce(table_catalog, '') + '.' + coalesce(table_schema, '') + '.' + n.name
                WHEN 'Column' IN labels THEN coalesce(column_catalog, '') + '.' + coalesce(column_schema, '') + '.' + coalesce(column_table, '') + '.' + n.name
                WHEN 'ValueDomain' IN labels THEN 'valuedomain.' + coalesce(n.domainCode, '')
                ELSE coalesce(n.code, n.name)
            END AS path,
            CASE
                WHEN 'Schema' IN labels THEN coalesce(schema_catalog, '') + '.' + n.name
                WHEN 'Table' IN labels THEN coalesce(table_schema, '') + '.' + n.name
                WHEN 'Column' IN labels THEN coalesce(column_schema, '') + '.' + coalesce(column_table, '') + '.' + n.name
                WHEN 'ValueDomain' IN labels THEN 'valuedomain.' + coalesce(n.domainCode, '')
                ELSE coalesce(n.code, n.name)
            END AS qualified_name
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, {"element_ids": element_ids})
                records = result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"批量获取 Knowledge 节点上下文失败: {e}")
            return []

    # 精确解析（Identifier Resolver） - 同步方法（统一入口）
    # -------------------------------------------------------------------------

    @staticmethod
    def _schema_spec(
        *, parts: list[str], raw_ref: str, limit_int: int
    ) -> tuple[str, dict[str, Any]]:
        if len(parts) >= 2:
            catalog_name, schema_name = parts[0], parts[1]
            return (
                """
                MATCH (c:Catalog:Knowledge {name: $catalog})-[:HAS_SCHEMA]->(s:Schema:Knowledge {name: $schema})
                RETURN elementId(s) AS element_id
                LIMIT $limit
                """,
                {"catalog": catalog_name, "schema": schema_name, "limit": limit_int},
            )

        schema_name = parts[0] if parts else raw_ref
        return (
            """
            MATCH (s:Schema:Knowledge {name: $schema})
            RETURN elementId(s) AS element_id
            LIMIT $limit
            """,
            {"schema": schema_name, "limit": limit_int},
        )

    @staticmethod
    def _table_spec(
        *, parts: list[str], raw_ref: str, limit_int: int
    ) -> tuple[str, dict[str, Any]]:
        if len(parts) >= 3:
            catalog_name, schema_name, table_name = parts[0], parts[1], parts[2]
            return (
                """
                MATCH (c:Catalog:Knowledge {name: $catalog})-[:HAS_SCHEMA]->(s:Schema:Knowledge {name: $schema})
                      -[:HAS_TABLE]->(t:Table:Knowledge {name: $table})
                RETURN elementId(t) AS element_id
                LIMIT $limit
                """,
                {
                    "catalog": catalog_name,
                    "schema": schema_name,
                    "table": table_name,
                    "limit": limit_int,
                },
            )

        if len(parts) == 2:
            schema_name, table_name = parts[0], parts[1]
            return (
                """
                MATCH (s:Schema:Knowledge {name: $schema})-[:HAS_TABLE]->(t:Table:Knowledge {name: $table})
                RETURN elementId(t) AS element_id
                LIMIT $limit
                """,
                {"schema": schema_name, "table": table_name, "limit": limit_int},
            )

        table_name = parts[0] if parts else raw_ref
        return (
            """
            MATCH (t:Table:Knowledge {name: $table})
            RETURN elementId(t) AS element_id
            LIMIT $limit
            """,
            {"table": table_name, "limit": limit_int},
        )

    @staticmethod
    def _column_spec(*, parts: list[str], limit_int: int) -> tuple[str, dict[str, Any]] | None:
        if len(parts) >= 4:
            catalog_name, schema_name, table_name, column_name = (
                parts[0],
                parts[1],
                parts[2],
                parts[3],
            )
            return (
                """
                MATCH (c:Catalog:Knowledge {name: $catalog})-[:HAS_SCHEMA]->(s:Schema:Knowledge {name: $schema})
                      -[:HAS_TABLE]->(t:Table:Knowledge {name: $table})-[:HAS_COLUMN]->(col:Column:Knowledge {name: $column})
                RETURN elementId(col) AS element_id
                LIMIT $limit
                """,
                {
                    "catalog": catalog_name,
                    "schema": schema_name,
                    "table": table_name,
                    "column": column_name,
                    "limit": limit_int,
                },
            )

        if len(parts) == 3:
            schema_name, table_name, column_name = parts[0], parts[1], parts[2]
            return (
                """
                MATCH (s:Schema:Knowledge {name: $schema})-[:HAS_TABLE]->(t:Table:Knowledge {name: $table})
                      -[:HAS_COLUMN]->(col:Column:Knowledge {name: $column})
                RETURN elementId(col) AS element_id
                LIMIT $limit
                """,
                {
                    "schema": schema_name,
                    "table": table_name,
                    "column": column_name,
                    "limit": limit_int,
                },
            )

        return None

    @classmethod
    def _resolve_spec(
        cls, *, kind_norm: str, raw_ref: str, parts: list[str], limit_int: int
    ) -> tuple[str, dict[str, Any]] | None:
        simple_specs: dict[str, tuple[str, dict[str, Any]]] = {
            "element_id": (
                """
                MATCH (n:Knowledge)
                WHERE elementId(n) = $eid
                RETURN elementId(n) AS element_id
                """,
                {"eid": raw_ref},
            ),
            "node_id": (
                """
                MATCH (n:Knowledge {id: $id})
                RETURN elementId(n) AS element_id
                LIMIT $limit
                """,
                {"id": raw_ref, "limit": limit_int},
            ),
            "catalog": (
                """
                MATCH (c:Catalog:Knowledge {name: $name})
                RETURN elementId(c) AS element_id
                LIMIT $limit
                """,
                {"name": (parts[0] if parts else raw_ref), "limit": limit_int},
            ),
            "valuedomain": (
                """
                MATCH (v:ValueDomain:Knowledge {domainCode: $code})
                RETURN elementId(v) AS element_id
                LIMIT $limit
                """,
                {"code": raw_ref.removeprefix("valuedomain.").strip(), "limit": limit_int},
            ),
            "metric": (
                """
                MATCH (m:Knowledge)
                WHERE (m:AtomicMetric OR m:DerivedMetric OR m:CompositeMetric)
                  AND m.code = $code
                RETURN elementId(m) AS element_id
                LIMIT $limit
                """,
                {"code": raw_ref.removeprefix("metric.").strip(), "limit": limit_int},
            ),
        }
        if kind_norm in simple_specs:
            return simple_specs[kind_norm]

        if kind_norm == "schema":
            return cls._schema_spec(parts=parts, raw_ref=raw_ref, limit_int=limit_int)

        if kind_norm == "table":
            return cls._table_spec(parts=parts, raw_ref=raw_ref, limit_int=limit_int)

        if kind_norm == "column":
            return cls._column_spec(parts=parts, limit_int=limit_int)

        if kind_norm in {"wordroot", "modifier", "unit"}:
            label = {"wordroot": "WordRoot", "modifier": "Modifier", "unit": "Unit"}[kind_norm]
            return (
                f"""
                MATCH (n:{label}:Knowledge {{code: $code}})
                RETURN elementId(n) AS element_id
                LIMIT $limit
                """,
                {"code": raw_ref, "limit": limit_int},
            )

        return None

    @classmethod
    def resolve_element_ids(
        cls,
        *,
        kind: str,
        ref: str,
        limit: int = 20,
    ) -> list[str]:
        """
        统一精确解析：将“确定性标识符”解析为 Knowledge 节点 elementId 列表（同步方法）

        【弃用提示（重要）】
        - 该方法只适用于“确定性标识符”（例如 element_id / node_id / catalog.schema.table / schema.table.column / metric.code）
        - 不要用于解析用户自由文本，更不要让 KnowledgeAgent 通过“从句子里抠 schema.table”来短路语义检索链路
        - 正确链路应为：语义检索召回候选 element_id → 再用 element_id 精确查询 Neo4j → 产出 ETLPointer

        约束：
        - 只做精确定位，不走向量/全文，不做语义猜测
        - 允许返回多个候选（歧义），由上层（KnowledgeAgent/前端）要求用户澄清

        kind 支持（大小写不敏感）：
        - catalog: catalog
        - schema: catalog.schema 或 schema
        - table: catalog.schema.table 或 schema.table 或 table
        - column: catalog.schema.table.column 或 schema.table.column
        - valuedomain: valuedomain.<domainCode> 或 <domainCode>
        - metric: metric.<code> 或 <code>
        - wordroot/modifier/unit: <code>
        - node_id: <n.id>
        - element_id: <elementId(n)>
        """
        kind_norm = (kind or "").strip().lower()
        raw_ref = (ref or "").strip()
        if not kind_norm or not raw_ref:
            return []

        parts = [p for p in raw_ref.split(".") if p]
        driver = Neo4jClient.get_driver()

        def run(cypher: str, params: dict[str, Any]) -> list[str]:
            try:
                with driver.session(database=settings.neo4j_database) as session:
                    result = run_cypher(session, cypher, params)
                    records = result.data()
                    return [r.get("element_id") for r in records if r.get("element_id")]
            except Exception as e:
                logger.error(f"统一精确解析失败(kind={kind_norm}): {e}")
                return []

        limit_int = int(limit)
        spec = cls._resolve_spec(
            kind_norm=kind_norm,
            raw_ref=raw_ref,
            parts=parts,
            limit_int=limit_int,
        )
        if spec is None:
            return []

        cypher, params = spec
        return run(cypher, params)
