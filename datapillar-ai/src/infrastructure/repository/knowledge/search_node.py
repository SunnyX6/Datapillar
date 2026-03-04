# @author Sunny
# @date 2026-01-27

"""
Neo4j Node search service

Responsibilities:to knowledge base agent Search node,Returns a list of matching nodes
design principles:- use neo4j-graphrag Official library(HybridCypherRetriever / VectorCypherRetriever)
- Unified use of services ID(node.id)
- Unified return structure SearchHit
- Pass retrieval_query One query returns all fields,No secondary query
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

from src.infrastructure.database import Neo4jClient
from src.infrastructure.database.cypher import run_cypher
from src.infrastructure.database.neo4j import convert_neo4j_types
from src.shared.config.settings import settings

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


@dataclass
class SearchHit:
    """Node search hits(metadata pointer)"""

    node_id: str  # Business ID(Nodal id Properties)
    score: float  # relevance score
    labels: list[str]  # node label,Such as ["Knowledge","Table"]
    name: str | None  # Node name
    description: str | None  # Node description


class Neo4jNodeSearch:
    """Knowledge"""

    # Default index name
    DEFAULT_VECTOR_INDEX = "kg_unified_vector_index"
    DEFAULT_FULLTEXT_INDEX = "kg_unified_fulltext_index"

    # unified retrieval_query:One query returns all required fields
    # use COALESCE Handle field differences between different node types:# - Table/Column/Schema Wait:use name,description
    #   - SQL node:use summary as name,summary as description
    #   - ValueDomain:use code or name
    #   Exclude specified types
    RETRIEVAL_QUERY = """
    RETURN node.id AS node_id,
           labels(node) AS labels,
           COALESCE(node.name, node.code, node.summary) AS name,
           COALESCE(node.description, node.summary) AS description,
           score
    """

    @classmethod
    def get_knowledge_navigation(cls) -> dict[str, int] | None:
        """kg_unified_fulltext_index"""
        cypher = """
        OPTIONAL MATCH (cat:Catalog)
        WITH count(cat) AS catalogs
        OPTIONAL MATCH (sch:Schema)
        WITH catalogs, count(sch) AS schemas
        OPTIONAL MATCH (tbl:Table)
        WITH catalogs, schemas, count(tbl) AS tables
        OPTIONAL MATCH (col:Column)
        WITH catalogs, schemas, tables, count(col) AS columns
        OPTIONAL MATCH (metric)
        WHERE metric:AtomicMetric OR metric:DerivedMetric OR metric:CompositeMetric
        WITH catalogs, schemas, tables, columns, count(metric) AS metrics
        OPTIONAL MATCH (sql:SQL)
        WITH catalogs, schemas, tables, columns, metrics, count(sql) AS sql
        OPTIONAL MATCH (tag:Tag)
        WITH catalogs, schemas, tables, columns, metrics, sql, count(tag) AS tags
        OPTIONAL MATCH (vd:ValueDomain)
        RETURN catalogs, schemas, tables, columns, metrics, sql, tags, count(vd) AS value_domains
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher)
                record = result.single()
                if not record:
                    return {
                        "catalogs": 0,
                        "schemas": 0,
                        "tables": 0,
                        "columns": 0,
                        "metrics": 0,
                        "sql": 0,
                        "tags": 0,
                        "value_domains": 0,
                    }
                return {
                    "catalogs": int(record.get("catalogs") or 0),
                    "schemas": int(record.get("schemas") or 0),
                    "tables": int(record.get("tables") or 0),
                    "columns": int(record.get("columns") or 0),
                    "metrics": int(record.get("metrics") or 0),
                    "sql": int(record.get("sql") or 0),
                    "tags": int(record.get("tags") or 0),
                    "value_domains": int(record.get("value_domains") or 0),
                }
        except BaseException:
            logger.error("value_domains", exc_info=True)
            return None

    @classmethod
    def _result_formatter(cls, record: Any) -> Any:
        """value_domains"""
        from neo4j_graphrag.types import RetrieverResultItem

        return RetrieverResultItem(
            content=f"{record.get('name')} {record.get('description') or ''}",
            metadata={
                "node_id": record.get("node_id"),
                "labels": record.get("labels"),
                "name": record.get("name"),
                "description": record.get("description"),
                "score": record.get("score"),
            },
        )

    @classmethod
    def _build_hits(
        cls,
        items: list[Any],
        min_score: float,
        node_types: list[str] | None,
        exclude_types: list[str] | None = None,
    ) -> list[SearchHit]:
        """score"""
        hits: list[SearchHit] = []

        for item in items:
            metadata = getattr(item, "metadata", {}) or {}
            score = float(metadata.get("score", 0) or 0)

            if score < min_score:
                continue

            node_id = metadata.get("node_id")
            if not node_id:
                continue

            labels = metadata.get("labels") or []

            # Node type filter
            if exclude_types and any(t in labels for t in exclude_types):
                continue

            # Node type filter
            if node_types and not any(t in labels for t in node_types):
                continue

            hits.append(
                SearchHit(
                    node_id=node_id,
                    score=score,
                    labels=labels,
                    name=metadata.get("name"),
                    description=metadata.get("description"),
                )
            )

        return hits

    @classmethod
    def vector_search(
        cls,
        query: str,
        top_k: int = 10,
        min_score: float = 0.8,
        node_types: list[str] | None = None,
        vector_index: str | None = None,
        tenant_id: int | None = None,
    ) -> list[SearchHit]:
        """name"""
        index_name = vector_index or cls.DEFAULT_VECTOR_INDEX

        try:
            from neo4j_graphrag.retrievers import VectorCypherRetriever

            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            retriever = VectorCypherRetriever(
                driver=Neo4jClient.get_driver(),
                index_name=index_name,
                retrieval_query=cls.RETRIEVAL_QUERY,
                result_formatter=cls._result_formatter,
                embedder=UnifiedEmbedder(tenant_id),
            )

            results = retriever.search(query_text=query, top_k=top_k)
            items = list(getattr(results, "items", []) or [])

            return cls._build_hits(items, min_score, node_types)

        except BaseException:
            logger.error(
                "\n    vector search\n\n    use VectorCypherRetriever Perform vector search\n\n    Args:query:Search text\n    top_k:Return quantity\n    min_score:Minimum similarity threshold\n    node_types:Node type filter,Such as [\"Table\",\"Column\"]\n    vector_index:vector index name,Use unified index by default\n\n    Returns:SearchHit list\n    "
            )
            return []

    @classmethod
    def fulltext_search(
        cls,
        query: str,
        top_k: int = 10,
        node_types: list[str] | None = None,
        fulltext_index: str | None = None,
    ) -> list[SearchHit]:
        """items"""
        index_name = fulltext_index or cls.DEFAULT_FULLTEXT_INDEX

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                cypher = """
                CALL db.index.fulltext.queryNodes($index_name, $query)
                YIELD node, score
                RETURN
                    node.id AS node_id,
                    labels(node) AS labels,
                    node.name AS name,
                    node.description AS description,
                    score
                ORDER BY score DESC
                LIMIT $top_k
                """

                result = run_cypher(
                    session,
                    cypher,
                    {"index_name": index_name, "query": query, "top_k": top_k},
                )

                hits: list[SearchHit] = []
                for record in result:
                    node_id = record.get("node_id")
                    if not node_id:
                        continue

                    labels = list(record.get("labels") or [])

                    # Exclude by default SQL node
                    if node_types and not any(t in labels for t in node_types):
                        continue

                    hits.append(
                        SearchHit(
                            node_id=node_id,
                            score=float(record.get("score", 0) or 0),
                            labels=labels,
                            name=record.get("name"),
                            description=record.get("description"),
                        )
                    )

                return hits

        except BaseException:
            logger.error("name")
            return []

    @classmethod
    def hybrid_search(
        cls,
        query: str,
        top_k: int = 10,
        min_score: float = 0.3,
        node_types: list[str] | None = None,
        exclude_sql: bool = True,
        vector_index: str | None = None,
        fulltext_index: str | None = None,
        tenant_id: int | None = None,
    ) -> list[SearchHit]:
        """description"""
        v_index = vector_index or cls.DEFAULT_VECTOR_INDEX
        f_index = fulltext_index or cls.DEFAULT_FULLTEXT_INDEX

        # Exclude SQL nodes by default
        exclude_types = ["SQL"] if exclude_sql else None

        try:
            from neo4j_graphrag.retrievers import HybridCypherRetriever
            from neo4j_graphrag.types import HybridSearchRanker

            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            retriever = HybridCypherRetriever(
                driver=Neo4jClient.get_driver(),
                vector_index_name=v_index,
                fulltext_index_name=f_index,
                retrieval_query=cls.RETRIEVAL_QUERY,
                result_formatter=cls._result_formatter,
                embedder=UnifiedEmbedder(tenant_id),
            )

            results = retriever.search(
                query_text=query,
                top_k=top_k,
                ranker=HybridSearchRanker.LINEAR,
                alpha=0.6,
            )
            items = list(getattr(results, "items", []) or [])

            return cls._build_hits(items, min_score, node_types, exclude_types)

        except BaseException:
            logger.error("SQL")
            return []

    @classmethod
    def get_nodes_context(
        cls,
        node_ids: list[str],
    ) -> list[dict[str, Any]]:
        """items"""
        if not node_ids:
            return []

        cypher = """
        UNWIND $node_ids AS nid
        MATCH (n:Knowledge)
        WHERE n.id = nid
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
            END AS qualified_name,
            CASE WHEN 'Column' IN labels THEN n.dataType ELSE null END AS data_type,
            CASE WHEN 'ValueDomain' IN labels THEN n.domainType ELSE null END AS domain_type,
            CASE WHEN 'ValueDomain' IN labels THEN n.items ELSE null END AS items
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, {"node_ids": node_ids})
                records = result.data()
                return [convert_neo4j_types(r) for r in records]
        except BaseException:
            logger.error(
                "\n    UNWIND $node_ids AS nid\n    MATCH (n:Knowledge)\n    WHERE n.id = nid\n    WITH n,labels(n) AS labels\n\n    WITH\n    n,labels,head([x IN labels WHERE x <> 'Knowledge']) AS primary_label,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | cat.name]) AS table_catalog,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | sch.name]) AS table_schema,head([(sch:Schema:Knowledge)-[:HAS_TABLE]->(n) | n.name]) AS table_name,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(n) | cat.name]) AS schema_catalog,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | cat.name]) AS column_catalog,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | sch.name]) AS column_schema,head([(cat:Catalog:Knowledge)-[:HAS_SCHEMA]->(sch:Schema:Knowledge)-[:HAS_TABLE]->(t:Table:Knowledge)-[:HAS_COLUMN]->(n) | t.name]) AS column_table\n\n    RETURN\n    elementId(n) AS element_id,labels AS labels,primary_label AS primary_label,coalesce(n.id,null) AS node_id,CASE\n    WHEN 'ValueDomain' IN labels THEN coalesce(n.domainCode,null)\n    ELSE coalesce(n.code,null)\n    END AS code,CASE\n    WHEN 'ValueDomain' IN labels THEN coalesce(n.domainName,n.domainCode,null)\n    ELSE n.name\n    END AS name,CASE\n    WHEN 'ValueDomain' IN labels THEN coalesce(n.domainName,null)\n    ELSE coalesce(n.displayName,null)\n    END AS display_name,coalesce(n.description,null) AS description,coalesce(n.tags,[]) AS tags,coalesce(table_catalog,schema_catalog,column_catalog,null) AS catalog_name,coalesce(table_schema,column_schema,null) AS schema_name,coalesce(table_name,column_table,null) AS table_name,CASE\n    WHEN 'Catalog' IN labels THEN n.name\n    WHEN 'Schema' IN labels THEN coalesce(schema_catalog,'') + '.' + n.name\n    WHEN 'Table' IN labels THEN coalesce(table_catalog,'') + '.' + coalesce(table_schema,'') + '.' + n.name\n    WHEN 'Column' IN labels THEN coalesce(column_catalog,'') + '.' + coalesce(column_schema,'') + '.' + coalesce(column_table,'') + '.' + n.name\n    WHEN 'ValueDomain' IN labels THEN 'valuedomain.' + coalesce(n.domainCode,'')\n    ELSE coalesce(n.code,n.name)\n    END AS path,CASE\n    WHEN 'Schema' IN labels THEN coalesce(schema_catalog,'') + '.' + n.name\n    WHEN 'Table' IN labels THEN coalesce(table_schema,'') + '.' + n.name\n    WHEN 'Column' IN labels THEN coalesce(column_schema,'') + '.' + coalesce(column_table,'') + '.' + n.name\n    WHEN 'ValueDomain' IN labels THEN 'valuedomain.' + coalesce(n.domainCode,'')\n    ELSE coalesce(n.code,n.name)\n    END AS qualified_name,CASE WHEN 'Column' IN labels THEN n.dataType ELSE null END AS data_type,CASE WHEN 'ValueDomain' IN labels THEN n.domainType ELSE null END AS domain_type,CASE WHEN 'ValueDomain' IN labels THEN n.items ELSE null END AS items\n    "
            )
            return []
