# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识图谱同步血缘数据访问

约束：
- 模块内禁止直接拼接/执行 Cypher
- 所有与"血缘关系边（SQL/表级/列级/指标列/列值域）"相关的 Cypher 语句在此集中管理
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from typing import Any

from src.infrastructure.database.cypher import arun_cypher


class Lineage:
    """知识图谱同步血缘 Neo4j 访问层（Cypher Mapper）"""

    # ==================== 结构关系（层级边） ====================

    @staticmethod
    async def link_catalog_schema(
        session: Any,
        *,
        catalog_id: str,
        schema_id: str,
    ) -> None:
        query = """
        MATCH (c:Catalog {id: $catalogId})
        MATCH (s:Schema {id: $schemaId})
        MERGE (c)-[:HAS_SCHEMA]->(s)
        """
        await arun_cypher(session, query, catalogId=catalog_id, schemaId=schema_id)

    @staticmethod
    async def link_schema_table(
        session: Any,
        *,
        schema_id: str,
        table_id: str,
    ) -> None:
        query = """
        MATCH (s:Schema {id: $schemaId})
        MATCH (t:Table {id: $tableId})
        MERGE (s)-[:HAS_TABLE]->(t)
        """
        await arun_cypher(session, query, schemaId=schema_id, tableId=table_id)

    @staticmethod
    async def link_table_columns(
        session: Any,
        *,
        table_id: str,
        column_ids: Sequence[str],
    ) -> None:
        if not column_ids:
            return

        query = """
        UNWIND $columnIds AS columnId
        MATCH (t:Table {id: $tableId})
        MATCH (c:Column {id: columnId})
        MERGE (t)-[:HAS_COLUMN]->(c)
        """
        await arun_cypher(
            session,
            query,
            tableId=table_id,
            columnIds=list(column_ids),
        )

    @staticmethod
    async def link_schema_metric(
        session: Any,
        *,
        schema_id: str,
        metric_id: str,
    ) -> None:
        query = """
        MATCH (s:Schema {id: $schemaId})
        MATCH (m {id: $metricId})
        MERGE (s)-[:HAS_METRIC]->(m)
        """
        await arun_cypher(session, query, schemaId=schema_id, metricId=metric_id)

    # ==================== 指标父子关系：DERIVED_FROM / COMPUTED_FROM ====================

    _ALLOWED_METRIC_LABELS: set[str] = {"AtomicMetric", "DerivedMetric", "CompositeMetric"}
    _ALLOWED_METRIC_REL_TYPES: set[str] = {"DERIVED_FROM", "COMPUTED_FROM"}

    @staticmethod
    def _assert_metric_label(label: str) -> None:
        if label not in Lineage._ALLOWED_METRIC_LABELS:
            raise ValueError(f"不支持的 Metric label: {label}")

    @staticmethod
    def _assert_metric_rel(rel_type: str) -> None:
        if rel_type not in Lineage._ALLOWED_METRIC_REL_TYPES:
            raise ValueError(f"不支持的 Metric 关系类型: {rel_type}")

    @staticmethod
    async def set_metric_parents(
        session: Any,
        *,
        child_label: str,
        child_id: str,
        rel_type: str,
        parent_ids: Sequence[str],
    ) -> None:
        """
        重置某个指标的父子关系（用于 alter_metric 等）。

        注意：父指标固定为 AtomicMetric；子指标可能为 DerivedMetric / CompositeMetric。
        """
        Lineage._assert_metric_label(child_label)
        Lineage._assert_metric_rel(rel_type)

        delete_query = f"""
        MATCH (child:{child_label} {{id: $childId}})-[r:{rel_type}]->()
        DELETE r
        """
        await arun_cypher(session, delete_query, childId=child_id)

        if not parent_ids:
            return

        data = [{"childId": child_id, "parentId": pid} for pid in parent_ids]
        query = f"""
        UNWIND $data AS item
        MATCH (child:{child_label} {{id: item.childId}})
        MATCH (parent:AtomicMetric {{id: item.parentId}})
        MERGE (child)-[r:{rel_type}]->(parent)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, data=data)

    @staticmethod
    async def add_metric_parents(
        session: Any,
        *,
        child_label: str,
        child_id: str,
        rel_type: str,
        parent_ids: Sequence[str],
    ) -> None:
        """
        追加写入父子关系（不删除旧关系）。
        """
        Lineage._assert_metric_label(child_label)
        Lineage._assert_metric_rel(rel_type)

        if not parent_ids:
            return

        data = [{"childId": child_id, "parentId": pid} for pid in parent_ids]
        query = f"""
        UNWIND $data AS item
        MATCH (child:{child_label} {{id: item.childId}})
        MATCH (parent:AtomicMetric {{id: item.parentId}})
        MERGE (child)-[r:{rel_type}]->(parent)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, data=data)

    @staticmethod
    async def upsert_sql(
        session: Any,
        *,
        id: str,
        content: str,
        dialect: str | None,
        engine: str | None,
        job_namespace: str | None,
        job_name: str | None,
        created_by: str,
    ) -> None:
        query = """
        MERGE (s:SQL:Knowledge {id: $id})
        ON CREATE SET
            s.createdAt = datetime(),
            s.content = $content,
            s.dialect = $dialect,
            s.engine = $engine,
            s.jobNamespace = $jobNamespace,
            s.jobName = $jobName,
            s.executionCount = 1,
            s.createdBy = $createdBy
        ON MATCH SET
            s.updatedAt = datetime(),
            s.executionCount = COALESCE(s.executionCount, 0) + 1
        """
        await arun_cypher(
            session,
            query,
            id=id,
            content=content,
            dialect=dialect,
            engine=engine,
            jobNamespace=job_namespace,
            jobName=job_name,
            createdBy=created_by,
        )

    @staticmethod
    async def link_sql_inputs(
        session: Any,
        *,
        sql_id: str,
        table_ids: Sequence[str],
    ) -> None:
        query = """
        UNWIND $tableIds AS tableId
        MATCH (t:Table {id: tableId})
        MATCH (s:SQL {id: $sqlId})
        MERGE (t)-[r:INPUT_OF]->(s)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, tableIds=list(table_ids), sqlId=sql_id)

    @staticmethod
    async def link_sql_outputs(
        session: Any,
        *,
        sql_id: str,
        table_ids: Sequence[str],
    ) -> None:
        query = """
        UNWIND $tableIds AS tableId
        MATCH (s:SQL {id: $sqlId})
        MATCH (t:Table {id: tableId})
        MERGE (s)-[r:OUTPUT_TO]->(t)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, tableIds=list(table_ids), sqlId=sql_id)

    @staticmethod
    async def link_column_lineage(
        session: Any,
        *,
        lineage_data: Sequence[Mapping[str, Any]],
    ) -> None:
        query = """
        UNWIND $lineageData AS item
        MATCH (src:Column {id: item.srcId})
        MATCH (dst:Column {id: item.dstId})
        MERGE (dst)-[r:DERIVES_FROM]->(src)
        ON CREATE SET
            r.createdAt = datetime(),
            r.transformationType = item.transformType,
            r.transformationSubtype = item.transformSubtype
        ON MATCH SET
            r.updatedAt = datetime()
        """
        await arun_cypher(session, query, lineageData=list(lineage_data))

    # ==================== 指标列血缘：MEASURES / FILTERS_BY ====================

    @staticmethod
    async def set_metric_measures(
        session: Any,
        *,
        metric_id: str,
        column_ids: Sequence[str],
    ) -> None:
        delete_query = """
        MATCH (m:AtomicMetric {id: $metricId})-[r:MEASURES]->()
        DELETE r
        """
        await arun_cypher(session, delete_query, metricId=metric_id)

        if not column_ids:
            return

        data = [{"metricId": metric_id, "columnId": cid} for cid in column_ids]
        query = """
        UNWIND $lineageData AS item
        MATCH (m:AtomicMetric {id: item.metricId})
        MATCH (c:Column {id: item.columnId})
        MERGE (m)-[r:MEASURES]->(c)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, lineageData=data)

    @staticmethod
    async def set_metric_filters(
        session: Any,
        *,
        metric_id: str,
        column_ids: Sequence[str],
    ) -> None:
        delete_query = """
        MATCH (m:AtomicMetric {id: $metricId})-[r:FILTERS_BY]->()
        DELETE r
        """
        await arun_cypher(session, delete_query, metricId=metric_id)

        if not column_ids:
            return

        data = [{"metricId": metric_id, "columnId": cid} for cid in column_ids]
        query = """
        UNWIND $lineageData AS item
        MATCH (m:AtomicMetric {id: item.metricId})
        MATCH (c:Column {id: item.columnId})
        MERGE (m)-[r:FILTERS_BY]->(c)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, lineageData=data)

    @staticmethod
    async def add_metric_measure(
        session: Any,
        *,
        metric_id: str,
        column_id: str,
    ) -> None:
        query = """
        MATCH (m:AtomicMetric {id: $metricId})
        MATCH (c:Column {id: $columnId})
        MERGE (m)-[r:MEASURES]->(c)
        ON CREATE SET r.createdAt = datetime()
        """
        await arun_cypher(session, query, metricId=metric_id, columnId=column_id)

    @staticmethod
    async def add_metric_filter(
        session: Any,
        *,
        metric_id: str,
        column_id: str,
    ) -> None:
        query = """
        MATCH (m:AtomicMetric {id: $metricId})
        MATCH (c:Column {id: $columnId})
        MERGE (m)-[r:FILTERS_BY]->(c)
        ON CREATE SET r.createdAt = datetime()
        """
        await arun_cypher(session, query, metricId=metric_id, columnId=column_id)

    # ==================== 列值域血缘：HAS_VALUE_DOMAIN ====================

    @staticmethod
    async def add_column_valuedomain(
        session: Any,
        *,
        column_id: str,
        domain_code: str,
    ) -> dict[str, Any] | None:
        query = """
        MATCH (c:Column {id: $columnId})
        MATCH (v:ValueDomain {domainCode: $domainCode})
        MERGE (c)-[r:HAS_VALUE_DOMAIN]->(v)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        RETURN c.id as columnId, v.id as valueDomainId
        """
        result = await arun_cypher(session, query, columnId=column_id, domainCode=domain_code)
        record = await result.single()
        return dict(record) if record else None

    @staticmethod
    async def remove_column_valuedomain(
        session: Any,
        *,
        column_id: str,
        domain_code: str,
    ) -> dict[str, Any] | None:
        query = """
        MATCH (c:Column {id: $columnId})-[r:HAS_VALUE_DOMAIN]->(v:ValueDomain {domainCode: $domainCode})
        DELETE r
        RETURN c.id as columnId, v.id as valueDomainId
        """
        result = await arun_cypher(session, query, columnId=column_id, domainCode=domain_code)
        record = await result.single()
        return dict(record) if record else None

    @staticmethod
    async def set_column_valuedomain(
        session: Any,
        *,
        column_id: str,
        domain_code: str,
    ) -> None:
        query = """
        MATCH (c:Column {id: $columnId})
        MATCH (v:ValueDomain {domainCode: $domainCode})
        OPTIONAL MATCH (c)-[old:HAS_VALUE_DOMAIN]->()
        DELETE old
        WITH c, v
        MERGE (c)-[r:HAS_VALUE_DOMAIN]->(v)
        ON CREATE SET r.createdAt = datetime()
        """
        await arun_cypher(session, query, columnId=column_id, domainCode=domain_code)

    # ==================== Tag 关系边：HAS_TAG ====================

    _ALLOWED_TAG_SOURCE_LABELS: set[str] = {"Catalog", "Schema", "Table", "Column"}

    @staticmethod
    def _assert_tag_label(label: str) -> None:
        if label not in Lineage._ALLOWED_TAG_SOURCE_LABELS:
            raise ValueError(f"不支持的 Tag 源节点类型: {label}")

    @staticmethod
    async def add_has_tag(
        session: Any,
        *,
        source_label: str,
        source_id: str,
        tag_id: str,
    ) -> dict[str, Any] | None:
        """添加 HAS_TAG 关系边"""
        Lineage._assert_tag_label(source_label)
        query = f"""
        MATCH (src:{source_label} {{id: $sourceId}})
        MATCH (tag:Tag {{id: $tagId}})
        MERGE (src)-[r:HAS_TAG]->(tag)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        RETURN src.id as sourceId, tag.id as tagId
        """
        result = await arun_cypher(session, query, sourceId=source_id, tagId=tag_id)
        record = await result.single()
        return dict(record) if record else None

    @staticmethod
    async def remove_has_tag(
        session: Any,
        *,
        source_label: str,
        source_id: str,
        tag_id: str,
    ) -> dict[str, Any] | None:
        """移除 HAS_TAG 关系边"""
        Lineage._assert_tag_label(source_label)
        query = f"""
        MATCH (src:{source_label} {{id: $sourceId}})-[r:HAS_TAG]->(tag:Tag {{id: $tagId}})
        DELETE r
        RETURN src.id as sourceId, tag.id as tagId
        """
        result = await arun_cypher(session, query, sourceId=source_id, tagId=tag_id)
        record = await result.single()
        return dict(record) if record else None

    @staticmethod
    async def batch_add_tag(
        session: Any,
        *,
        source_label: str,
        source_id: str,
        tag_ids: Sequence[str],
    ) -> None:
        """批量添加 HAS_TAG 关系边"""
        if not tag_ids:
            return
        Lineage._assert_tag_label(source_label)
        query = f"""
        UNWIND $tagIds AS tagId
        MATCH (src:{source_label} {{id: $sourceId}})
        MATCH (tag:Tag {{id: tagId}})
        MERGE (src)-[r:HAS_TAG]->(tag)
        ON CREATE SET r.createdAt = datetime()
        ON MATCH SET r.updatedAt = datetime()
        """
        await arun_cypher(session, query, sourceId=source_id, tagIds=list(tag_ids))

    @staticmethod
    async def batch_remove_tag(
        session: Any,
        *,
        source_label: str,
        source_id: str,
        tag_ids: Sequence[str],
    ) -> None:
        """批量移除 HAS_TAG 关系边"""
        if not tag_ids:
            return
        Lineage._assert_tag_label(source_label)
        query = f"""
        UNWIND $tagIds AS tagId
        MATCH (src:{source_label} {{id: $sourceId}})-[r:HAS_TAG]->(tag:Tag {{id: tagId}})
        DELETE r
        """
        await arun_cypher(session, query, sourceId=source_id, tagIds=list(tag_ids))
