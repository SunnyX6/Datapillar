# @author Sunny
# @date 2026-01-27

"""
SQL Search service

Responsibilities:Search Reference SQL,History for the agent to refer to SQL Writing method
design principles:- SQL Node does not participate in pointer retrieval(hybrid_search exclude SQL)
- Users can enter natural language or SQL Fragment to search
- Based on SQL of summary(LLM Generated semantic summary)Perform vector matching

Usage scenarios:- User posts a paragraph SQL,Find similar history SQL
- User description requirements,Find relevant references SQL
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from src.infrastructure.database import Neo4jClient

logger = logging.getLogger(__name__)


@dataclass
class SQLHit:
    """SQL Search hits(Reference SQL)"""

    node_id: str  # SQL node ID
    score: float  # relevance score
    content: str | None  # SQL content
    summary: str | None  # SQL Summary
    tags: str | None  # label
    dialect: str | None  # SQL dialect
    engine: str | None  # execution engine


class Neo4jSQLSearch:
    """Neo4j SQL Search service"""

    @classmethod
    def search_reference_sql(
        cls,
        query: str,
        top_k: int = 5,
        min_score: float = 0.6,
        dialect: str | None = None,
        engine: str | None = None,
        tenant_id: int | None = None,
    ) -> list[SQLHit]:
        """
        Search Reference SQL

        Used to find history similar to user input SQL.User can enter:- natural language description:Such as "Summarize transaction amount by user"
        - SQL fragment:Such as "SELECT user_id,SUM(amount) FROM orders"

        Search based on SQL of summary(LLM Generated semantic summary)Perform vector matching.Args:query:Search text(natural language or SQL)
        top_k:Return quantity
        min_score:Minimum similarity threshold
        dialect:SQL Dialect filtering(Such as spark,flink)
        engine:Perform engine filtering

        Returns:SQLHit list
        """
        try:
            from neo4j_graphrag.retrievers import VectorCypherRetriever

            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            # SQL dedicated retrieval_query
            sql_retrieval_query = """
            RETURN node.id AS node_id,
                   node.content AS content,
                   node.summary AS summary,
                   node.tags AS tags,
                   node.dialect AS dialect,
                   node.engine AS engine,
                   score
            """

            # SQL dedicated result_formatter
            def sql_result_formatter(record: Any) -> Any:
                from neo4j_graphrag.types import RetrieverResultItem

                return RetrieverResultItem(
                    content=record.get("summary") or record.get("content") or "",
                    metadata={
                        "node_id": record.get("node_id"),
                        "content": record.get("content"),
                        "summary": record.get("summary"),
                        "tags": record.get("tags"),
                        "dialect": record.get("dialect"),
                        "engine": record.get("engine"),
                        "score": record.get("score"),
                    },
                )

            retriever = VectorCypherRetriever(
                driver=Neo4jClient.get_driver(),
                index_name="sql_embedding",  # SQL Private vector index
                retrieval_query=sql_retrieval_query,
                result_formatter=sql_result_formatter,
                embedder=UnifiedEmbedder(tenant_id),
            )

            results = retriever.search(query_text=query, top_k=top_k)
            items = list(getattr(results, "items", []) or [])

            hits: list[SQLHit] = []
            for item in items:
                metadata = getattr(item, "metadata", {}) or {}
                score = float(metadata.get("score", 0) or 0)

                if score < min_score:
                    continue

                node_id = metadata.get("node_id")
                if not node_id:
                    continue

                # Dialect filtering
                if dialect and metadata.get("dialect") != dialect:
                    continue

                # engine filter
                if engine and metadata.get("engine") != engine:
                    continue

                hits.append(
                    SQLHit(
                        node_id=node_id,
                        score=score,
                        content=metadata.get("content"),
                        summary=metadata.get("summary"),
                        tags=metadata.get("tags"),
                        dialect=metadata.get("dialect"),
                        engine=metadata.get("engine"),
                    )
                )

            return hits

        except Exception as e:
            logger.error(f"Reference SQL Search failed:{e}")
            return []
