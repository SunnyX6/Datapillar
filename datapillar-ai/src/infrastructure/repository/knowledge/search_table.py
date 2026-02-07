# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 表查询服务

职责：提供表相关的查询功能（含列、值域、血缘）
- get_table_info: 获取表基本信息
- get_table_detail: 获取表详情（含列和值域）
- get_table_lineage: 获取表血缘关系
- get_column_lineage: 获取列级血缘
- find_lineage_sql: 根据血缘查找 SQL
- search_tables: 混合搜索表（向量 + 全文）
"""

from __future__ import annotations

import logging
import time
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.infrastructure.database.cypher import run_cypher
from src.infrastructure.database.neo4j import convert_neo4j_types
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jTableSearch:
    """Neo4j 表查询服务"""

    # Table 混合检索返回格式（HybridCypherRetriever）
    _TABLE_RETRIEVAL_QUERY = """
    OPTIONAL MATCH (s:Schema)-[:HAS_TABLE]->(node)
    OPTIONAL MATCH (c:Catalog)-[:HAS_SCHEMA]->(s)
    RETURN
        node.id AS node_id,
        'Table' AS type,
        CASE
            WHEN c IS NULL OR s IS NULL THEN node.name
            ELSE c.name + '.' + s.name + '.' + node.name
        END AS path,
        node.name AS name,
        coalesce(node.description, '') AS description,
        null AS dataType,
        null AS table,
        score
    """

    # =========================================================================
    # 精确查询
    # =========================================================================

    @classmethod
    def list_catalogs(cls, limit: int = 50) -> list[dict[str, Any]]:
        """列出 Catalog 列表（导航用途）"""
        safe_limit = min(max(int(limit), 1), 2000)
        cypher = """
        MATCH (c:Catalog)
        RETURN
            c.id AS node_id,
            c.name AS name,
            coalesce(c.description, '') AS description
        ORDER BY name
        LIMIT $limit
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, {"limit": safe_limit})
                return [convert_neo4j_types(r) for r in (result.data() or [])]
        except Exception as e:
            logger.error(f"列出 Catalog 失败: {e}")
            return []

    @classmethod
    def list_schemas(cls, catalog: str, limit: int = 200) -> list[dict[str, Any]]:
        """列出指定 Catalog 下的 Schema 列表（导航用途）"""
        if not (isinstance(catalog, str) and catalog.strip()):
            return []

        safe_limit = min(max(int(limit), 1), 2000)
        cypher = """
        MATCH (c:Catalog {name: $catalog})-[:HAS_SCHEMA]->(s:Schema)
        RETURN
            s.id AS node_id,
            s.name AS name,
            coalesce(s.description, '') AS description
        ORDER BY name
        LIMIT $limit
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session, cypher, {"catalog": catalog.strip(), "limit": safe_limit}
                )
                return [convert_neo4j_types(r) for r in (result.data() or [])]
        except Exception as e:
            logger.error(f"列出 Schema 失败: {e}")
            return []

    @classmethod
    def list_tables(
        cls,
        catalog: str,
        schema: str,
        keyword: str | None = None,
        cursor: str | None = None,
        limit: int = 200,
    ) -> list[dict[str, Any]]:
        """列出指定 Catalog + Schema 下的 Table 列表（导航用途，支持 cursor 翻页）"""
        if not (isinstance(catalog, str) and catalog.strip()):
            return []
        if not (isinstance(schema, str) and schema.strip()):
            return []

        safe_limit = min(max(int(limit), 1), 2000)
        kw = (keyword or "").strip()

        cur = (cursor or "").strip()

        cypher = """
        MATCH (c:Catalog {name: $catalog})-[:HAS_SCHEMA]->(s:Schema {name: $schema})-[:HAS_TABLE]->(t:Table)
        WHERE ($keyword = '' OR toLower(t.name) CONTAINS toLower($keyword))
          AND ($cursor = '' OR t.name > $cursor)
        RETURN
            t.id AS node_id,
            t.name AS name,
            coalesce(t.description, '') AS description
        ORDER BY name
        LIMIT $page_size
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session,
                    cypher,
                    {
                        "catalog": catalog.strip(),
                        "schema": schema.strip(),
                        "keyword": kw,
                        "cursor": cur,
                        "page_size": safe_limit + 1,
                    },
                )
                rows = [convert_neo4j_types(r) for r in (result.data() or [])]
                items = rows[:safe_limit]
                has_more = len(rows) > safe_limit
                next_cursor = None
                if has_more and items:
                    next_cursor = str(items[-1].get("name") or "").strip() or None
                return [
                    {
                        **item,
                        "has_more": has_more,
                        "next_cursor": next_cursor,
                    }
                    for item in items
                ]
        except Exception as e:
            logger.error(f"列出 Table 失败: {e}")
            return []

    @classmethod
    def get_table_info(
        cls,
        catalog: str,
        schema: str,
        table: str,
    ) -> dict[str, Any] | None:
        """
        获取表基本信息

        参数：
        - catalog: Catalog 名称
        - schema: Schema 名称
        - table: 表名

        返回：
        - {catalog, schema, table, description, column_count}
        """
        cypher = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        WITH t, count(col) AS column_count
        RETURN
            t.name AS name,
            coalesce(t.description, '') AS description,
            column_count
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session, cypher, {"catalog": catalog, "schema": schema, "table": table}
                )
                record = result.single()

                if not record:
                    logger.debug(f"未找到表: {catalog}.{schema}.{table}")
                    return None

                return {
                    "catalog": catalog,
                    "schema": schema,
                    "table": record["name"],
                    "description": record["description"],
                    "column_count": record["column_count"],
                }
        except Exception as e:
            logger.error(f"获取表基本信息失败: {e}")
            return None

    @classmethod
    def get_table_detail(
        cls,
        catalog: str,
        schema: str,
        table: str,
    ) -> dict[str, Any] | None:
        """
        获取表详情（含列和值域）

        参数：
        - catalog: Catalog 名称
        - schema: Schema 名称
        - table: 表名

        返回：
        - {catalog, schema, table, description, columns: [{name, dataType, description, valueDomain}]}
        """
        cypher = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (col)-[:HAS_VALUE_DOMAIN]->(vd:ValueDomain)
        WITH t, col, vd
        ORDER BY col.name
        WITH t, collect({
            name: col.name,
            dataType: coalesce(col.dataType, ''),
            description: coalesce(col.description, ''),
            nullable: col.nullable,
            valueDomain: CASE WHEN vd IS NOT NULL THEN {
                code: vd.domainCode,
                name: coalesce(vd.domainName, vd.domainCode),
                type: vd.domainType,
                items: vd.items
            } ELSE null END
        }) AS columns
        RETURN
            t.name AS name,
            coalesce(t.description, '') AS description,
            columns
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session, cypher, {"catalog": catalog, "schema": schema, "table": table}
                )
                record = result.single()

                if not record:
                    logger.debug(f"未找到表: {catalog}.{schema}.{table}")
                    return None

                return {
                    "catalog": catalog,
                    "schema": schema,
                    "table": record["name"],
                    "description": record["description"],
                    "columns": record["columns"],
                }
        except Exception as e:
            logger.error(f"获取表详情失败: {e}")
            return None

    # =========================================================================
    # 血缘查询
    # =========================================================================

    @classmethod
    def get_table_lineage(
        cls,
        schema: str,
        table: str,
        direction: str = "both",
    ) -> dict[str, Any]:
        """
        获取表血缘关系

        参数：
        - schema: Schema 名称
        - table: 表名
        - direction: 方向（upstream/downstream/both）

        返回：
        - {upstream: [], downstream: [], edges: []}
        """
        qualified_name = f"{schema}.{table}"

        cypher = """
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target:Table)
        WITH source, target, sql
        MATCH (source)<-[:HAS_TABLE]-(source_schema:Schema)
        MATCH (target)<-[:HAS_TABLE]-(target_schema:Schema)
        RETURN DISTINCT
            source_schema.name + '.' + source.name AS source_table,
            target_schema.name + '.' + target.name AS target_table,
            sql.id AS sql_id
        ORDER BY source_table, target_table
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher)
                records = result.data()

                upstream = []
                downstream = []
                edges = []

                for edge in records:
                    source = edge.get("source_table", "")
                    target = edge.get("target_table", "")

                    is_source = source == qualified_name or source.endswith(f".{table}")
                    is_target = target == qualified_name or target.endswith(f".{table}")

                    if direction in ("upstream", "both") and is_target:
                        upstream.append(source)
                        edges.append(edge)

                    if direction in ("downstream", "both") and is_source:
                        downstream.append(target)
                        if not (direction == "both" and is_target):
                            edges.append(edge)

                return {
                    "upstream": list(set(upstream)),
                    "downstream": list(set(downstream)),
                    "edges": edges,
                }
        except Exception as e:
            logger.error(f"获取表血缘失败: {e}")
            return {"upstream": [], "downstream": [], "edges": []}

    @classmethod
    def get_column_lineage(
        cls,
        source_table: str,
        target_table: str,
    ) -> list[dict[str, Any]]:
        """
        获取列级血缘

        参数：
        - source_table: 源表（schema.table 格式）
        - target_table: 目标表（schema.table 格式）

        返回：
        - [{sql_id, sql_content, column_mappings}]
        """
        source_parts = source_table.split(".", 1)
        target_parts = target_table.split(".", 1)

        if len(source_parts) < 2 or len(target_parts) < 2:
            logger.warning("列级血缘查询需要 schema.table 格式的表名")
            return []

        source_schema, source_name = source_parts
        target_schema, target_name = target_parts

        cypher = """
        MATCH (source:Table {name: $source_name})<-[:HAS_TABLE]-(src_sch:Schema {name: $source_schema})
        MATCH (target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})
        MATCH (source)-[:HAS_COLUMN]->(src_col:Column)<-[lineage:DERIVES_FROM]-(tgt_col:Column)<-[:HAS_COLUMN]-(target)
        OPTIONAL MATCH (source)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
        RETURN
            sql.id AS sql_id,
            sql.content AS sql_content,
            collect(DISTINCT {
                source_column: src_col.name,
                target_column: tgt_col.name,
                transformation: coalesce(lineage.transformationType, 'direct')
            }) AS column_mappings
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session,
                    cypher,
                    {
                        "source_name": source_name,
                        "source_schema": source_schema,
                        "target_name": target_name,
                        "target_schema": target_schema,
                    },
                )
                records = result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取列级血缘失败: {e}")
            return []

    @classmethod
    def find_lineage_sql(
        cls,
        source_tables: list[str],
        target_table: str,
    ) -> dict[str, Any] | None:
        """
        根据血缘关系精准查找 SQL

        参数：
        - source_tables: 源表列表
        - target_table: 目标表

        返回：
        - {sql_id, name, content, summary, engine, source_tables, target_table}
        """
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
        WITH sql, target, collect(DISTINCT source.name) AS source_tables
        RETURN
            sql.id AS sql_id,
            sql.name AS name,
            sql.content AS content,
            sql.summary AS summary,
            sql.engine AS engine,
            source_tables,
            target.name AS target_table
        ORDER BY coalesce(sql.confidence, 0.5) DESC, coalesce(sql.useCount, 0) DESC
        LIMIT 1
        """

        params: dict[str, Any] = {"source_names": source_names, "target_name": target_name}
        if target_schema:
            params["target_schema"] = target_schema

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, params)
                record = result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"根据血缘查找 SQL 失败: {e}")
            return None

    # =========================================================================
    # 混合搜索（表）
    # =========================================================================

    @classmethod
    def search_tables(
        cls,
        query: str,
        top_k: int = 3,
        min_score: float = 0.55,
        tenant_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        混合搜索表（向量 + 全文）

        参数：
        - query: 搜索文本
        - top_k: 返回数量上限
        - min_score: 最小相似度阈值

        返回：
        - [{type, path, name, description, dataType, table, score}]
        """
        from neo4j_graphrag.retrievers import HybridCypherRetriever
        from neo4j_graphrag.types import HybridSearchRanker, RetrieverResultItem

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        def table_result_formatter(record: Any) -> Any:
            return RetrieverResultItem(
                content=f"{record.get('name')} {record.get('description') or ''}",
                metadata={
                    "type": record.get("type"),
                    "path": record.get("path"),
                    "name": record.get("name"),
                    "description": record.get("description"),
                    "dataType": record.get("dataType"),
                    "table": record.get("table"),
                    "score": record.get("score"),
                },
            )

        try:
            retriever = HybridCypherRetriever(
                driver=Neo4jClient.get_driver(),
                vector_index_name="table_embedding",
                fulltext_index_name="table_fulltext",
                retrieval_query=cls._TABLE_RETRIEVAL_QUERY,
                result_formatter=table_result_formatter,
                embedder=UnifiedEmbedder(tenant_id),
                neo4j_database=settings.neo4j_database,
            )

            results = retriever.search(
                query_text=query,
                top_k=top_k,
                ranker=HybridSearchRanker.LINEAR,
                alpha=0.6,
            )
            items = list(getattr(results, "items", []) or [])

            recommendations: list[dict[str, Any]] = []
            for item in items:
                metadata = getattr(item, "metadata", {}) or {}
                score = float(metadata.get("score", 0) or 0)
                if score < min_score:
                    continue
                recommendations.append(
                    {
                        "type": metadata.get("type"),
                        "path": metadata.get("path"),
                        "name": metadata.get("name"),
                        "description": metadata.get("description"),
                        "dataType": metadata.get("dataType"),
                        "table": metadata.get("table"),
                        "score": round(score, 3),
                    }
                )

            logger.debug(f"[表搜索] 总耗时: {time.time() - start:.3f}s")
            return recommendations
        except Exception as e:
            logger.error(f"混合搜索表失败: {e}")
            return []
