"""
知识图谱数据访问层（Knowledge Repository）
负责所有 Neo4j 知识图谱的查询操作
"""

import hashlib
import logging
from datetime import datetime
from typing import Any

from src.infrastructure.database import AsyncNeo4jClient, Neo4jClient, convert_neo4j_types
from src.shared.config import settings

logger = logging.getLogger(__name__)


class KnowledgeRepository:
    """知识图谱数据访问（Neo4j）"""

    # 按索引名缓存 VectorRetriever
    _vector_retrievers: dict[str, Any] = {}

    # ==================== 全局上下文加载（返回原始 dict）====================

    @classmethod
    async def load_catalog_hierarchy(cls) -> list[dict[str, Any]]:
        """
        加载 Catalog -> Schema -> Table 层级结构

        Returns:
            原始 dict 列表，结构：
            [{
                "name": "catalog_name",
                "metalake": "metalake_name",
                "schemas": [{
                    "name": "schema_name",
                    "description": "...",
                    "tables": [{
                        "name": "table_name",
                        "schema_name": "schema_name",
                        "catalog": "catalog_name",
                        "tags": ["layer:ODS"],
                        "description": "...",
                        "column_count": 10
                    }]
                }]
            }]
        """
        cypher = """
        MATCH (cat:Catalog)-[:HAS_SCHEMA]->(sch:Schema)-[:HAS_TABLE]->(t:Table)
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        WITH cat, sch, t, count(col) as column_count
        WITH cat, sch, collect({
            name: t.name,
            schema_name: sch.name,
            catalog: cat.name,
            tags: coalesce(t.tags, []),
            description: t.description,
            column_count: column_count
        }) as tables
        WITH cat, collect({
            name: sch.name,
            catalog: cat.name,
            description: sch.description,
            tables: tables
        }) as schemas
        RETURN
            cat.name as name,
            cat.metalake as metalake,
            schemas
        ORDER BY cat.name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 Catalog 层级结构失败: {e}")
            return []

    @classmethod
    async def load_table_lineage(cls) -> list[dict[str, Any]]:
        """
        加载表级血缘图

        Returns:
            原始 dict 列表，结构：
            [{"source_table": "schema.table", "target_table": "schema.table", "sql_id": "..."}]
        """
        cypher = """
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target:Table)
        WITH source, target, sql
        MATCH (source)<-[:HAS_TABLE]-(source_schema:Schema)
        MATCH (target)<-[:HAS_TABLE]-(target_schema:Schema)
        RETURN DISTINCT
            source_schema.name + '.' + source.name as source_table,
            target_schema.name + '.' + target.name as target_table,
            sql.id as sql_id
        ORDER BY source_table, target_table
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载表级血缘失败: {e}")
            return []

    @classmethod
    async def load_sql_patterns(cls, limit: int = 10) -> list[dict[str, Any]]:
        """
        加载热门 SQL 模式

        Returns:
            原始 dict 列表，结构：
            [{"pattern": "...", "tables": ["t1", "t2"], "frequency": 10}]
        """
        cypher = """
        MATCH (t:Table)-[:INPUT_OF]->(sql:SQL)
        WITH sql, collect(DISTINCT t.name) as tables
        RETURN
            coalesce(sql.summary, sql.name, 'unknown') as pattern,
            tables,
            coalesce(sql.useCount, 1) as frequency
        ORDER BY frequency DESC
        LIMIT $limit
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, limit=limit)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"加载 SQL 模式失败: {e}")
            return []

    # ==================== 按需查询工具方法 ====================

    @classmethod
    async def get_table_columns(cls, table_name: str) -> list[dict[str, Any]]:
        """
        获取表的所有列详情

        Args:
            table_name: 表名（支持 schema.table 或 table 格式）

        Returns:
            列信息列表
        """
        # 解析表名
        parts = table_name.split(".", 1)
        if len(parts) == 2:
            schema_name, table_only = parts
            where_clause = "t.name = $table_name AND sch.name = $schema_name"
            params = {"table_name": table_only, "schema_name": schema_name}
        else:
            where_clause = "t.name = $table_name"
            params = {"table_name": table_name}

        cypher = f"""
        MATCH (sch:Schema)-[:HAS_TABLE]->(t:Table)-[:HAS_COLUMN]->(col:Column)
        WHERE {where_clause}
        RETURN
            col.name as name,
            col.dataType as data_type,
            col.description as description,
            col.nullable as nullable,
            coalesce(col.tags, []) as tags
        ORDER BY col.name
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, params)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取表列详情失败: {e}")
            return []

    @classmethod
    async def get_column_lineage(
        cls, source_table: str, target_table: str
    ) -> list[dict[str, Any]]:
        """
        获取列级血缘

        优先使用 LINEAGE_TO 关系查询显式的列级映射，
        如果没有则降级为同名列匹配（兼容旧数据）。

        Args:
            source_table: 源表名（schema.table 格式）
            target_table: 目标表名（schema.table 格式）

        Returns:
            列级血缘映射列表
        """
        # 解析表名
        source_parts = source_table.split(".", 1)
        target_parts = target_table.split(".", 1)

        if len(source_parts) < 2 or len(target_parts) < 2:
            logger.warning("列级血缘查询需要 schema.table 格式的表名")
            return []

        source_schema, source_name = source_parts
        target_schema, target_name = target_parts

        # 优先查询：使用 DERIVES_FROM 关系的显式列级血缘
        cypher_derives_from = """
        MATCH (source:Table {name: $source_name})<-[:HAS_TABLE]-(src_sch:Schema {name: $source_schema})
        MATCH (target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})
        MATCH (source)-[:HAS_COLUMN]->(src_col:Column)<-[lineage:DERIVES_FROM]-(tgt_col:Column)<-[:HAS_COLUMN]-(target)
        OPTIONAL MATCH (source)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
        RETURN
            sql.id as sql_id,
            sql.content as sql_content,
            collect(DISTINCT {
                source_column: src_col.name,
                target_column: tgt_col.name,
                transformation: coalesce(lineage.transformationType, 'direct')
            }) as column_mappings
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                # 先尝试 DERIVES_FROM 关系
                result = await session.run(cypher_derives_from, {
                    "source_name": source_name,
                    "source_schema": source_schema,
                    "target_name": target_name,
                    "target_schema": target_schema,
                })
                records = await result.data()

                # 检查是否有有效的列映射
                has_valid_lineage = False
                for record in records:
                    mappings = record.get("column_mappings", [])
                    if mappings and any(m.get("source_column") for m in mappings):
                        has_valid_lineage = True
                        break

                if has_valid_lineage:
                    return [convert_neo4j_types(r) for r in records]

                # 降级：同名列匹配（兼容旧数据）
                logger.info(f"未找到 DERIVES_FROM 关系，降级为同名列匹配: {source_table} → {target_table}")
                cypher_same_name = """
                MATCH (source:Table {name: $source_name})<-[:HAS_TABLE]-(src_sch:Schema {name: $source_schema})
                MATCH (target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})
                MATCH (source)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
                OPTIONAL MATCH (source)-[:HAS_COLUMN]->(src_col:Column)
                OPTIONAL MATCH (target)-[:HAS_COLUMN]->(tgt_col:Column)
                WHERE src_col.name = tgt_col.name
                RETURN
                    sql.id as sql_id,
                    sql.content as sql_content,
                    collect(DISTINCT {
                        source_column: src_col.name,
                        target_column: tgt_col.name,
                        transformation: 'direct'
                    }) as column_mappings
                """
                result = await session.run(cypher_same_name, {
                    "source_name": source_name,
                    "source_schema": source_schema,
                    "target_name": target_name,
                    "target_schema": target_schema,
                })
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]

        except Exception as e:
            logger.error(f"获取列级血缘失败: {e}")
            return []

    @classmethod
    async def search_sql_by_tables(
        cls, tables: list[str], limit: int = 5
    ) -> list[dict[str, Any]]:
        """
        根据表名搜索相关 SQL

        Args:
            tables: 表名列表
            limit: 返回数量限制

        Returns:
            相关 SQL 列表
        """
        cypher = """
        MATCH (t:Table)-[:INPUT_OF]->(sql:SQL)
        WHERE t.name IN $tables
        WITH sql, collect(DISTINCT t.name) as related_tables
        OPTIONAL MATCH (sql)-[:OUTPUT_TO]->(out:Table)
        WITH sql, related_tables + collect(DISTINCT out.name) as related_tables
        RETURN
            sql.id as sql_id,
            sql.name as name,
            sql.content as content,
            sql.summary as summary,
            coalesce(sql.useCount, 0) as use_count,
            coalesce(sql.confidence, 0.5) as confidence,
            related_tables
        ORDER BY sql.confidence DESC, sql.useCount DESC
        LIMIT $limit
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"tables": tables, "limit": limit})
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"搜索相关 SQL 失败: {e}")
            return []

    @classmethod
    async def get_sql_by_lineage(
        cls, source_tables: list[str], target_table: str
    ) -> dict[str, Any] | None:
        """
        根据血缘关系精准查找 SQL

        根据 Table -[:INPUT_OF]-> SQL -[:OUTPUT_TO]-> Table 关系，
        精准定位从源表到目标表的 SQL。

        Args:
            source_tables: 源表名列表
            target_table: 目标表名

        Returns:
            SQL 详情（包含完整 content、JOIN 条件等）
        """
        # 解析目标表名
        target_parts = target_table.split(".", 1)
        if len(target_parts) == 2:
            target_schema, target_name = target_parts
            target_match = "(target:Table {name: $target_name})<-[:HAS_TABLE]-(tgt_sch:Schema {name: $target_schema})"
        else:
            target_name = target_table
            target_schema = None
            target_match = "(target:Table {name: $target_name})"

        # 解析源表名（取第一个作为主要匹配）
        source_names = []
        for src in source_tables:
            parts = src.split(".", 1)
            source_names.append(parts[1] if len(parts) == 2 else src)

        cypher = f"""
        MATCH {target_match}
        MATCH (source:Table)-[:INPUT_OF]->(sql:SQL)-[:OUTPUT_TO]->(target)
        WHERE source.name IN $source_names
        WITH sql, target, collect(DISTINCT source.name) as source_tables
        RETURN
            sql.id as sql_id,
            sql.name as name,
            sql.content as content,
            sql.summary as summary,
            sql.engine as engine,
            sql.dialect as dialect,
            coalesce(sql.useCount, 0) as use_count,
            coalesce(sql.confidence, 0.5) as confidence,
            source_tables,
            target.name as target_table
        ORDER BY confidence DESC, use_count DESC
        LIMIT 1
        """

        params = {
            "source_names": source_names,
            "target_name": target_name,
        }
        if target_schema:
            params["target_schema"] = target_schema

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, params)
                record = await result.single()
                return convert_neo4j_types(record.data()) if record else None
        except Exception as e:
            logger.error(f"根据血缘查找 SQL 失败: {e}")
            return None

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
    ) -> list[dict[str, Any]]:
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
                    "element_id": item.metadata.get("id") if item.metadata else None,
                    "content": item.content,
                    "score": item.metadata.get("score") if item.metadata else 0,
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
    ) -> list[dict[str, Any]]:
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
    def get_initial_graph(limit: int = 50) -> list[dict[str, Any]]:
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
    async def search_tables_with_context(cls, table_ids: list[str]) -> list[dict[str, Any]]:
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
    async def get_table_lineage_detail(cls, source_table: str, target_table: str) -> dict[str, Any] | None:
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
        OPTIONAL MATCH (source)-[:HAS_COLUMN]->(sc:Column)<-[:DERIVES_FROM]-(tc:Column)<-[:HAS_COLUMN]-(target)

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
    async def get_table_detail(cls, table_name: str) -> dict[str, Any] | None:
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

    # ==================== 知识写回 ====================

    @classmethod
    async def persist_kg_updates(
        cls,
        updates: list[dict[str, Any]],
        user_id: str,
        session_id: str,
    ) -> int:
        """
        将用户确认过的结构化事实写回 Neo4j（事务性写入）

        使用显式事务确保原子性：要么全部成功，要么全部回滚。
        支持类型: table_role / lineage / col_map / join / reference_sql
        """
        if not updates:
            return 0

        ts = datetime.utcnow().isoformat()
        saved = 0

        driver = await AsyncNeo4jClient.get_driver()
        async with driver.session(database=settings.neo4j_database) as session:
            # 使用显式事务确保原子性
            tx = await session.begin_transaction()
            try:
                for upd in updates:
                    upd_type = upd.get("type")
                    if upd_type == "table_role":
                        await tx.run(
                            """
                            MERGE (t:Table {name:$table})
                            MERGE (w:WorkflowSession {session_id:$session_id})
                            SET w.last_seen=$ts, w.user_id=$user_id
                            MERGE (t)-[r:ETL_ROLE {session_id:$session_id}]->(w)
                            SET r.type=$role, r.by_user=$user_id, r.ts=$ts, r.confidence=$confidence
                            """,
                            {
                                "table": upd.get("table"),
                                "role": upd.get("role"),
                                "user_id": user_id,
                                "session_id": session_id,
                                "ts": ts,
                                "confidence": float(upd.get("confidence", 0.5)),
                            },
                        )
                        saved += 1
                    elif upd_type == "lineage":
                        await tx.run(
                            """
                            MATCH (s:Table {name:$source_table})
                            MATCH (t:Table {name:$target_table})
                            MERGE (s)-[r:CONFIRMED_LINEAGE]->(t)
                            SET r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "col_map":
                        await tx.run(
                            """
                            MATCH (s:Table {name:$source_table})-[:HAS_COLUMN]->(sc:Column {name:$source_column})
                            MATCH (t:Table {name:$target_table})-[:HAS_COLUMN]->(tc:Column {name:$target_column})
                            MERGE (sc)-[r:CONFIRMED_MAP]->(tc)
                            SET r.transform=$transform, r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "source_column": upd.get("source_column"),
                                "target_column": upd.get("target_column"),
                                "transform": upd.get("transform", "direct"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "join":
                        await tx.run(
                            """
                            MATCH (l:Table {name:$left})
                            MATCH (r:Table {name:$right})
                            MERGE (l)-[j:JOIN_KEY]->(r)
                            SET j.on=$on, j.confidence=$confidence, j.by_user=$user_id, j.ts=$ts
                            """,
                            {
                                "left": upd.get("left"),
                                "right": upd.get("right"),
                                "on": upd.get("on") or [],
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    else:
                        logger.warning(f"[persist_kg_updates] 忽略未知类型: {upd_type}")

                # 所有操作成功，提交事务
                await tx.commit()
                logger.info(f"[persist_kg_updates] 事务提交成功，写入 {saved} 条记录")

            except Exception as exc:
                # 出错时回滚事务
                await tx.rollback()
                logger.error(f"[persist_kg_updates] 事务回滚: {exc}")
                raise

        return saved

    # ==================== SQL 使用统计 ====================

    @classmethod
    async def increment_sql_use_count(cls, sql_id: str) -> bool:
        """
        增加 SQL 节点的使用次数

        当用户对 AI 生成的结果满意时，给 AI 参考过的历史 SQL 加分，
        使高分 SQL 在未来的检索中排名更靠前。

        Args:
            sql_id: SQL 节点的 ID

        Returns:
            是否更新成功
        """
        cypher = """
        MATCH (s:SQL {id: $sql_id})
        SET s.use_count = coalesce(s.use_count, 0) + 1,
            s.last_used = datetime()
        RETURN s.use_count as use_count
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"sql_id": sql_id})
                record = await result.single()
                if record:
                    logger.info(f"SQL 使用次数更新: {sql_id} -> {record['use_count']}")
                    return True
                return False
        except Exception as e:
            logger.error(f"更新 SQL 使用次数失败: {e}")
            return False
