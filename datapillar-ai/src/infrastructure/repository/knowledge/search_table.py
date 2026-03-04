# @author Sunny
# @date 2026-01-27

"""
Neo4j Table query service

Responsibilities:Provide table-related query functions(Contains columns,range,Bloodline)
- get_table_info:Get basic table information
- get_table_detail:Get table details(Contains columns and ranges)
- get_table_lineage:Get table blood relationship
- get_column_lineage:Get rank lineage
- find_lineage_sql:Search based on ancestry SQL
- search_tables:hybrid search table(Vector + Full text)
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
    """Neo4j Table query service"""

    _SYSTEM_CREATORS = ["OPENLINEAGE", "GRAVITINO_SYNC", "system", "SYSTEM"]

    # Table Mixed search return format(HybridCypherRetriever)
    _TABLE_RETRIEVAL_QUERY = """
    OPTIONAL MATCH (s:Schema)-[:HAS_TABLE]->(node)
    OPTIONAL MATCH (c:Catalog)-[:HAS_SCHEMA]->(s)
    WHERE ($tenantId IS NULL OR coalesce(node.tenantId, s.tenantId, c.tenantId) = $tenantId)
      AND (
        $userId IS NULL
        OR node.createdBy IS NULL
        OR toString(node.createdBy) = toString($userId)
        OR node.createdBy IN $systemCreators
      )
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
    # Precise query
    # =========================================================================

    @classmethod
    def list_catalogs(
        cls,
        limit: int = 50,
        *,
        tenant_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """list Catalog list(Navigation purposes)"""
        safe_limit = min(max(int(limit), 1), 2000)
        cypher = """
        MATCH (c:Catalog)
        WHERE ($tenantId IS NULL OR c.tenantId = $tenantId)
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
                result = run_cypher(
                    session,
                    cypher,
                    {"limit": safe_limit, "tenantId": tenant_id},
                )
                return [convert_neo4j_types(r) for r in (result.data() or [])]
        except Exception as e:
            logger.error(f"list Catalog failed:{e}")
            return []

    @classmethod
    def list_schemas(
        cls,
        catalog: str,
        limit: int = 200,
        *,
        tenant_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """list specified Catalog down Schema list(Navigation purposes)"""
        if not (isinstance(catalog, str) and catalog.strip()):
            return []

        safe_limit = min(max(int(limit), 1), 2000)
        cypher = """
        MATCH (c:Catalog {name: $catalog})-[:HAS_SCHEMA]->(s:Schema)
        WHERE ($tenantId IS NULL OR (c.tenantId = $tenantId AND s.tenantId = $tenantId))
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
                    session,
                    cypher,
                    {
                        "catalog": catalog.strip(),
                        "limit": safe_limit,
                        "tenantId": tenant_id,
                    },
                )
                return [convert_neo4j_types(r) for r in (result.data() or [])]
        except Exception as e:
            logger.error(f"list Schema failed:{e}")
            return []

    @classmethod
    def list_tables(
        cls,
        catalog: str,
        schema: str,
        keyword: str | None = None,
        cursor: str | None = None,
        limit: int = 200,
        *,
        tenant_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """list specified Catalog + Schema down Table list(Navigation purposes,support cursor Turn page)"""
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
          AND ($tenantId IS NULL OR (c.tenantId = $tenantId AND s.tenantId = $tenantId AND t.tenantId = $tenantId))
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
                        "tenantId": tenant_id,
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
            logger.error(f"list Table failed:{e}")
            return []

    @classmethod
    def get_table_info(
        cls,
        catalog: str,
        schema: str,
        table: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> dict[str, Any] | None:
        """
        Get basic table information

        parameters:- catalog:Catalog Name
        - schema:Schema Name
        - table:table name

        Return:- {catalog,schema,table,description,column_count}
        """
        cypher = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        WHERE ($tenantId IS NULL OR (cat.tenantId = $tenantId AND sch.tenantId = $tenantId AND t.tenantId = $tenantId))
          AND (
            $userId IS NULL
            OR t.createdBy IS NULL
            OR toString(t.createdBy) = toString($userId)
            OR t.createdBy IN $systemCreators
          )
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
                    session,
                    cypher,
                    {
                        "catalog": catalog,
                        "schema": schema,
                        "table": table,
                        "tenantId": tenant_id,
                        "userId": user_id,
                        "systemCreators": cls._SYSTEM_CREATORS,
                    },
                )
                record = result.single()

                if not record:
                    logger.debug(f"table not found:{catalog}.{schema}.{table}")
                    return None

                return {
                    "catalog": catalog,
                    "schema": schema,
                    "table": record["name"],
                    "description": record["description"],
                    "column_count": record["column_count"],
                }
        except Exception as e:
            logger.error(f"Failed to obtain basic table information:{e}")
            return None

    @classmethod
    def get_table_detail(
        cls,
        catalog: str,
        schema: str,
        table: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> dict[str, Any] | None:
        """
        Get table details(Contains columns and ranges)

        parameters:- catalog:Catalog Name
        - schema:Schema Name
        - table:table name

        Return:- {catalog,schema,table,description,columns:[{name,dataType,description,valueDomain}]}
        """
        cypher = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        WHERE ($tenantId IS NULL OR (cat.tenantId = $tenantId AND sch.tenantId = $tenantId AND t.tenantId = $tenantId))
          AND (
            $userId IS NULL
            OR t.createdBy IS NULL
            OR toString(t.createdBy) = toString($userId)
            OR t.createdBy IN $systemCreators
          )
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
                    session,
                    cypher,
                    {
                        "catalog": catalog,
                        "schema": schema,
                        "table": table,
                        "tenantId": tenant_id,
                        "userId": user_id,
                        "systemCreators": cls._SYSTEM_CREATORS,
                    },
                )
                record = result.single()

                if not record:
                    logger.debug(f"table not found:{catalog}.{schema}.{table}")
                    return None

                return {
                    "catalog": catalog,
                    "schema": schema,
                    "table": record["name"],
                    "description": record["description"],
                    "columns": record["columns"],
                }
        except Exception as e:
            logger.error(f"Failed to get table details:{e}")
            return None

    # =========================================================================
    # Bloodline query
    # =========================================================================

    @classmethod
    def get_table_lineage(
        cls,
        schema: str,
        table: str,
        direction: str = "both",
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> dict[str, Any]:
        """
        Get table blood relationship

        parameters:- schema:Schema Name
        - table:table name
        - direction:direction(upstream/downstream/both)

        Return:- {upstream:[],downstream:[],edges:[]}
        """
        qualified_name = f"{schema}.{table}"

        cypher = """
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target:Table)
        WHERE ($tenantId IS NULL OR (source.tenantId = $tenantId AND sql.tenantId = $tenantId AND target.tenantId = $tenantId))
          AND (
            $userId IS NULL
            OR sql.createdBy IS NULL
            OR toString(sql.createdBy) = toString($userId)
            OR sql.createdBy IN $systemCreators
          )
        WITH source, target, sql
        MATCH (source)<-[:HAS_TABLE]-(source_schema:Schema)
        MATCH (target)<-[:HAS_TABLE]-(target_schema:Schema)
        WHERE ($tenantId IS NULL OR (source_schema.tenantId = $tenantId AND target_schema.tenantId = $tenantId))
        RETURN DISTINCT
            source_schema.name + '.' + source.name AS source_table,
            target_schema.name + '.' + target.name AS target_table,
            sql.id AS sql_id
        ORDER BY source_table, target_table
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session,
                    cypher,
                    {
                        "tenantId": tenant_id,
                        "userId": user_id,
                        "systemCreators": cls._SYSTEM_CREATORS,
                    },
                )
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
            logger.error(f"Failed to obtain table lineage:{e}")
            return {"upstream": [], "downstream": [], "edges": []}

    @classmethod
    def get_column_lineage(
        cls,
        source_table: str,
        target_table: str,
    ) -> list[dict[str, Any]]:
        """
        Get rank lineage

        parameters:- source_table:source table(schema.table Format)
        - target_table:target table(schema.table Format)

        Return:- [{sql_id,sql_content,column_mappings}]
        """
        source_parts = source_table.split(".", 1)
        target_parts = target_table.split(".", 1)

        if len(source_parts) < 2 or len(target_parts) < 2:
            logger.warning(
                "Required for column-level ancestry query schema.table Format table name"
            )
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
            logger.error(f"Failed to obtain rank bloodline:{e}")
            return []

    @classmethod
    def find_lineage_sql(
        cls,
        source_tables: list[str],
        target_table: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> dict[str, Any] | None:
        """
        Accurate search based on blood relationship SQL

        parameters:- source_tables:Source table list
        - target_table:target table

        Return:- {sql_id,name,content,summary,engine,source_tables,target_table}
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
          AND ($tenantId IS NULL OR (source.tenantId = $tenantId AND target.tenantId = $tenantId AND sql.tenantId = $tenantId))
          AND (
            $userId IS NULL
            OR sql.createdBy IS NULL
            OR toString(sql.createdBy) = toString($userId)
            OR sql.createdBy IN $systemCreators
          )
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

        params: dict[str, Any] = {
            "source_names": source_names,
            "target_name": target_name,
            "tenantId": tenant_id,
            "userId": user_id,
            "systemCreators": cls._SYSTEM_CREATORS,
        }
        if target_schema:
            params["target_schema"] = target_schema

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, params)
                record = result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"Search based on ancestry SQL failed:{e}")
            return None

    # =========================================================================
    # hybrid search(table)
    # =========================================================================

    @classmethod
    def search_tables(
        cls,
        query: str,
        top_k: int = 3,
        min_score: float = 0.55,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        hybrid search table(Vector + Full text)

        parameters:- query:Search text
        - top_k:Return maximum quantity
        - min_score:Minimum similarity threshold

        Return:- [{type,path,name,description,dataType,table,score}]
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
                query_params={
                    "tenantId": tenant_id,
                    "userId": user_id,
                    "systemCreators": cls._SYSTEM_CREATORS,
                },
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

            logger.debug(f"[table search] Total time spent:{time.time() - start:.3f}s")
            return recommendations
        except Exception as e:
            logger.error(f"Mixed search table failed:{e}")
            return []
