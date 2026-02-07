# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 节点搜索服务

职责：给知识库 agent 搜索节点，返回匹配的节点列表
设计原则：
- 使用 neo4j-graphrag 官方库（HybridCypherRetriever / VectorCypherRetriever）
- 统一使用业务 ID（node.id）
- 统一返回结构 SearchHit
- 通过 retrieval_query 一次查询返回所有字段，不做二次查询
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
    """节点搜索命中项（元数据指针）"""

    node_id: str  # 业务 ID（节点的 id 属性）
    score: float  # 相关性得分
    labels: list[str]  # 节点标签，如 ["Knowledge", "Table"]
    name: str | None  # 节点名称
    description: str | None  # 节点描述


class Neo4jNodeSearch:
    """Neo4j 节点搜索服务（基于 neo4j-graphrag）"""

    # 默认索引名称
    DEFAULT_VECTOR_INDEX = "kg_unified_vector_index"
    DEFAULT_FULLTEXT_INDEX = "kg_unified_fulltext_index"

    # 统一的 retrieval_query：一次查询返回所有需要的字段
    # 使用 COALESCE 处理不同节点类型的字段差异：
    #   - Table/Column/Schema 等：使用 name, description
    #   - SQL 节点：使用 summary 作为 name，summary 作为 description
    #   - ValueDomain：使用 code 或 name
    RETRIEVAL_QUERY = """
    RETURN node.id AS node_id,
           labels(node) AS labels,
           COALESCE(node.name, node.code, node.summary) AS name,
           COALESCE(node.description, node.summary) AS description,
           score
    """

    @classmethod
    def get_knowledge_navigation(cls) -> dict[str, int] | None:
        """获取数仓知识导航统计（Catalog/Schema/Table/Column/Metric/SQL/Tag/ValueDomain）"""
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
        except Exception as e:
            logger.error(f"获取数仓知识导航失败: {e}", exc_info=True)
            return None

    @classmethod
    def _result_formatter(cls, record: Any) -> Any:
        """
        格式化 neo4j-graphrag 返回的 record 为 RetrieverResultItem

        将 Cypher 查询结果转换为标准的 RetrieverResultItem 结构
        """
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
        """
        从 neo4j-graphrag 返回的 items 构建 SearchHit 列表

        Args:
            items: retriever.search() 返回的 items
            min_score: 最小相似度阈值
            node_types: 节点类型过滤（包含）
            exclude_types: 节点类型排除

        Returns:
            SearchHit 列表
        """
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

            # 排除指定类型
            if exclude_types and any(t in labels for t in exclude_types):
                continue

            # 节点类型过滤
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
        """
        向量搜索

        使用 VectorCypherRetriever 进行向量搜索

        Args:
            query: 搜索文本
            top_k: 返回数量
            min_score: 最小相似度阈值
            node_types: 节点类型过滤，如 ["Table", "Column"]
            vector_index: 向量索引名称，默认使用统一索引

        Returns:
            SearchHit 列表
        """
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

        except Exception as e:
            logger.error(f"向量搜索失败: {e}")
            return []

    @classmethod
    def fulltext_search(
        cls,
        query: str,
        top_k: int = 10,
        node_types: list[str] | None = None,
        fulltext_index: str | None = None,
    ) -> list[SearchHit]:
        """
        全文搜索

        使用原生 Cypher 进行全文搜索（neo4j-graphrag 没有单独的 FulltextRetriever）

        Args:
            query: 搜索文本
            top_k: 返回数量
            node_types: 节点类型过滤，如 ["Table", "Column"]
            fulltext_index: 全文索引名称，默认使用统一索引

        Returns:
            SearchHit 列表
        """
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

                    # 节点类型过滤
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

        except Exception as e:
            logger.error(f"全文搜索失败: {e}")
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
        """
        混合搜索（向量 + 全文）- 用于元数据指针检索

        使用 HybridCypherRetriever 进行混合搜索，默认排除 SQL 节点。
        SQL 节点应使用 search_reference_sql 方法单独搜索。

        Args:
            query: 搜索文本
            top_k: 返回数量
            min_score: 最小相似度阈值
            node_types: 节点类型过滤，如 ["Table", "Column"]
            exclude_sql: 是否排除 SQL 节点，默认 True
            vector_index: 向量索引名称
            fulltext_index: 全文索引名称

        Returns:
            SearchHit 列表
        """
        v_index = vector_index or cls.DEFAULT_VECTOR_INDEX
        f_index = fulltext_index or cls.DEFAULT_FULLTEXT_INDEX

        # 默认排除 SQL 节点
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

        except Exception as e:
            logger.error(f"混合搜索失败: {e}")
            return []

    @classmethod
    def get_nodes_context(
        cls,
        node_ids: list[str],
    ) -> list[dict[str, Any]]:
        """
        批量获取 Knowledge 节点上下文

        设计目标：
        - 给 KnowledgeAgent 构造"指针（pointer）"使用：必须可定位、可再解析
        - 不返回列明细/大字段，只返回定位与导航信息

        返回字段（按节点类型尽可能填充）：
        - element_id, labels, primary_label, node_id, code, name, display_name, description, tags
        - catalog_name, schema_name, table_name, path, qualified_name

        Args:
            node_ids: 节点业务 ID 列表

        Returns:
            节点上下文列表
        """
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
        except Exception as e:
            logger.error(f"批量获取 Knowledge 节点上下文失败: {e}")
            return []
