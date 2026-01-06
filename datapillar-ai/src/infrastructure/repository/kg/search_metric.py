"""
Neo4j 指标查询服务

职责：提供指标相关的查询功能
- get_metric_context: 根据指标 code 列表查询指标详情
- search_metrics: 向量搜索指标
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
    def get_metric_context(cls, codes: list[str]) -> list[dict]:
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

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher, {"codes": codes})
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
        cls, query: str, top_k: int = 3, min_score: float = 0.8
    ) -> list[dict[str, Any]]:
        """
        向量搜索指标

        搜索原子指标和派生指标索引，返回匹配的指标。

        参数：
        - query: 搜索文本
        - top_k: 每个索引返回的数量
        - min_score: 最小相似度阈值

        返回：
        - 推荐的指标列表，包含 type, code, name, description, score
        """
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()
        index_names = ["atomic_metric_embedding", "derived_metric_embedding"]

        # 生成 embedding
        embedder = UnifiedEmbedder()
        query_vector = embedder.embed_query(query)

        driver = Neo4jClient.get_driver()
        all_results: list[dict] = []

        def search_single_index(index_name: str) -> list[dict]:
            """单索引查询"""
            results = []
            try:
                with driver.session(database=settings.neo4j_database) as session:
                    cypher = """
                    CALL db.index.vector.queryNodes($index_name, $top_k, $vector)
                    YIELD node, score
                    WHERE score >= $min_score
                    RETURN elementId(node) AS element_id, score
                    """
                    result = run_cypher(
                        session,
                        cypher,
                        {
                            "index_name": index_name,
                            "top_k": top_k,
                            "vector": query_vector,
                            "min_score": min_score,
                        },
                    )
                    for record in result:
                        results.append(
                            {"element_id": record["element_id"], "score": record["score"]}
                        )
            except Exception as e:
                logger.warning(f"向量搜索[{index_name}]失败: {e}")
            return results

        # 并行查询多个索引
        with ThreadPoolExecutor(max_workers=len(index_names)) as executor:
            futures = {executor.submit(search_single_index, idx): idx for idx in index_names}
            for future in as_completed(futures):
                all_results.extend(future.result())

        if not all_results:
            return []

        # 按 score 排序，取 top 5
        all_results.sort(key=lambda x: x.get("score", 0), reverse=True)
        all_results = all_results[:5]

        element_ids = [r.get("element_id") for r in all_results if r.get("element_id")]
        score_map = {r.get("element_id"): r.get("score", 0) for r in all_results}

        if not element_ids:
            return []

        # 批量查询上下文
        try:
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cls._METRIC_CYPHER, {"element_ids": element_ids})
                records = result.data()

                recommendations = []
                for r in records:
                    eid = r.get("element_id")
                    r["score"] = round(score_map.get(eid, 0), 3)
                    del r["element_id"]
                    recommendations.append(r)

                logger.debug(f"[指标搜索] 总耗时: {time.time() - start:.3f}s")
                return recommendations
        except Exception as e:
            logger.error(f"搜索指标上下文失败: {e}")
            return []
