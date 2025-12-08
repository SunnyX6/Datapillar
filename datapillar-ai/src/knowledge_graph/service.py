# -*- coding: utf-8 -*-
"""
知识图谱服务层

提供:
- 初始图数据加载
- 自然语言搜索 (向量/全文/混合)
"""

from typing import List, Dict, Any, AsyncGenerator
import logging

logger = logging.getLogger(__name__)

from neo4j_graphrag.retrievers import VectorRetriever, HybridRetriever

from src.core.database import Neo4jClient
from src.knowledge_graph.schemas import GraphNode, GraphRelationship, GraphData, KGEventType, KGStreamEvent, get_node_level
from src.knowledge_graph.utils import msgpack_encode
from src.integrations.embeddings import UnifiedEmbedder

BATCH_SIZE = 200


class KnowledgeGraphService:
    """知识图谱服务"""

    def __init__(self, neo4j_client: Neo4jClient):
        self.neo4j_client = neo4j_client

        # 创建统一 Embedder
        try:
            self.embedder = UnifiedEmbedder()
            logger.info("KnowledgeGraphService: Embedder 初始化成功")
        except Exception as e:
            logger.warning(f"KnowledgeGraphService: Embedder 初始化失败，向量/混合检索将不可用: {e}")
            self.embedder = None

    def get_initial_graph(self, limit: int = 50) -> GraphData:
        """
        获取初始图数据 (全图)

        Args:
            limit: 节点数量限制

        Returns:
            GraphData: 包含节点和关系的图数据
        """
        query = """
        MATCH (n)
        WITH collect(n)[0..$limit] AS nodes
        UNWIND nodes AS n
        OPTIONAL MATCH (n)-[r]-(m)
        WHERE m IN nodes
        WITH nodes, collect(DISTINCT r) AS rels
        RETURN
            [n IN nodes | {id: id(n), type: labels(n)[0], properties: properties(n)}] AS nodes,
            [r IN rels WHERE r IS NOT NULL | {id: id(r), start: id(startNode(r)), end: id(endNode(r)), type: type(r), properties: properties(r)}] AS relationships
        """
        
        try:
            records = self.neo4j_client.execute_query(query, {"limit": limit})
            if not records:
                return GraphData()

            record = records[0]
            # 创建节点时根据类型计算 level
            nodes = []
            for n in (record.get("nodes") or []):
                if n:
                    node_type = n.get("type", "")
                    nodes.append(GraphNode(
                        id=n["id"],
                        type=node_type,
                        level=get_node_level(node_type),
                        properties=n.get("properties", {})
                    ))

            relationships = [GraphRelationship(**r) for r in (record.get("relationships") or []) if r]

            logger.info(f"加载初始图数据: {len(nodes)} 节点, {len(relationships)} 关系")
            return GraphData(nodes=nodes, relationships=relationships)
            
        except Exception as e:
            logger.error(f"加载初始图数据失败: {e}")
            raise

    def search_by_text(self, query: str, top_k: int = 10, search_type: str = "hybrid") -> Dict[str, Any]:
        """
        自然语言搜索

        Args:
            query: 搜索文本
            top_k: 返回数量
            search_type: 搜索类型 (vector/fulltext/hybrid)

        Returns:
            搜索结果（包含节点、关系、高亮节点ID）
        """
        logger.info(f"搜索知识图谱: query='{query}', top_k={top_k}, type={search_type}")

        try:
            if search_type == "vector":
                # 向量检索
                if not self.embedder:
                    raise ValueError("Embedder 未初始化，无法使用向量检索")

                retriever = VectorRetriever(
                    driver=self.neo4j_client.driver,
                    index_name="kg_unified_vector_index",
                    embedder=self.embedder,
                    return_properties=["name", "displayName", "description"]
                )
                results = retriever.search(query_text=query, top_k=top_k)

            elif search_type == "fulltext":
                # 全文检索（使用 Cypher 查询）
                results = self._fulltext_search(query, top_k)

            elif search_type == "hybrid":
                # 混合检索
                if not self.embedder:
                    raise ValueError("Embedder 未初始化，无法使用混合检索")

                retriever = HybridRetriever(
                    driver=self.neo4j_client.driver,
                    vector_index_name="kg_unified_vector_index",
                    fulltext_index_name="kg_unified_fulltext_index",
                    embedder=self.embedder,
                    return_properties=["name", "displayName", "description"]
                )
                results = retriever.search(query_text=query, top_k=top_k)

            else:
                raise ValueError(f"不支持的搜索类型: {search_type}")

            # 提取匹配节点的属性，用于后续查询
            matched_nodes_props = []
            if hasattr(results, 'items') and results.items:
                import ast
                for item in results.items:
                    if hasattr(item, 'content') and isinstance(item.content, str):
                        try:
                            # content 是字符串形式的字典，解析它
                            props = ast.literal_eval(item.content)
                            if isinstance(props, dict) and 'name' in props:
                                matched_nodes_props.append(props)
                        except Exception as e:
                            logger.warning(f"解析 content 失败: {e}, content={item.content}")

            logger.info(f"解析到 {len(matched_nodes_props)} 个匹配节点属性")

            # 根据节点属性查询 element_id
            matched_node_ids = []
            if matched_nodes_props:
                names = [p['name'] for p in matched_nodes_props]
                query = """
                UNWIND $names AS name
                MATCH (n {name: name})
                RETURN collect(elementId(n)) AS ids
                """
                records = self.neo4j_client.execute_query(query, {"names": names})
                if records and records[0]:
                    matched_node_ids = records[0].get("ids", [])
                    logger.info(f"查询到 {len(matched_node_ids)} 个节点ID")

            # 扩展图数据：获取匹配节点的相关关系
            expanded_result = self._expand_graph_data(matched_node_ids)

            logger.info(f"搜索完成: 找到 {len(expanded_result['nodes'])} 个节点, {len(expanded_result['relationships'])} 个关系")

            return {
                "nodes": expanded_result["nodes"],
                "relationships": expanded_result["relationships"],
                "highlight_node_ids": matched_node_ids
            }

        except Exception as e:
            logger.error(f"搜索失败: {e}", exc_info=True)
            return {"nodes": [], "relationships": [], "highlight_node_ids": []}

    def _fulltext_search(self, query: str, top_k: int):
        """
        全文搜索（使用 Neo4j 全文索引）

        Args:
            query: 搜索文本
            top_k: 返回数量

        Returns:
            模拟 retriever.search 的返回结果
        """
        cypher = """
        CALL db.index.fulltext.queryNodes('kg_unified_fulltext_index', $query)
        YIELD node, score
        WITH node, score
        ORDER BY score DESC
        LIMIT $top_k
        RETURN collect({
            element_id: elementId(node),
            type: labels(node)[0],
            properties: properties(node),
            score: score
        }) AS items
        """

        records = self.neo4j_client.execute_query(cypher, {"query": query, "top_k": top_k})

        if not records or not records[0].get("items"):
            # 返回空结果对象（模拟 retriever）
            class EmptyResult:
                items = []
            return EmptyResult()

        # 构建类似 retriever 的返回结构
        class FulltextResult:
            def __init__(self, items):
                self.items = []
                for item in items:
                    node_obj = type('Node', (), {
                        'element_id': item['element_id'],
                        'type': item['type'],
                        'properties': item['properties']
                    })()
                    result_item = type('Item', (), {
                        'node': node_obj,
                        'score': item['score']
                    })()
                    self.items.append(result_item)

        return FulltextResult(records[0]["items"])

    def _expand_graph_data(self, node_ids: List[str]) -> Dict[str, List]:
        """
        扩展图数据：获取匹配节点及其相关关系

        Args:
            node_ids: 匹配节点的 element_id 列表

        Returns:
            包含节点和关系的字典
        """
        if not node_ids:
            return {"nodes": [], "relationships": []}

        cypher = """
        UNWIND $node_ids AS node_id
        MATCH (n)
        WHERE elementId(n) = node_id

        // 获取节点的直接关系（限制深度避免爆炸）
        OPTIONAL MATCH (n)-[r]-(related)

        // 返回节点和关系
        RETURN
            collect(DISTINCT {
                id: id(n),
                type: labels(n)[0],
                properties: properties(n)
            }) AS matched_nodes,
            collect(DISTINCT {
                id: id(related),
                type: labels(related)[0],
                properties: properties(related)
            }) AS related_nodes,
            collect(DISTINCT CASE WHEN r IS NOT NULL THEN {
                id: id(r),
                start: id(startNode(r)),
                end: id(endNode(r)),
                type: type(r),
                properties: properties(r)
            } END) AS relationships
        """

        records = self.neo4j_client.execute_query(cypher, {"node_ids": node_ids})

        if not records:
            return {"nodes": [], "relationships": []}

        record = records[0]

        # 合并匹配节点和相关节点，添加 level
        all_nodes = []
        seen_node_ids = set()

        for node in (record.get("matched_nodes") or []):
            if node and node.get("id") not in seen_node_ids:
                node_type = node.get("type", "")
                all_nodes.append(GraphNode(
                    id=node["id"],
                    type=node_type,
                    level=get_node_level(node_type),
                    properties=node.get("properties", {})
                ))
                seen_node_ids.add(node["id"])

        for node in (record.get("related_nodes") or []):
            if node and node.get("id") and node.get("id") not in seen_node_ids:
                node_type = node.get("type", "")
                all_nodes.append(GraphNode(
                    id=node["id"],
                    type=node_type,
                    level=get_node_level(node_type),
                    properties=node.get("properties", {})
                ))
                seen_node_ids.add(node["id"])

        # 处理关系
        relationships = [
            GraphRelationship(**r)
            for r in (record.get("relationships") or [])
            if r
        ]

        return {
            "nodes": [node.model_dump() for node in all_nodes],
            "relationships": [rel.model_dump() for rel in relationships]
        }

    async def stream_initial_graph(self, limit: int = 500) -> AsyncGenerator[KGStreamEvent, None]:
        """流式获取初始图数据"""
        try:
            graph_data = self.get_initial_graph(limit=limit)
            nodes = [n.model_dump() for n in graph_data.nodes]
            rels = [r.model_dump() for r in graph_data.relationships]
            total_nodes = len(nodes)
            total_rels = len(rels)

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_START,
                data=msgpack_encode({"total_nodes": total_nodes, "total_rels": total_rels}),
            )

            for i in range(0, total_nodes, BATCH_SIZE):
                batch = nodes[i:i + BATCH_SIZE]
                yield KGStreamEvent(
                    event_type=KGEventType.NODES_BATCH,
                    data=msgpack_encode(batch),
                    total=total_nodes,
                    current=min(i + BATCH_SIZE, total_nodes),
                )

            for i in range(0, total_rels, BATCH_SIZE):
                batch = rels[i:i + BATCH_SIZE]
                yield KGStreamEvent(
                    event_type=KGEventType.RELS_BATCH,
                    data=msgpack_encode(batch),
                    total=total_rels,
                    current=min(i + BATCH_SIZE, total_rels),
                )

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_END,
                data=msgpack_encode({"success": True}),
            )
        except Exception as e:
            logger.error(f"流式获取初始图数据失败: {e}")
            yield KGStreamEvent(
                event_type=KGEventType.ERROR,
                data=msgpack_encode({"error": str(e)}),
            )

    async def stream_search(self, query: str, top_k: int = 10, search_type: str = "hybrid") -> AsyncGenerator[KGStreamEvent, None]:
        """流式搜索知识图谱"""
        try:
            yield KGStreamEvent(
                event_type=KGEventType.STREAM_START,
                data=msgpack_encode({"query": query}),
            )

            result = self.search_by_text(query=query, top_k=top_k, search_type=search_type)

            yield KGStreamEvent(
                event_type=KGEventType.SEARCH_RESULT,
                data=msgpack_encode(result),
            )

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_END,
                data=msgpack_encode({"success": True}),
            )
        except Exception as e:
            logger.error(f"流式搜索失败: {e}")
            yield KGStreamEvent(
                event_type=KGEventType.ERROR,
                data=msgpack_encode({"error": str(e)}),
            )
