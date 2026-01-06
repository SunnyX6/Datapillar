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
from src.infrastructure.database.cypher import run_cypher
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jSemanticSearch:
    """Neo4j 语义资产查询服务（词根、修饰符、单位）"""

    _WORDROOT_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (w:WordRoot)
    WHERE elementId(w) = eid
    RETURN
        elementId(w) AS element_id,
        w.code AS code,
        w.name AS name,
        w.dataType AS dataType,
        w.description AS description
    """

    _MODIFIER_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (m:Modifier)
    WHERE elementId(m) = eid
    RETURN
        elementId(m) AS element_id,
        m.code AS code,
        m.modifierType AS modifierType,
        m.description AS description
    """

    _UNIT_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (u:Unit)
    WHERE elementId(u) = eid
    RETURN
        elementId(u) AS element_id,
        u.code AS code,
        u.name AS name,
        u.symbol AS symbol,
        u.description AS description
    """

    @classmethod
    def search_semantic_assets(
        cls, query: str, top_k: int = 10, min_score: float = 0.75
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
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        embedder = UnifiedEmbedder()
        query_vector = embedder.embed_query(query)

        driver = Neo4jClient.get_driver()

        search_configs = [
            {
                "name": "word_roots",
                "vector_index": "wordroot_embedding",
                "fulltext_index": "wordroot_fulltext",
                "cypher": cls._WORDROOT_CYPHER,
            },
            {
                "name": "modifiers",
                "vector_index": "modifier_embedding",
                "fulltext_index": "modifier_fulltext",
                "cypher": cls._MODIFIER_CYPHER,
            },
            {
                "name": "units",
                "vector_index": "unit_embedding",
                "fulltext_index": "unit_fulltext",
                "cypher": cls._UNIT_CYPHER,
            },
        ]

        def hybrid_search_single(config: dict) -> tuple[str, list]:
            """单资产类型混合搜索（支持降级）"""
            results: list[dict] = []
            score_map: dict[str, float] = {}

            try:
                with driver.session(database=settings.neo4j_database) as session:
                    vector_cypher = """
                    CALL db.index.vector.queryNodes($index_name, $top_k, $vector)
                    YIELD node, score
                    WHERE score >= $min_score
                    RETURN elementId(node) AS element_id, score
                    """
                    vector_result = run_cypher(
                        session,
                        vector_cypher,
                        {
                            "index_name": config["vector_index"],
                            "top_k": top_k,
                            "vector": query_vector,
                            "min_score": min_score,
                        },
                    )
                    for record in vector_result:
                        eid = record["element_id"]
                        score_map[eid] = max(score_map.get(eid, 0), record["score"])

                    fulltext_cypher = """
                    CALL db.index.fulltext.queryNodes($index_name, $query)
                    YIELD node, score
                    WHERE score >= 0.5
                    RETURN elementId(node) AS element_id, score / 10.0 AS score
                    LIMIT $top_k
                    """
                    fulltext_result = run_cypher(
                        session,
                        fulltext_cypher,
                        {"index_name": config["fulltext_index"], "query": query, "top_k": top_k},
                    )
                    for record in fulltext_result:
                        eid = record["element_id"]
                        score_map[eid] = max(score_map.get(eid, 0), record["score"])

                    if not score_map:
                        fallback_min_score = 0.55
                        vector_result = run_cypher(
                            session,
                            vector_cypher,
                            {
                                "index_name": config["vector_index"],
                                "top_k": top_k,
                                "vector": query_vector,
                                "min_score": fallback_min_score,
                            },
                        )
                        for record in vector_result:
                            eid = record["element_id"]
                            score_map[eid] = max(score_map.get(eid, 0), record["score"])

                    if not score_map:
                        return (config["name"], [])

                    sorted_items = sorted(score_map.items(), key=lambda x: x[1], reverse=True)[
                        :top_k
                    ]
                    element_ids = [eid for eid, _ in sorted_items]

                    detail_result = run_cypher(
                        session, config["cypher"], {"element_ids": element_ids}
                    )
                    for record in detail_result:
                        data = dict(record)
                        eid = data.pop("element_id")
                        data["score"] = round(score_map.get(eid, 0), 3)
                        results.append(data)

                    results.sort(key=lambda x: x.get("score", 0), reverse=True)

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
