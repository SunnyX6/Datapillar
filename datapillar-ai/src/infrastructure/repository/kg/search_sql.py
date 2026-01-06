"""
SQL 检索服务

职责：搜索参考 SQL，供智能体参考历史 SQL 写法
设计原则：
- SQL 节点不参与指针检索（hybrid_search 排除 SQL）
- 用户可输入自然语言或 SQL 片段进行搜索
- 基于 SQL 的 summary（LLM 生成的语义摘要）进行向量匹配

使用场景：
- 用户贴一段 SQL，查找相似的历史 SQL
- 用户描述需求，查找相关的参考 SQL
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from src.infrastructure.database import Neo4jClient

logger = logging.getLogger(__name__)


@dataclass
class SQLHit:
    """SQL 搜索命中项（参考 SQL）"""

    node_id: str  # SQL 节点 ID
    score: float  # 相关性得分
    content: str | None  # SQL 内容
    summary: str | None  # SQL 摘要
    tags: str | None  # 标签
    dialect: str | None  # SQL 方言
    engine: str | None  # 执行引擎


class Neo4jSQLSearch:
    """Neo4j SQL 搜索服务"""

    @classmethod
    def search_reference_sql(
        cls,
        query: str,
        top_k: int = 5,
        min_score: float = 0.6,
        dialect: str | None = None,
        engine: str | None = None,
    ) -> list[SQLHit]:
        """
        搜索参考 SQL

        用于查找与用户输入相似的历史 SQL。用户可以输入：
        - 自然语言描述：如 "按用户汇总交易金额"
        - SQL 片段：如 "SELECT user_id, SUM(amount) FROM orders"

        搜索基于 SQL 的 summary（LLM 生成的语义摘要）进行向量匹配。

        Args:
            query: 搜索文本（自然语言或 SQL）
            top_k: 返回数量
            min_score: 最小相似度阈值
            dialect: SQL 方言过滤（如 spark, flink）
            engine: 执行引擎过滤

        Returns:
            SQLHit 列表
        """
        try:
            from neo4j_graphrag.retrievers import VectorCypherRetriever

            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            # SQL 专用的 retrieval_query
            sql_retrieval_query = """
            RETURN node.id AS node_id,
                   node.content AS content,
                   node.summary AS summary,
                   node.tags AS tags,
                   node.dialect AS dialect,
                   node.engine AS engine,
                   score
            """

            # SQL 专用的 result_formatter
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
                index_name="sql_embedding",  # SQL 专用向量索引
                retrieval_query=sql_retrieval_query,
                result_formatter=sql_result_formatter,
                embedder=UnifiedEmbedder(),
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

                # 方言过滤
                if dialect and metadata.get("dialect") != dialect:
                    continue

                # 引擎过滤
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
            logger.error(f"参考 SQL 搜索失败: {e}")
            return []
