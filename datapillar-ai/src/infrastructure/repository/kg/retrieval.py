"""
Neo4j 知识图谱：向量/混合检索

说明：
- 这里只放检索逻辑（向量检索 / 混合检索 / 语义召回）。
- 标准 Cypher 查询与写回在 `kg/queries.py`。
"""

from __future__ import annotations

import logging
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.infrastructure.database.cypher import run_cypher
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jKGRepositoryRetrieval:
    """Neo4j 知识图谱统一数据访问层（检索）"""

    _vector_retrievers: dict[str, Any] = {}

    # =========================================================================
    # 4. GraphRAG 向量检索 - 基于 neo4j_graphrag 的向量/混合检索
    # =========================================================================

    # Table/Column 上下文查询 Cypher 模板
    _TABLE_COLUMN_CYPHER = """
    UNWIND $element_ids AS eid
    MATCH (n:Knowledge)
    WHERE elementId(n) = eid
    WITH n, labels(n) AS node_labels

    // Column: 获取 Table -> Schema -> Catalog 路径
    OPTIONAL MATCH (t:Table)-[:HAS_COLUMN]->(n)
    WHERE 'Column' IN node_labels
    OPTIONAL MATCH (s:Schema)-[:HAS_TABLE]->(t)
    OPTIONAL MATCH (c:Catalog)-[:HAS_SCHEMA]->(s)

    // Table: 获取 Schema -> Catalog 路径
    OPTIONAL MATCH (s2:Schema)-[:HAS_TABLE]->(n)
    WHERE 'Table' IN node_labels
    OPTIONAL MATCH (c2:Catalog)-[:HAS_SCHEMA]->(s2)

    RETURN
        elementId(n) AS element_id,
        CASE
            WHEN 'Column' IN node_labels THEN 'Column'
            WHEN 'Table' IN node_labels THEN 'Table'
            ELSE head(node_labels)
        END AS type,
        CASE
            WHEN 'Column' IN node_labels THEN c.name + '.' + s.name + '.' + t.name + '.' + n.name
            WHEN 'Table' IN node_labels THEN c2.name + '.' + s2.name + '.' + n.name
            ELSE n.name
        END AS path,
        n.name AS name,
        n.description AS description,
        CASE WHEN 'Column' IN node_labels THEN n.dataType ELSE null END AS dataType,
        CASE WHEN 'Column' IN node_labels THEN c.name + '.' + s.name + '.' + t.name ELSE null END AS table
    """

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
    def _get_vector_retriever(cls, index_name: str = "kg_unified_vector_index"):
        """懒加载向量检索器（按索引名缓存）"""
        if index_name not in cls._vector_retrievers:
            from neo4j_graphrag.retrievers import VectorRetriever

            from src.infrastructure.llm.embeddings import UnifiedEmbedder

            try:
                cls._vector_retrievers[index_name] = VectorRetriever(
                    driver=Neo4jClient.get_driver(),
                    index_name=index_name,
                    embedder=UnifiedEmbedder(),
                    return_properties=["id", "name", "displayName", "description"],
                )
                logger.info(f"VectorRetriever[{index_name}] 初始化成功")
            except Exception as e:
                logger.warning(f"VectorRetriever[{index_name}] 初始化失败: {e}")
                return None
        return cls._vector_retrievers.get(index_name)

    @classmethod
    def _search_with_context(
        cls,
        query: str,
        index_names: list[str],
        cypher_template: str,
        top_k: int = 3,
        min_score: float = 0.8,
    ) -> list[dict[str, Any]]:
        """
        向量搜索 + Cypher 批量获取完整上下文（私有方法）

        先在多个向量索引中搜索，合并结果后用 Cypher 查询完整上下文。
        优化：
        1. 只调用一次 embedding API，复用向量在多个索引中搜索
        2. 使用 ThreadPoolExecutor 并行查询多个索引
        3. 过滤低于 min_score 阈值的结果
        """
        import time
        from concurrent.futures import ThreadPoolExecutor, as_completed

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        # 使用单例 Embedder
        embedder = UnifiedEmbedder()

        embed_start = time.time()
        query_vector = embedder.embed_query(query)
        logger.debug(f"[向量搜索] embedding 耗时: {time.time() - embed_start:.3f}s")

        # 2. 使用线程池并行查询多个索引
        driver = Neo4jClient.get_driver()
        all_results = []

        def search_single_index(index_name: str) -> list[dict]:
            """单索引查询（用于并行执行）"""
            search_start = time.time()
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
                logger.debug(
                    f"[向量搜索] {index_name} 耗时: {time.time() - search_start:.3f}s, 结果: {len(results)}"
                )
            except Exception as e:
                logger.warning(f"向量搜索[{index_name}]失败: {e}")
            return results

        # 并行执行多索引查询
        parallel_start = time.time()
        with ThreadPoolExecutor(max_workers=len(index_names)) as executor:
            futures = {executor.submit(search_single_index, idx): idx for idx in index_names}
            for future in as_completed(futures):
                all_results.extend(future.result())
        logger.debug(f"[向量搜索] 并行查询耗时: {time.time() - parallel_start:.3f}s")

        if not all_results:
            return []

        # 3. 按 score 排序，取 top 5
        all_results.sort(key=lambda x: x.get("score", 0), reverse=True)
        all_results = all_results[:5]

        # 4. 提取 element_id 和 score
        element_ids = [r.get("element_id") for r in all_results if r.get("element_id")]
        score_map = {r.get("element_id"): r.get("score", 0) for r in all_results}

        if not element_ids:
            return []

        # 5. 单次 Cypher 批量查询完整上下文
        try:
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, cypher_template, {"element_ids": element_ids})
                records = result.data()

                recommendations = []
                for r in records:
                    eid = r.get("element_id")
                    r["score"] = round(score_map.get(eid, 0), 3)
                    del r["element_id"]
                    recommendations.append(r)

                logger.debug(f"[向量搜索] 总耗时: {time.time() - start:.3f}s")
                return recommendations
        except Exception as e:
            logger.error(f"搜索上下文失败: {e}")
            return []

    @classmethod
    def search_tables_columns(cls, query: str, top_k: int = 3) -> list[dict[str, Any]]:
        """
        搜索表和列（同步方法）

        向量搜索表和列索引，返回匹配的表和列及其完整路径。

        Args:
            query: 搜索文本
            top_k: 每个索引返回的数量

        Returns:
            推荐的表和列列表，包含 type, path, name, description, dataType, table, score
        """
        return cls._search_with_context(
            query=query,
            index_names=["table_embedding", "column_embedding"],
            cypher_template=cls._TABLE_COLUMN_CYPHER,
            top_k=top_k,
        )

    @classmethod
    def search_metrics(cls, query: str, top_k: int = 3) -> list[dict[str, Any]]:
        """
        搜索指标（同步方法）

        向量搜索原子指标和派生指标索引，返回匹配的指标。

        Args:
            query: 搜索文本
            top_k: 每个索引返回的数量

        Returns:
            推荐的指标列表，包含 type, code, name, description, score
        """
        return cls._search_with_context(
            query=query,
            index_names=["atomic_metric_embedding", "derived_metric_embedding"],
            cypher_template=cls._METRIC_CYPHER,
            top_k=top_k,
        )

    @classmethod
    def vector_search(
        cls,
        query: str,
        top_k: int = 10,
        index_name: str = "kg_unified_vector_index",
        filters: dict | None = None,
        min_score: float = 0.8,
    ) -> list[dict[str, Any]]:
        """
        向量语义检索（同步方法）

        基于 neo4j_graphrag 的 VectorRetriever，使用向量相似度搜索知识图谱节点。

        Args:
            query: 搜索文本
            top_k: 返回数量
            index_name: 向量索引名称
            filters: 过滤条件
            min_score: 最小相似度阈值

        Returns:
            包含 element_id、content、score 的结果列表
        """
        retriever = cls._get_vector_retriever(index_name)
        if not retriever:
            return []

        try:
            results = retriever.search(query_text=query, top_k=top_k, filters=filters)
            return (
                [
                    {
                        "element_id": item.metadata.get("id") if item.metadata else None,
                        "content": item.content,
                        "score": item.metadata.get("score") if item.metadata else 0,
                    }
                    for item in results.items
                    if item.metadata and item.metadata.get("score", 0) >= min_score
                ]
                if results.items
                else []
            )
        except Exception as e:
            logger.error(f"向量检索失败[{index_name}]: {e}")
            return []

    # 语义资产 Cypher 模板
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
    ) -> dict[str, list]:
        """
        根据用户输入语义检索相关的词根、修饰符、单位（混合检索：向量+全文）

        Args:
            query: 用户输入文本
            top_k: 每种资产类型返回的数量
            min_score: 最小相似度阈值

        Returns:
            {
                "word_roots": [{"code": ..., "name": ..., "dataType": ..., "score": ...}],
                "modifiers": [{"code": ..., "modifierType": ..., "score": ...}],
                "units": [{"code": ..., "name": ..., "symbol": ..., "score": ...}]
            }
        """
        import time
        from concurrent.futures import ThreadPoolExecutor, as_completed

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        start = time.time()

        # 1. 生成 query embedding（只调用一次）
        embedder = UnifiedEmbedder()
        query_vector = embedder.embed_query(query)

        driver = Neo4jClient.get_driver()

        # 2. 定义混合搜索配置
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
            results = []
            score_map = {}

            try:
                with driver.session(database=settings.neo4j_database) as session:
                    # 向量搜索（先用高阈值）
                    vector_cypher = """
                    CALL db.index.vector.queryNodes($index_name, $top_k, $vector)
                    YIELD node, score
                    WHERE score >= $min_score
                    RETURN elementId(node) AS element_id, score, 'vector' AS source
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

                    # 全文搜索
                    fulltext_cypher = """
                    CALL db.index.fulltext.queryNodes($index_name, $query)
                    YIELD node, score
                    WHERE score >= 0.5
                    RETURN elementId(node) AS element_id, score / 10.0 AS score, 'fulltext' AS source
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

                    # 如果高阈值搜不到，降级用低阈值再搜
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
                        if score_map:
                            logger.debug(
                                f"[语义资产] {config['name']} 降级搜索(阈值={fallback_min_score}): {len(score_map)} 个"
                            )

                    if not score_map:
                        return (config["name"], [])

                    # 按分数排序取 top_k
                    sorted_items = sorted(score_map.items(), key=lambda x: x[1], reverse=True)[
                        :top_k
                    ]
                    element_ids = [eid for eid, _ in sorted_items]

                    # 批量获取详情
                    detail_cypher = config.get("cypher")
                    if not isinstance(detail_cypher, str):
                        raise TypeError("hybrid_search_single 期望 config['cypher'] 为 str")
                    detail_result = run_cypher(session, detail_cypher, {"element_ids": element_ids})
                    for record in detail_result:
                        data = dict(record)
                        eid = data.pop("element_id")
                        data["score"] = round(score_map.get(eid, 0), 3)
                        results.append(data)

                    # 按分数排序
                    results.sort(key=lambda x: x.get("score", 0), reverse=True)

            except Exception as e:
                logger.warning(f"语义资产搜索[{config['name']}]失败: {e}")

            return (config["name"], results)

        # 3. 并行执行三种资产类型的混合搜索
        result = {"word_roots": [], "modifiers": [], "units": []}

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

    @classmethod
    def hybrid_search(
        cls,
        query: str,
        top_k: int = 10,
        vector_index: str = "kg_unified_vector_index",
        fulltext_index: str = "kg_unified_fulltext_index",
        min_score: float = 0.8,
    ) -> list[dict[str, Any]]:
        """
        混合检索（向量 + 全文）（同步方法）

        结合向量语义检索和全文关键词检索，提高召回率。

        Args:
            query: 搜索文本
            top_k: 返回数量
            vector_index: 向量索引名称
            fulltext_index: 全文索引名称
            min_score: 最小相似度阈值

        Returns:
            包含 element_id、content、score 的结果列表
        """
        from neo4j_graphrag.retrievers import HybridRetriever

        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        try:
            retriever = HybridRetriever(
                driver=Neo4jClient.get_driver(),
                vector_index_name=vector_index,
                fulltext_index_name=fulltext_index,
                embedder=UnifiedEmbedder(),
                return_properties=["name", "displayName", "description"],
            )
            results = retriever.search(query_text=query, top_k=top_k)
            items: list[Any] = list(getattr(results, "items", []) or [])
            normalized: list[dict[str, Any]] = []
            for item in items:
                score = float(getattr(item, "score", 0.0) or 0.0)
                if score < min_score:
                    continue
                node = getattr(item, "node", None)
                element_id = getattr(node, "element_id", None)
                normalized.append(
                    {
                        "element_id": element_id,
                        "content": str(getattr(item, "content", "")),
                        "score": score,
                    }
                )
            return normalized
        except Exception as e:
            logger.error(f"混合检索失败: {e}")
            return []

    @classmethod
    def search_nodes(
        cls,
        query: str,
        top_k: int = 10,
        min_score: float = 0.8,
        node_types: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        """
        通用 Knowledge 节点检索（同步方法）

        先做混合检索得到 element_id+score，再批量取上下文返回。

        参数：
        - query: 检索查询
        - top_k: 召回数量
        - min_score: 最低相关性阈值
        - node_types: 节点类型过滤（如 ["Table", "Column", "ValueDomain"]）
        """
        results = cls.hybrid_search(
            query=query,
            top_k=top_k,
            vector_index="kg_unified_vector_index",
            fulltext_index="kg_unified_fulltext_index",
            min_score=min_score,
        )
        if not results:
            return []

        score_map = {
            r.get("element_id"): float(r.get("score", 0) or 0)
            for r in results
            if r.get("element_id")
        }
        element_ids: list[str] = []
        for r in results:
            eid = r.get("element_id")
            if isinstance(eid, str) and eid:
                element_ids.append(eid)
        context = cls.get_nodes_context(element_ids)

        for item in context:
            eid = item.get("element_id")
            item["score"] = score_map.get(eid, 0.0)

        context.sort(key=lambda x: x.get("score", 0), reverse=True)

        # 按节点类型过滤
        if node_types:
            type_set = set(node_types)
            context = [item for item in context if type_set & set(item.get("labels") or [])]

        return context

    # -------------------------------------------------------------------------
