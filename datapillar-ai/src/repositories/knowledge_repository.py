"""
知识图谱数据访问层（Knowledge Repository）
负责所有 Neo4j 知识图谱的查询操作
"""

from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)

from src.config.connection import Neo4jClient, AsyncNeo4jClient, convert_neo4j_types
from src.config import settings


class KnowledgeRepository:
    """知识图谱数据访问（Neo4j）"""

    # 按索引名缓存 VectorRetriever
    _vector_retrievers: Dict[str, Any] = {}

    @classmethod
    def _get_vector_retriever(cls, index_name: str = "kg_unified_vector_index"):
        """懒加载向量检索器（按索引名缓存）"""
        if index_name not in cls._vector_retrievers:
            from neo4j_graphrag.retrievers import VectorRetriever
            from src.integrations.embeddings import UnifiedEmbedder

            try:
                cls._vector_retrievers[index_name] = VectorRetriever(
                    driver=Neo4jClient.get_driver(),
                    index_name=index_name,
                    embedder=UnifiedEmbedder(),
                    return_properties=["name", "displayName", "description"]
                )
                logger.info(f"VectorRetriever[{index_name}] 初始化成功")
            except Exception as e:
                logger.warning(f"VectorRetriever[{index_name}] 初始化失败: {e}")
                return None
        return cls._vector_retrievers.get(index_name)

    @classmethod
    def vector_search(
        cls,
        query: str,
        top_k: int = 10,
        index_name: str = "kg_unified_vector_index"
    ) -> List[Dict[str, Any]]:
        """
        统一向量检索接口

        Args:
            query: 搜索文本
            top_k: 返回数量
            index_name: 向量索引名称

        Returns:
            包含 element_id、content、score 的结果列表
        """
        retriever = cls._get_vector_retriever(index_name)
        if not retriever:
            return []

        try:
            results = retriever.search(query_text=query, top_k=top_k)
            return [
                {
                    "element_id": item.node.element_id,
                    "content": item.content,
                    "score": item.score
                }
                for item in results.items
            ] if results.items else []
        except Exception as e:
            logger.error(f"向量检索失败[{index_name}]: {e}")
            return []

    @classmethod
    def hybrid_search(
        cls,
        query: str,
        top_k: int = 10,
        vector_index: str = "kg_unified_vector_index",
        fulltext_index: str = "kg_unified_fulltext_index"
    ) -> List[Dict[str, Any]]:
        """
        混合检索（向量 + 全文）

        Args:
            query: 搜索文本
            top_k: 返回数量
            vector_index: 向量索引名称
            fulltext_index: 全文索引名称

        Returns:
            包含 element_id、content、score 的结果列表
        """
        from neo4j_graphrag.retrievers import HybridRetriever
        from src.integrations.embeddings import UnifiedEmbedder

        try:
            retriever = HybridRetriever(
                driver=Neo4jClient.get_driver(),
                vector_index_name=vector_index,
                fulltext_index_name=fulltext_index,
                embedder=UnifiedEmbedder(),
                return_properties=["name", "displayName", "description"]
            )
            results = retriever.search(query_text=query, top_k=top_k)
            return [
                {
                    "element_id": item.node.element_id if hasattr(item, 'node') else None,
                    "content": item.content,
                    "score": item.score
                }
                for item in results.items
            ] if results.items else []
        except Exception as e:
            logger.error(f"混合检索失败: {e}")
            return []

    @staticmethod
    def get_initial_graph(limit: int = 50) -> List[Dict[str, Any]]:
        """
        获取初始图数据

        Args:
            limit: 节点数量限制

        Returns:
            查询结果列表
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
                result = session.run(query, {"limit": limit})
                return [convert_neo4j_types(record.data()) for record in result]
        except Exception as e:
            logger.error(f"获取初始图数据失败: {e}")
            return []

    @classmethod
    async def search_tables_with_context(cls, table_ids: List[str]) -> List[Dict[str, Any]]:
        """
        基于表 ID 列表，获取表详情及业务上下文

        Args:
            table_ids: 表的 element_id 列表

        Returns:
            包含表详情、列信息、下游血缘、业务层级的查询结果
        """
        cypher = """
        UNWIND $table_ids AS table_id
        MATCH (table:Table)
        WHERE elementId(table) = table_id

        OPTIONAL MATCH (table)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (table)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)
        MATCH (table)<-[:CONTAINS]-(sch:Schema)<-[:CONTAINS]-(subj:Subject)<-[:CONTAINS]-(cat:Catalog)<-[:CONTAINS]-(dom:Domain)

        WITH table, sch, subj, cat, dom,
             collect(DISTINCT {
                 name: col.name,
                 displayName: col.displayName,
                 dataType: col.dataType,
                 description: col.description
             }) as columns,
             collect(DISTINCT downstream.name) as downstream_tables

        RETURN
            elementId(table) as table_id,
            table.name as table_name,
            table.displayName as table_display_name,
            table.description as table_description,
            columns,
            downstream_tables,
            sch.layer as schema_layer,
            sch.displayName as schema_name,
            subj.displayName as subject_name,
            cat.displayName as catalog_name,
            dom.displayName as domain_name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_ids": table_ids})
                records = await result.data()
                return [convert_neo4j_types(record) for record in records]
        except Exception as e:
            logger.error(f"搜索表上下文失败: {e}")
            return []

    @classmethod
    async def get_table_lineage_detail(cls, source_table: str, target_table: str) -> Optional[Dict[str, Any]]:
        """
        获取源表到目标表的列级血缘

        Args:
            source_table: 源表名
            target_table: 目标表名

        Returns:
            血缘详情字典
        """
        cypher = """
        MATCH (source:Table {name: $source_table})
        MATCH (target:Table {name: $target_table})

        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(source_col:Column)
        OPTIONAL MATCH (target)-[:HAS_COLUMN]->(target_col:Column)
        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(sc:Column)-[:LINEAGE_TO]->(tc:Column)<-[:HAS_COLUMN]-(target)

        RETURN
            source.name as source_table_name,
            source.displayName as source_display_name,
            source.description as source_description,
            collect(DISTINCT {
                name: source_col.name,
                displayName: source_col.displayName,
                dataType: source_col.dataType,
                description: source_col.description
            }) as source_columns,
            target.name as target_table_name,
            target.displayName as target_display_name,
            target.description as target_description,
            collect(DISTINCT {
                name: target_col.name,
                displayName: target_col.displayName,
                dataType: target_col.dataType,
                description: target_col.description
            }) as target_columns,
            collect(DISTINCT {
                source_column: sc.name,
                target_column: tc.name,
                transformation_type: "direct"
            }) as column_lineage
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {
                    "source_table": source_table,
                    "target_table": target_table
                })
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表血缘详情失败: {e}")
            return None

    @classmethod
    async def get_table_detail(cls, table_name: str) -> Optional[Dict[str, Any]]:
        """
        获取单表详情及下游表

        Args:
            table_name: 表名

        Returns:
            表详情字典
        """
        cypher = """
        MATCH (source:Table {name: $table_name})

        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (source)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)

        WITH source,
             collect(DISTINCT {
                 name: col.name,
                 displayName: col.displayName,
                 dataType: col.dataType,
                 description: col.description
             }) as columns,
             collect(DISTINCT downstream.name) as downstream_tables

        RETURN
            source.name as table_name,
            source.displayName as display_name,
            source.description as description,
            columns,
            downstream_tables
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_name": table_name})
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"获取表详情失败: {e}")
            return None
