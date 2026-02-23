# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 指标查询服务

职责：提供指标相关的查询功能
- get_metric_context: 根据指标 code 列表查询指标详情
- search_metrics: 混合检索指标（向量 + 全文）
"""

from __future__ import annotations

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.infrastructure.database.cypher import run_cypher
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jMetricSearch:
    """Neo4j 指标查询服务"""

    _SYSTEM_CREATORS = ["OPENLINEAGE", "GRAVITINO_SYNC", "system", "SYSTEM"]

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
    def get_metric_context(
        cls,
        codes: list[str],
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> list[dict]:
        """
        根据指标 code 列表查询指标详情

        参数：
        - codes: 指标 code 列表

        返回：
        - 指标上下文列表
        """
        if not codes:
            return []

        cypher = """
        UNWIND $codes AS code
        OPTIONAL MATCH (m:AtomicMetric {code: code})
        WHERE ($tenantId IS NULL OR m.tenantId = $tenantId)
          AND (
            $userId IS NULL
            OR m.createdBy IS NULL
            OR toString(m.createdBy) = toString($userId)
            OR m.createdBy IN $systemCreators
          )
        OPTIONAL MATCH (d:DerivedMetric {code: code})
        WHERE ($tenantId IS NULL OR d.tenantId = $tenantId)
          AND (
            $userId IS NULL
            OR d.createdBy IS NULL
            OR toString(d.createdBy) = toString($userId)
            OR d.createdBy IN $systemCreators
          )
        OPTIONAL MATCH (c:CompositeMetric {code: code})
        WHERE ($tenantId IS NULL OR c.tenantId = $tenantId)
          AND (
            $userId IS NULL
            OR c.createdBy IS NULL
            OR toString(c.createdBy) = toString($userId)
            OR c.createdBy IN $systemCreators
          )
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

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(
                    session,
                    cypher,
                    {
                        "codes": codes,
                        "tenantId": tenant_id,
                        "userId": user_id,
                        "systemCreators": cls._SYSTEM_CREATORS,
                    },
                )
                records = result.data()

            logger.debug(f"从 Neo4j 获取 {len(records)} 个指标上下文")

            return [
                {
                    "code": r["code"],
                    "name": r["name"],
                    "description": r["description"],
                    "metric_type": r["metricType"],
                    "unit": r["unit"],
                    "calculation_formula": r["calculationFormula"],
                    "aggregation_logic": r["aggregationLogic"],
                }
                for r in records
            ]
        except Exception as e:
            logger.error(f"获取指标上下文失败: {e}")
            return []

    @classmethod
    def search_metrics(
        cls,
        query: str,
        top_k: int = 3,
        min_score: float = 0.55,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        混合搜索指标

        参数：
        - query: 搜索文本
        - top_k: 每个索引返回的数量
        - min_score: 最小相似度阈值

        返回：
        - 推荐的指标列表，包含 type, code, name, description, score
        """
        from neo4j_graphrag.retrievers import HybridCypherRetriever
        from neo4j_graphrag.types import HybridSearchRanker, RetrieverResultItem

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        search_configs = [
            {
                "metric_type": "AtomicMetric",
                "vector_index": "atomic_metric_embedding",
                "fulltext_index": "atomic_metric_fulltext",
            },
            {
                "metric_type": "DerivedMetric",
                "vector_index": "derived_metric_embedding",
                "fulltext_index": "derived_metric_fulltext",
            },
            {
                "metric_type": "CompositeMetric",
                "vector_index": "composite_metric_embedding",
                "fulltext_index": "composite_metric_fulltext",
            },
        ]

        def metric_result_formatter(record: Any) -> Any:
            return RetrieverResultItem(
                content=f"{record.get('name')} {record.get('description') or ''}",
                metadata={
                    "type": record.get("type"),
                    "code": record.get("code"),
                    "name": record.get("name"),
                    "description": record.get("description"),
                    "score": record.get("score"),
                },
            )

        def hybrid_search_single(config: dict[str, str]) -> list[dict[str, Any]]:
            results: list[dict[str, Any]] = []
            retrieval_query = f"""
            WHERE ($tenantId IS NULL OR node.tenantId = $tenantId)
              AND (
                $userId IS NULL
                OR node.createdBy IS NULL
                OR toString(node.createdBy) = toString($userId)
                OR node.createdBy IN $systemCreators
              )
            RETURN
                node.id AS node_id,
                '{config["metric_type"]}' AS type,
                node.code AS code,
                node.name AS name,
                node.description AS description,
                score
            """

            try:
                retriever = HybridCypherRetriever(
                    driver=Neo4jClient.get_driver(),
                    vector_index_name=config["vector_index"],
                    fulltext_index_name=config["fulltext_index"],
                    retrieval_query=retrieval_query,
                    result_formatter=metric_result_formatter,
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
                    results.append(
                        {
                            "type": metadata.get("type"),
                            "code": metadata.get("code"),
                            "name": metadata.get("name"),
                            "description": metadata.get("description"),
                            "score": round(score, 3),
                        }
                    )
            except Exception as e:
                logger.warning(f"混合搜索[{config['metric_type']}]失败: {e}")

            return results

        all_results: list[dict[str, Any]] = []
        with ThreadPoolExecutor(max_workers=len(search_configs)) as executor:
            futures = [executor.submit(hybrid_search_single, cfg) for cfg in search_configs]
            for future in as_completed(futures):
                all_results.extend(future.result())

        if not all_results:
            return []

        all_results.sort(key=lambda x: x.get("score", 0), reverse=True)
        all_results = all_results[:top_k]

        logger.debug(f"[指标搜索] 总耗时: {time.time() - start:.3f}s")
        return all_results
