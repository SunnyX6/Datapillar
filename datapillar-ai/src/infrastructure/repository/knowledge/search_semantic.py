# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 语义资产查询服务

职责：提供语义资产相关的查询功能
- search_semantic_assets: 混合检索词根、修饰符、单位
"""

from __future__ import annotations

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jSemanticSearch:
    """Neo4j 语义资产查询服务（词根、修饰符、单位）"""

    _SYSTEM_CREATORS = ["OPENLINEAGE", "GRAVITINO_SYNC", "system", "SYSTEM"]

    @classmethod
    def search_semantic_assets(
        cls,
        query: str,
        top_k: int = 10,
        min_score: float = 0.55,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> dict[str, list[Any]]:
        """
        根据用户输入语义检索相关的词根、修饰符、单位（混合检索：向量+全文）

        参数：
        - query: 用户输入文本
        - top_k: 每种资产类型返回的数量
        - min_score: 最小相似度阈值

        返回：
        {
            "word_roots": [{"code": ..., "name": ..., "dataType": ..., "score": ...}],
            "modifiers": [{"code": ..., "modifierType": ..., "score": ...}],
            "units": [{"code": ..., "name": ..., "symbol": ..., "score": ...}]
        }
        """
        from neo4j_graphrag.retrievers import HybridCypherRetriever
        from neo4j_graphrag.types import HybridSearchRanker, RetrieverResultItem

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        search_configs = [
            {
                "name": "word_roots",
                "vector_index": "wordroot_embedding",
                "fulltext_index": "wordroot_fulltext",
                "retrieval_query": """
                WHERE ($tenantId IS NULL OR node.tenantId = $tenantId)
                  AND (
                    $userId IS NULL
                    OR node.createdBy IS NULL
                    OR toString(node.createdBy) = toString($userId)
                    OR node.createdBy IN $systemCreators
                  )
                RETURN
                    node.code AS code,
                    node.name AS name,
                    node.dataType AS dataType,
                    node.description AS description,
                    score
                """,
            },
            {
                "name": "modifiers",
                "vector_index": "modifier_embedding",
                "fulltext_index": "modifier_fulltext",
                "retrieval_query": """
                WHERE ($tenantId IS NULL OR node.tenantId = $tenantId)
                  AND (
                    $userId IS NULL
                    OR node.createdBy IS NULL
                    OR toString(node.createdBy) = toString($userId)
                    OR node.createdBy IN $systemCreators
                  )
                RETURN
                    node.code AS code,
                    node.modifierType AS modifierType,
                    node.description AS description,
                    score
                """,
            },
            {
                "name": "units",
                "vector_index": "unit_embedding",
                "fulltext_index": "unit_fulltext",
                "retrieval_query": """
                WHERE ($tenantId IS NULL OR node.tenantId = $tenantId)
                  AND (
                    $userId IS NULL
                    OR node.createdBy IS NULL
                    OR toString(node.createdBy) = toString($userId)
                    OR node.createdBy IN $systemCreators
                  )
                RETURN
                    node.code AS code,
                    node.name AS name,
                    node.symbol AS symbol,
                    node.description AS description,
                    score
                """,
            },
        ]

        def semantic_result_formatter(record: Any) -> Any:
            return RetrieverResultItem(
                content=f"{record.get('name') or record.get('code')} {record.get('description') or ''}",
                metadata=dict(record),
            )

        def hybrid_search_single(config: dict[str, str]) -> tuple[str, list]:
            """单资产类型混合搜索"""
            results: list[dict[str, Any]] = []

            try:
                retriever = HybridCypherRetriever(
                    driver=Neo4jClient.get_driver(),
                    vector_index_name=config["vector_index"],
                    fulltext_index_name=config["fulltext_index"],
                    retrieval_query=config["retrieval_query"],
                    result_formatter=semantic_result_formatter,
                    embedder=UnifiedEmbedder(tenant_id),
                    neo4j_database=settings.neo4j_database,
                )

                search_result = retriever.search(
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
                items = list(getattr(search_result, "items", []) or [])

                for item in items:
                    metadata = getattr(item, "metadata", {}) or {}
                    score = float(metadata.get("score", 0) or 0)
                    if score < min_score:
                        continue
                    metadata["score"] = round(score, 3)
                    results.append(metadata)

            except Exception as e:
                logger.warning(f"语义资产搜索[{config['name']}]失败: {e}")

            return (config["name"], results)

        result: dict[str, list[Any]] = {"word_roots": [], "modifiers": [], "units": []}

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
