# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 列查询服务

职责：提供列相关的混合检索（向量 + 全文）
"""

from __future__ import annotations

import logging
import time
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jColumnSearch:
    """Neo4j 列查询服务"""

    _SYSTEM_CREATORS = ["OPENLINEAGE", "GRAVITINO_SYNC", "system", "SYSTEM"]

    _COLUMN_RETRIEVAL_QUERY = """
    OPTIONAL MATCH (t:Table)-[:HAS_COLUMN]->(node)
    OPTIONAL MATCH (s:Schema)-[:HAS_TABLE]->(t)
    OPTIONAL MATCH (c:Catalog)-[:HAS_SCHEMA]->(s)
    WHERE ($tenantId IS NULL OR coalesce(node.tenantId, t.tenantId, s.tenantId, c.tenantId) = $tenantId)
      AND (
        $userId IS NULL
        OR node.createdBy IS NULL
        OR toString(node.createdBy) = toString($userId)
        OR node.createdBy IN $systemCreators
      )
    RETURN
        node.id AS node_id,
        'Column' AS type,
        CASE
            WHEN c IS NULL OR s IS NULL OR t IS NULL THEN node.name
            ELSE c.name + '.' + s.name + '.' + t.name + '.' + node.name
        END AS path,
        node.name AS name,
        coalesce(node.description, '') AS description,
        node.dataType AS dataType,
        CASE
            WHEN c IS NULL OR s IS NULL OR t IS NULL THEN null
            ELSE c.name + '.' + s.name + '.' + t.name
        END AS table,
        score
    """

    @classmethod
    def search_columns(
        cls,
        query: str,
        top_k: int = 3,
        min_score: float = 0.55,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        混合搜索列（向量 + 全文）

        参数：
        - query: 搜索文本
        - top_k: 返回数量上限
        - min_score: 最小相似度阈值
        """
        from neo4j_graphrag.retrievers import HybridCypherRetriever
        from neo4j_graphrag.types import HybridSearchRanker, RetrieverResultItem

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        def column_result_formatter(record: Any) -> Any:
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
                vector_index_name="column_embedding",
                fulltext_index_name="column_fulltext",
                retrieval_query=cls._COLUMN_RETRIEVAL_QUERY,
                result_formatter=column_result_formatter,
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

            logger.debug(f"[列搜索] 总耗时: {time.time() - start:.3f}s")
            return recommendations
        except Exception as e:
            logger.error(f"混合搜索列失败: {e}")
            return []
