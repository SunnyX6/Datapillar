"""
知识图谱数据访问层（Knowledge Repository）
负责所有 Neo4j 知识图谱的查询操作
"""

from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)

from src.infrastructure.database import Neo4jClient, AsyncNeo4jClient, convert_neo4j_types
from src.shared.config import settings
import hashlib
from datetime import datetime


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
    async def get_join_hints(cls, table_name: str) -> List[Dict[str, Any]]:
        """
        获取指定表参与的 Join 关系（基于 Join 节点的 JOIN_LEFT / JOIN_RIGHT）
        返回：[{join_id,left_table,left_column,right_table,right_column,join_type}]
        """
        cypher = """
        MATCH (lt:Table {name:$table_name})-[:HAS_COLUMN]->(lc:Column)<-[:JOIN_LEFT]-(j:Join)-[:JOIN_RIGHT]->(rc:Column)<-[:HAS_COLUMN]-(rt:Table)
        RETURN j.id AS join_id, j.joinType AS join_type,
               lt.name AS left_table, lc.name AS left_column,
               rt.name AS right_table, rc.name AS right_column
        UNION
        MATCH (rt:Table {name:$table_name})-[:HAS_COLUMN]->(rc:Column)<-[:JOIN_RIGHT]-(j:Join)-[:JOIN_LEFT]->(lc:Column)<-[:HAS_COLUMN]-(lt:Table)
        RETURN j.id AS join_id, j.joinType AS join_type,
               lt.name AS left_table, lc.name AS left_column,
               rt.name AS right_table, rc.name AS right_column
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_name": table_name})
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取 Join 线索失败: {e}")
            return []

    @classmethod
    async def get_quality_rules(cls, table_name: str) -> List[Dict[str, Any]]:
        """
        获取表的列质量规则（HAS_QUALITY_RULE）
        返回：[{column, rule_name, rule_type, sql_exp, severity, is_required}]
        """
        cypher = """
        MATCH (t:Table {name:$table_name})-[:HAS_COLUMN]->(c:Column)-[r:HAS_QUALITY_RULE]->(q:QualityRule)
        RETURN c.name AS column, q.name AS rule_name, q.ruleType AS rule_type,
               q.sqlExp AS sql_exp, q.severity AS severity, q.isRequired AS is_required
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"table_name": table_name})
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取质量规则失败: {e}")
            return []

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

    # ==================== 知识写回 ====================

    @classmethod
    async def persist_kg_updates(
        cls,
        updates: List[Dict[str, Any]],
        user_id: str,
        session_id: str,
    ) -> int:
        """
        将用户确认过的结构化事实写回 Neo4j
        支持类型: table_role / lineage / col_map / join / reference_sql
        """
        if not updates:
            return 0

        ts = datetime.utcnow().isoformat()
        saved = 0

        driver = await AsyncNeo4jClient.get_driver()
        async with driver.session(database=settings.neo4j_database) as session:
            for upd in updates:
                try:
                    upd_type = upd.get("type")
                    if upd_type == "table_role":
                        await session.run(
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
                        await session.run(
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
                        await session.run(
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
                        await session.run(
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
                    elif upd_type == "reference_sql":
                        sql_text = upd.get("sql") or ""
                        if not sql_text:
                            continue
                        fingerprint = hashlib.sha256(sql_text.encode("utf-8")).hexdigest()
                        params = {
                            "fp": fingerprint,
                            "text": sql_text,
                            "summary": upd.get("summary") or "",
                            "tags": upd.get("tags") or [],
                            "dialect": upd.get("dialect"),
                            "user_id": user_id,
                            "ts": ts,
                        }
                        await session.run(
                            """
                            MERGE (rs:ReferenceSQL {fingerprint:$fp})
                            SET rs.text=$text, rs.summary=$summary, rs.tags=$tags, rs.dialect=$dialect,
                                rs.by_user=$user_id, rs.ts=$ts
                            """,
                            params,
                        )
                        sources = upd.get("sources") or []
                        targets = upd.get("targets") or []
                        if sources:
                            await session.run(
                                """
                                UNWIND $sources AS sname
                                MATCH (s:Table {name:sname})
                                MATCH (rs:ReferenceSQL {fingerprint:$fp})
                                MERGE (rs)-[:READS_FROM]->(s)
                                """,
                                {"sources": sources, "fp": fingerprint},
                            )
                        if targets:
                            await session.run(
                                """
                                UNWIND $targets AS tname
                                MATCH (t:Table {name:tname})
                                MATCH (rs:ReferenceSQL {fingerprint:$fp})
                                MERGE (rs)-[:WRITES_TO]->(t)
                                """,
                                {"targets": targets, "fp": fingerprint},
                            )
                        saved += 1
                    else:
                        logger.warning(f"[persist_kg_updates] 忽略未知类型: {upd_type}")
                except Exception as exc:  # noqa: BLE001
                    logger.error(f"[persist_kg_updates] 写回失败: {exc}, 数据={upd}")
        return saved

    # ==================== 参考 SQL 检索 ====================

    @classmethod
    async def search_reference_sql(
        cls,
        query: str,
        source_tables: Optional[List[str]] = None,
        target_tables: Optional[List[str]] = None,
        tags: Optional[List[str]] = None,
        limit: int = 5,
    ) -> List[Dict[str, Any]]:
        """
        检索历史参考 SQL

        支持：
        1. 语义相似度匹配（基于 summary）
        2. 源表/目标表过滤
        3. 标签过滤

        Args:
            query: 用户查询
            source_tables: 源表列表（可选过滤）
            target_tables: 目标表列表（可选过滤）
            tags: 标签过滤（可选）
            limit: 返回数量

        Returns:
            参考 SQL 列表，按相关性排序
        """
        # 构建 Cypher 查询
        cypher_parts = ["MATCH (rs:ReferenceSQL)"]
        params = {"limit": limit}

        # 源表过滤
        if source_tables:
            cypher_parts.append(
                "MATCH (rs)-[:READS_FROM]->(src:Table) WHERE src.name IN $source_tables"
            )
            params["source_tables"] = source_tables

        # 目标表过滤
        if target_tables:
            cypher_parts.append(
                "MATCH (rs)-[:WRITES_TO]->(tgt:Table) WHERE tgt.name IN $target_tables"
            )
            params["target_tables"] = target_tables

        # 标签过滤
        if tags:
            cypher_parts.append("WHERE any(tag IN rs.tags WHERE tag IN $tags)")
            params["tags"] = tags

        # 获取关联的表信息
        cypher_parts.append("""
        OPTIONAL MATCH (rs)-[:READS_FROM]->(src:Table)
        OPTIONAL MATCH (rs)-[:WRITES_TO]->(tgt:Table)
        WITH rs,
             collect(DISTINCT src.name) as source_tables,
             collect(DISTINCT tgt.name) as target_tables
        RETURN
            rs.fingerprint as fingerprint,
            rs.text as sql_text,
            rs.summary as summary,
            rs.tags as tags,
            rs.dialect as dialect,
            rs.ts as created_at,
            rs.confidence as confidence,
            coalesce(rs.use_count, 0) as use_count,
            source_tables,
            target_tables
        ORDER BY rs.confidence DESC, rs.use_count DESC
        LIMIT $limit
        """)

        cypher = "\n".join(cypher_parts)

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, params)
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"检索参考 SQL 失败: {e}")
            return []

    @classmethod
    async def increment_reference_sql_use_count(cls, fingerprint: str) -> bool:
        """
        增加参考 SQL 的使用次数

        当 DeveloperAgent 参考某个历史 SQL 时调用，
        用于统计哪些 SQL 最常被复用。

        Args:
            fingerprint: SQL 指纹

        Returns:
            是否更新成功
        """
        cypher = """
        MATCH (rs:ReferenceSQL {fingerprint: $fingerprint})
        SET rs.use_count = coalesce(rs.use_count, 0) + 1,
            rs.last_used = datetime()
        RETURN rs.use_count as use_count
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {"fingerprint": fingerprint})
                record = await result.single()
                if record:
                    logger.info(f"参考 SQL 使用次数更新: {fingerprint} -> {record['use_count']}")
                    return True
                return False
        except Exception as e:
            logger.error(f"更新参考 SQL 使用次数失败: {e}")
            return False

    # ==================== 失败模式学习 ====================

    @classmethod
    async def persist_failure_pattern(
        cls,
        failure_type: str,
        pattern: str,
        avoidance_hint: str,
        root_cause: Optional[str] = None,
        involved_tables: Optional[List[str]] = None,
        involved_columns: Optional[List[str]] = None,
        examples: Optional[List[str]] = None,
    ) -> bool:
        """
        将识别到的失败模式存入 Neo4j

        用于沉淀常见错误模式，供后续 Agent 学习避免。

        Args:
            failure_type: 失败类型（syntax_error/semantic_error/logic_error 等）
            pattern: 错误模式描述
            avoidance_hint: 避免策略
            root_cause: 根本原因
            involved_tables: 涉及的表
            involved_columns: 涉及的列
            examples: 错误示例

        Returns:
            是否保存成功
        """
        # 生成模式指纹
        pattern_fp = hashlib.sha256(f"{failure_type}:{pattern}".encode("utf-8")).hexdigest()[:16]
        ts = datetime.utcnow().isoformat()

        cypher = """
        MERGE (fp:FailurePattern {fingerprint: $fingerprint})
        SET fp.failure_type = $failure_type,
            fp.pattern = $pattern,
            fp.avoidance_hint = $avoidance_hint,
            fp.root_cause = $root_cause,
            fp.involved_tables = $involved_tables,
            fp.involved_columns = $involved_columns,
            fp.examples = $examples,
            fp.occurrence_count = coalesce(fp.occurrence_count, 0) + 1,
            fp.updated_at = $ts
        RETURN fp.occurrence_count as occurrence_count
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {
                    "fingerprint": pattern_fp,
                    "failure_type": failure_type,
                    "pattern": pattern,
                    "avoidance_hint": avoidance_hint,
                    "root_cause": root_cause,
                    "involved_tables": involved_tables or [],
                    "involved_columns": involved_columns or [],
                    "examples": examples or [],
                    "ts": ts,
                })
                record = await result.single()
                if record:
                    logger.info(
                        f"失败模式已保存: {failure_type}, 出现次数: {record['occurrence_count']}"
                    )
                    return True
                return False
        except Exception as e:
            logger.error(f"保存失败模式失败: {e}")
            return False

    @classmethod
    async def get_top_failure_patterns(
        cls,
        failure_type: Optional[str] = None,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        获取高频失败模式

        按出现次数排序，用于动态优化 Agent 提示词。

        Args:
            failure_type: 失败类型过滤（可选）
            limit: 返回数量

        Returns:
            失败模式列表
        """
        where_clause = "WHERE fp.failure_type = $failure_type" if failure_type else ""

        cypher = f"""
        MATCH (fp:FailurePattern)
        {where_clause}
        RETURN
            fp.fingerprint as fingerprint,
            fp.failure_type as failure_type,
            fp.pattern as pattern,
            fp.avoidance_hint as avoidance_hint,
            fp.root_cause as root_cause,
            fp.occurrence_count as occurrence_count,
            fp.involved_tables as involved_tables,
            fp.updated_at as updated_at
        ORDER BY fp.occurrence_count DESC
        LIMIT $limit
        """

        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await session.run(cypher, {
                    "failure_type": failure_type,
                    "limit": limit,
                })
                records = await result.data()
                return [convert_neo4j_types(r) for r in records]
        except Exception as e:
            logger.error(f"获取高频失败模式失败: {e}")
            return []
