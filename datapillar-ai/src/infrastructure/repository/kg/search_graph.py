"""
Neo4j 图可视化服务

职责：提供知识图谱可视化相关的查询功能
- get_initial_graph: 获取初始图数据
- get_graph: 基于 elementId 扩展子图
"""

from __future__ import annotations

import logging
from typing import Any

from src.infrastructure.database import Neo4jClient
from src.infrastructure.database.cypher import run_cypher
from src.infrastructure.database.neo4j import convert_neo4j_types
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jGraphSearch:
    """Neo4j 图可视化服务"""

    @classmethod
    def get_initial_graph(cls, limit: int = 50) -> list[dict[str, Any]]:
        """
        获取初始图数据

        参数：
        - limit: 节点数量限制

        返回：
        - [{nodes: [...], relationships: [...]}]
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
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, query, {"limit": limit})
                return [convert_neo4j_types(record.data()) for record in result]
        except Exception as e:
            logger.error(f"获取初始图数据失败: {e}")
            return []

    @classmethod
    def get_graph(cls, node_ids: list[str]) -> dict[str, list]:
        """
        基于业务 ID 批量扩展子图

        用途：前端高亮/搜索命中后，拉取命中节点及其一跳关系子图

        参数：
        - node_ids: 节点业务 ID 列表（node.id 属性）

        返回：
        - {nodes: [...], relationships: [...]}
        """
        if not node_ids:
            return {"nodes": [], "relationships": []}

        query = """
        UNWIND $node_ids AS nid
        MATCH (n:Knowledge) WHERE n.id = nid
        OPTIONAL MATCH (n)-[r]-(m)
        WITH collect(DISTINCT n) + collect(DISTINCT m) AS all_nodes, collect(DISTINCT r) AS rels
        RETURN
            [n IN all_nodes WHERE n IS NOT NULL | {id: id(n), type: labels(n)[0], properties: properties(n)}] AS nodes,
            [r IN rels WHERE r IS NOT NULL | {id: id(r), start: id(startNode(r)), end: id(endNode(r)), type: type(r), properties: properties(r)}] AS relationships
        """

        try:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = run_cypher(session, query, {"node_ids": node_ids})
                record = result.single()
                if not record:
                    return {"nodes": [], "relationships": []}
                return convert_neo4j_types(record.data())
        except Exception as e:
            logger.error(f"扩展图数据失败: {e}")
            return {"nodes": [], "relationships": []}
