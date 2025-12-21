# -*- coding: utf-8 -*-
"""
知识图谱服务层（使用 KnowledgeRepository）

提供:
- 初始图数据加载
- 自然语言搜索 (向量/全文/混合)
"""

from typing import List, Dict, Any, AsyncGenerator
import logging

logger = logging.getLogger(__name__)

from src.infrastructure.repository import KnowledgeRepository
from src.modules.knowledge.schemas import GraphNode, GraphRelationship, GraphData, KGEventType, KGStreamEvent, get_node_level
from src.modules.knowledge.utils import msgpack_encode

BATCH_SIZE = 200


class KnowledgeGraphService:
    """知识图谱服务（使用 Repository 模式）"""

    def get_initial_graph(self, limit: int = 50) -> GraphData:
        """
        获取初始图数据 (全图)

        Args:
            limit: 节点数量限制

        Returns:
            GraphData: 包含节点和关系的图数据
        """
        try:
            records = KnowledgeRepository.get_initial_graph(limit)
            if not records:
                return GraphData()

            record = records[0]
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
            # 通过 Repository 执行检索
            if search_type == "vector":
                results = KnowledgeRepository.vector_search(query, top_k, index_name="kg_unified_vector_index")
            elif search_type == "hybrid":
                results = KnowledgeRepository.hybrid_search(query, top_k)
            else:
                raise ValueError(f"不支持的搜索类型: {search_type}，仅支持 vector/hybrid")

            # 直接从检索结果提取 element_id
            matched_node_ids = [item["element_id"] for item in results if item.get("element_id")]

            # 扩展图数据
            expanded_result = self._expand_graph_data(matched_node_ids)

            logger.info(f"搜索完成: 找到 {len(expanded_result['nodes'])} 个节点")

            return {
                "nodes": expanded_result["nodes"],
                "relationships": expanded_result["relationships"],
                "highlight_node_ids": matched_node_ids
            }

        except Exception as e:
            logger.error(f"搜索失败: {e}", exc_info=True)
            return {"nodes": [], "relationships": [], "highlight_node_ids": []}

    def _expand_graph_data(self, node_ids: List[str]) -> Dict[str, List]:
        """扩展图数据：获取匹配节点及其相关关系"""
        if not node_ids:
            return {"nodes": [], "relationships": []}

        from src.infrastructure.database import Neo4jClient, convert_neo4j_types
        from src.shared.config import settings

        cypher = """
        UNWIND $element_ids AS eid
        MATCH (n) WHERE elementId(n) = eid
        OPTIONAL MATCH (n)-[r]-(m)
        WITH collect(DISTINCT n) + collect(DISTINCT m) AS all_nodes, collect(DISTINCT r) AS rels
        RETURN
            [n IN all_nodes WHERE n IS NOT NULL | {id: id(n), type: labels(n)[0], properties: properties(n)}] AS nodes,
            [r IN rels WHERE r IS NOT NULL | {id: id(r), start: id(startNode(r)), end: id(endNode(r)), type: type(r), properties: properties(r)}] AS relationships
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = session.run(cypher, {"element_ids": node_ids})
                record = result.single()
                if not record:
                    return {"nodes": [], "relationships": []}

                data = convert_neo4j_types(record.data())

                nodes = [
                    GraphNode(
                        id=n["id"],
                        type=n.get("type", ""),
                        level=get_node_level(n.get("type", "")),
                        properties=n.get("properties", {})
                    ).model_dump()
                    for n in (data.get("nodes") or []) if n
                ]

                relationships = [
                    GraphRelationship(**r).model_dump()
                    for r in (data.get("relationships") or []) if r
                ]

                return {"nodes": nodes, "relationships": relationships}

        except Exception as e:
            logger.error(f"扩展图数据失败: {e}")
            return {"nodes": [], "relationships": []}

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
