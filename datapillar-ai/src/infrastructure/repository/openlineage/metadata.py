# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 元数据数据访问

约束：
- openlineage 模块内禁止直接拼接/执行 Cypher
- 所有与"元数据节点（Catalog/Schema/Table/Column/Metric/语义层）"相关的 Cypher 语句在此集中管理
- 不创建任何关系（HAS_* / 血缘边等）；关系统一由 Lineage 管理
- 事务边界由调用方（AsyncSession）控制；此层只做语句与参数映射
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Any

from src.infrastructure.database.cypher import arun_cypher
from src.infrastructure.repository.kg.dto import (
    ModifierDTO,
    UnitDTO,
    ValueDomainDTO,
    WordRootDTO,
)

_ALLOWED_NODE_LABELS: set[str] = {
    "Knowledge",
    "Catalog",
    "Schema",
    "Table",
    "Column",
    "SQL",
    "Tag",
    "AtomicMetric",
    "DerivedMetric",
    "CompositeMetric",
    "WordRoot",
    "Modifier",
    "Unit",
    "ValueDomain",
}

_ALLOWED_COLUMN_PROPERTIES: set[str] = {
    "dataType",
    "description",
    "nullable",
    "autoIncrement",
    "defaultValue",
    "name",
    "properties",
}


@dataclass(frozen=True, slots=True)
class TableUpsertPayload:
    producer: str | None = None
    description: str | None = None
    properties: str | None = None
    partitions: Any | None = None
    distribution: Any | None = None
    sort_orders: Any | None = None
    indexes: Any | None = None
    creator: str | None = None
    create_time: Any | None = None
    last_modifier: str | None = None
    last_modified_time: Any | None = None


class Metadata:
    """OpenLineage 元数据 Neo4j 访问层（Cypher Mapper）"""

    @staticmethod
    def _assert_node_label(node_label: str) -> None:
        if node_label not in _ALLOWED_NODE_LABELS:
            raise ValueError(f"不支持的 Neo4j label: {node_label}")

    @staticmethod
    def _assert_column_property(property_name: str) -> None:
        if property_name not in _ALLOWED_COLUMN_PROPERTIES:
            raise ValueError(f"不支持的 Column 属性更新: {property_name}")

    # ==================== Embedding 同步辅助 ====================

    @staticmethod
    async def list_embedding_ids(
        session: Any,
        *,
        provider: str,
    ) -> set[str]:
        """
        查询 embeddingProvider 与当前 provider 匹配的节点 id 集合。
        """
        query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND n.embeddingProvider = $provider
        RETURN n.id AS id
        """
        result = await arun_cypher(session, query, provider=provider)
        records = await result.data()
        return {r["id"] for r in records if r.get("id")}

    @staticmethod
    async def count_stale_embeddings(
        session: Any,
        *,
        provider: str,
    ) -> int:
        """
        统计 embeddingProvider 不匹配的节点数量（需要重新向量化）。
        """
        query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND (n.embeddingProvider IS NULL OR n.embeddingProvider <> $provider)
        RETURN count(n) AS count
        """
        result = await arun_cypher(session, query, provider=provider)
        record = await result.single()
        return int(record["count"]) if record and record.get("count") is not None else 0

    @staticmethod
    async def write_embeddings_batch(
        session: Any,
        *,
        node_label: str,
        data: Sequence[Mapping[str, Any]],
        provider: str,
    ) -> None:
        """
        批量回写 embedding（按 label 分组后的单组写入）。

        data: [{"id": "...", "embedding": [...]}, ...]
        """
        Metadata._assert_node_label(node_label)
        query = f"""
        UNWIND $data AS item
        MATCH (n:{node_label} {{id: item.id}})
        SET n.embedding = item.embedding,
            n.embeddingProvider = $provider,
            n.embeddingUpdatedAt = datetime()
        """
        await arun_cypher(session, query, data=list(data), provider=provider)

    # ==================== 基础节点：Catalog/Schema/Table/Column ====================

    @staticmethod
    async def upsert_catalog(
        session: Any,
        *,
        id: str,
        name: str,
        metalake: str,
        created_by: str,
        catalog_type: str | None = None,
        provider: str | None = None,
        description: str | None = None,
        properties: str | None = None,
        return_tags: bool = False,
    ) -> list[str] | None:
        query = """
        MERGE (c:Catalog:Knowledge {id: $id})
        ON CREATE SET
            c.id = $id,
            c.name = $name,
            c.metalake = $metalake,
            c.catalogType = $catalogType,
            c.provider = $provider,
            c.description = $description,
            c.properties = $properties,
            c.createdBy = $createdBy,
            c.createdAt = datetime()
        ON MATCH SET
            c.catalogType = COALESCE($catalogType, c.catalogType),
            c.provider = COALESCE($provider, c.provider),
            c.description = COALESCE($description, c.description),
            c.properties = COALESCE($properties, c.properties),
            c.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN c.tags as tags\n"

        result = await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            metalake=metalake,
            catalogType=catalog_type,
            provider=provider,
            description=description,
            properties=properties,
            createdBy=created_by,
        )
        if not return_tags:
            return None
        record = await result.single()
        tags = record["tags"] if record and record.get("tags") else None
        return tags

    # ==================== Schema / Table / Column（仅节点写入，不写关系） ====================

    @staticmethod
    async def upsert_schema(
        session: Any,
        *,
        id: str,
        name: str,
        created_by: str,
        description: str | None = None,
        properties: str | None = None,
        return_tags: bool = False,
    ) -> list[str] | None:
        """
        写入 Schema 节点（不写 Catalog->Schema 关系）。
        """
        query = """
        MERGE (s:Schema:Knowledge {id: $id})
        ON CREATE SET
            s.id = $id,
            s.name = $name,
            s.description = $description,
            s.properties = $properties,
            s.createdBy = $createdBy,
            s.createdAt = datetime()
        ON MATCH SET
            s.description = COALESCE($description, s.description),
            s.properties = COALESCE($properties, s.properties),
            s.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN s.tags as tags\n"

        result = await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            description=description,
            properties=properties,
            createdBy=created_by,
        )
        if not return_tags:
            return None
        record = await result.single()
        tags = record["tags"] if record and record.get("tags") else None
        return tags

    @staticmethod
    async def upsert_table(
        session: Any,
        *,
        id: str,
        name: str,
        created_by: str,
        payload: TableUpsertPayload | None = None,
        return_tags: bool = False,
    ) -> list[str] | None:
        """
        写入 Table 节点（不写 Schema->Table 关系）。
        """
        payload = payload or TableUpsertPayload()
        query = """
        MERGE (t:Table:Knowledge {id: $id})
        ON CREATE SET
            t.id = $id,
            t.name = $name,
            t.producer = $producer,
            t.description = $description,
            t.properties = $properties,
            t.partitions = $partitions,
            t.distribution = $distribution,
            t.sortOrders = $sortOrders,
            t.indexes = $indexes,
            t.creator = $creator,
            t.createTime = $createTime,
            t.lastModifier = $lastModifier,
            t.lastModifiedTime = $lastModifiedTime,
            t.createdBy = $createdBy,
            t.createdAt = datetime()
        ON MATCH SET
            t.producer = COALESCE($producer, t.producer),
            t.description = COALESCE($description, t.description),
            t.properties = COALESCE($properties, t.properties),
            t.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN t.tags as tags\n"

        result = await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            producer=payload.producer,
            description=payload.description,
            properties=payload.properties,
            partitions=payload.partitions,
            distribution=payload.distribution,
            sortOrders=payload.sort_orders,
            indexes=payload.indexes,
            creator=payload.creator,
            createTime=payload.create_time,
            lastModifier=payload.last_modifier,
            lastModifiedTime=payload.last_modified_time,
            createdBy=created_by,
        )
        if not return_tags:
            return None
        record = await result.single()
        tags = record["tags"] if record and record.get("tags") else None
        return tags

    @staticmethod
    async def upsert_columns_event(
        session: Any,
        *,
        columns: Sequence[Mapping[str, Any]],
    ) -> dict[str, list[str] | None]:
        """
        写入观测到的 Column（仅节点，不写 Table->Column 关系；保持历史语义）。
        """
        query = """
        UNWIND $columns AS col
        MERGE (c:Column:Knowledge {id: col.id})
        ON CREATE SET
            c.id = col.id,
            c.name = col.name,
            c.dataType = col.dataType,
            c.description = col.description,
            c.nullable = col.nullable,
            c.autoIncrement = col.autoIncrement,
            c.defaultValue = col.defaultValue,
            c.createdBy = col.createdBy,
            c.createdAt = datetime()
        ON MATCH SET
            c.dataType = COALESCE(col.dataType, c.dataType),
            c.description = COALESCE(col.description, c.description),
            c.updatedAt = datetime()
        RETURN c.id as id, c.tags as tags
        """
        result = await arun_cypher(session, query, columns=list(columns))
        records = [record async for record in result]
        return {r["id"]: r.get("tags") for r in records if r.get("id")}

    @staticmethod
    async def upsert_column_event(
        session: Any,
        *,
        column_id: str,
        name: str,
        data_type: str | None,
        description: str | None,
        nullable: bool | None,
        auto_increment: bool | None,
        default_value: Any,
        created_by: str,
    ) -> None:
        """
        写入观测到的单个 Column 节点（不写 Table->Column 关系）。
        """
        query = """
        MERGE (c:Column:Knowledge {id: $columnId})
        ON CREATE SET
            c.id = $columnId,
            c.name = $name,
            c.dataType = $dataType,
            c.description = $description,
            c.nullable = $nullable,
            c.autoIncrement = $autoIncrement,
            c.defaultValue = $defaultValue,
            c.createdBy = $createdBy,
            c.createdAt = datetime()
        ON MATCH SET
            c.dataType = COALESCE($dataType, c.dataType),
            c.description = COALESCE($description, c.description),
            c.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            columnId=column_id,
            name=name,
            dataType=data_type,
            description=description,
            nullable=nullable,
            autoIncrement=auto_increment,
            defaultValue=default_value,
            createdBy=created_by,
        )

    @staticmethod
    async def upsert_column_sync(
        session: Any,
        *,
        id: str,
        name: str,
        data_type: str,
        description: str | None,
        nullable: bool,
        auto_increment: bool,
        default_value: Any,
    ) -> None:
        """
        写入 Column 定义快照（仅节点；ON MATCH 覆盖 nullable/autoIncrement/defaultValue，保持历史语义）。
        """
        query = """
        MERGE (col:Column:Knowledge {id: $id})
        ON CREATE SET
            col.id = $id,
            col.name = $name,
            col.dataType = $dataType,
            col.description = $description,
            col.nullable = $nullable,
            col.autoIncrement = $autoIncrement,
            col.defaultValue = $defaultValue,
            col.createdBy = 'GRAVITINO_SYNC',
            col.createdAt = datetime()
        ON MATCH SET
            col.dataType = $dataType,
            col.description = COALESCE($description, col.description),
            col.nullable = $nullable,
            col.autoIncrement = $autoIncrement,
            col.defaultValue = $defaultValue,
            col.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            dataType=data_type,
            description=description,
            nullable=nullable,
            autoIncrement=auto_increment,
            defaultValue=default_value,
        )

    @staticmethod
    async def delete_orphan_columns(
        session: Any,
        *,
        table_id: str,
        valid_column_ids: Sequence[str],
    ) -> int:
        query = """
        MATCH (t:Table {id: $tableId})-[:HAS_COLUMN]->(c:Column)
        WHERE NOT c.id IN $validColumnIds
        DETACH DELETE c
        RETURN count(c) as deletedCount
        """
        result = await arun_cypher(
            session,
            query,
            tableId=table_id,
            validColumnIds=list(valid_column_ids),
        )
        record = await result.single()
        return int(record["deletedCount"]) if record and record.get("deletedCount") else 0

    @staticmethod
    async def delete_table(
        session: Any,
        *,
        table_id: str,
    ) -> None:
        query = """
        MATCH (t:Table {id: $tableId})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
        DETACH DELETE t, c
        """
        await arun_cypher(session, query, tableId=table_id)

    @staticmethod
    async def delete_schema_cascade(
        session: Any,
        *,
        schema_id: str,
    ) -> None:
        query = """
        MATCH (s:Schema {id: $schemaId})
        OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table)
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
        OPTIONAL MATCH (s)-[:HAS_METRIC]->(m)
        DETACH DELETE s, t, c, m
        """
        await arun_cypher(session, query, schemaId=schema_id)

    @staticmethod
    async def delete_catalog_cascade(
        session: Any,
        *,
        catalog_id: str,
    ) -> None:
        query = """
        MATCH (cat:Catalog {id: $catalogId})
        OPTIONAL MATCH (cat)-[:HAS_SCHEMA]->(s:Schema)
        OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table)
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column)
        OPTIONAL MATCH (s)-[:HAS_METRIC]->(m)
        DETACH DELETE cat, s, t, c, m
        """
        await arun_cypher(session, query, catalogId=catalog_id)

    @staticmethod
    async def delete_column(
        session: Any,
        *,
        column_id: str,
    ) -> None:
        query = """
        MATCH (c:Column {id: $columnId})
        DETACH DELETE c
        """
        await arun_cypher(session, query, columnId=column_id)

    @staticmethod
    async def rename_column(
        session: Any,
        *,
        old_column_id: str,
        new_column_id: str,
        new_column_name: str,
        created_by: str,
    ) -> str | None:
        """
        列重命名（迁移属性，只写 Column 节点，不写 Table->Column 关系）。
        """
        query = """
        MATCH (c:Column {id: $oldColumnId})
        WITH c, c.dataType as dataType, c.description as description,
             c.nullable as nullable, c.autoIncrement as autoIncrement,
             c.defaultValue as defaultValue
        DETACH DELETE c
        WITH dataType, description, nullable, autoIncrement, defaultValue
        MERGE (nc:Column:Knowledge {id: $newColumnId})
        ON CREATE SET
            nc.id = $newColumnId,
            nc.name = $newColumnName,
            nc.dataType = dataType,
            nc.description = description,
            nc.nullable = nullable,
            nc.autoIncrement = autoIncrement,
            nc.defaultValue = defaultValue,
            nc.createdBy = $createdBy,
            nc.createdAt = datetime()
        ON MATCH SET
            nc.name = COALESCE($newColumnName, nc.name),
            nc.dataType = COALESCE(dataType, nc.dataType),
            nc.description = COALESCE(description, nc.description),
            nc.nullable = COALESCE(nullable, nc.nullable),
            nc.autoIncrement = COALESCE(autoIncrement, nc.autoIncrement),
            nc.defaultValue = COALESCE(defaultValue, nc.defaultValue),
            nc.updatedAt = datetime()
        RETURN description
        """
        result = await arun_cypher(
            session,
            query,
            oldColumnId=old_column_id,
            newColumnId=new_column_id,
            newColumnName=new_column_name,
            createdBy=created_by,
        )
        record = await result.single()
        return record["description"] if record else None

    @staticmethod
    async def update_table_comment(
        session: Any,
        *,
        table_id: str,
        description: str | None,
    ) -> str | None:
        query = """
        MATCH (t:Table {id: $tableId})
        SET t.description = $description, t.updatedAt = datetime()
        RETURN t.name AS name
        """
        result = await arun_cypher(session, query, tableId=table_id, description=description)
        record = await result.single()
        return record["name"] if record else None

    @staticmethod
    async def update_table_properties(
        session: Any,
        *,
        table_id: str,
        properties: Any,
    ) -> None:
        query = """
        MATCH (t:Table {id: $tableId})
        SET t.properties = $properties, t.updatedAt = datetime()
        """
        await arun_cypher(session, query, tableId=table_id, properties=properties)

    @staticmethod
    async def update_column_property(
        session: Any,
        *,
        column_id: str,
        property_name: str,
        value: Any,
    ) -> dict[str, Any] | None:
        Metadata._assert_column_property(property_name)
        query = f"""
        MATCH (c:Column {{id: $columnId}})
        SET c.{property_name} = $value, c.updatedAt = datetime()
        RETURN c.name AS name, c.description AS description
        """
        result = await arun_cypher(session, query, columnId=column_id, value=value)
        record = await result.single()
        return dict(record) if record else None

    # ==================== tags 属性同步（写 tags，不做业务判定） ====================

    @staticmethod
    async def set_node_tags(
        session: Any,
        *,
        node_label: str,
        node_id: str,
        tags: Sequence[str],
        return_fields: bool = False,
    ) -> dict[str, Any] | None:
        Metadata._assert_node_label(node_label)
        query = f"""
        MATCH (n:{node_label} {{id: $nodeId}})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        if return_fields:
            query += "\nRETURN n.id as id, n.name as name, n.description as description\n"

        result = await arun_cypher(session, query, nodeId=node_id, tags=list(tags))
        if not return_fields:
            return None
        record = await result.single()
        return dict(record) if record else None

    # ==================== 语义层：Metric / WordRoot / Modifier / Unit / ValueDomain ====================

    @staticmethod
    async def upsert_metric_sync(
        session: Any,
        *,
        label: str,
        id: str,
        name: str,
        code: str,
        description: str | None,
        unit: str | None = None,
        aggregation_logic: str | None = None,
        calculation_formula: str | None = None,
        created_by: str,
    ) -> None:
        Metadata._assert_node_label(label)
        query = f"""
        MERGE (m:{label}:Knowledge {{id: $id}})
        ON CREATE SET
            m.id = $id,
            m.code = $code,
            m.name = $name,
            m.description = $description,
            m.unit = $unit,
            m.aggregationLogic = $aggregationLogic,
            m.calculationFormula = $calculationFormula,
            m.createdBy = $createdBy,
            m.createdAt = datetime()
        ON MATCH SET
            m.name = COALESCE($name, m.name),
            m.description = COALESCE($description, m.description),
            m.unit = COALESCE($unit, m.unit),
            m.aggregationLogic = COALESCE($aggregationLogic, m.aggregationLogic),
            m.calculationFormula = COALESCE($calculationFormula, m.calculationFormula),
            m.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            code=code,
            description=description,
            unit=unit,
            aggregationLogic=aggregation_logic,
            calculationFormula=calculation_formula,
            createdBy=created_by,
        )

    @staticmethod
    async def upsert_metric_event(
        session: Any,
        *,
        label: str,
        id: str,
        name: str,
        code: str,
        description: str | None,
        unit: str | None = None,
        aggregation_logic: str | None = None,
        calculation_formula: str | None = None,
        created_by: str,
    ) -> None:
        """
        写入观测到的 Metric：只在 ON MATCH 更新 description，保持历史语义。
        """
        Metadata._assert_node_label(label)
        query = f"""
        MERGE (m:{label}:Knowledge {{id: $id}})
        ON CREATE SET
            m.id = $id,
            m.code = $code,
            m.name = $name,
            m.description = $description,
            m.unit = $unit,
            m.aggregationLogic = $aggregationLogic,
            m.calculationFormula = $calculationFormula,
            m.createdBy = $createdBy,
            m.createdAt = datetime()
        ON MATCH SET
            m.description = COALESCE($description, m.description),
            m.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            code=code,
            description=description,
            unit=unit,
            aggregationLogic=aggregation_logic,
            calculationFormula=calculation_formula,
            createdBy=created_by,
        )

    @staticmethod
    async def delete_node(
        session: Any,
        *,
        node_id: str,
        node_label: str | None = None,
    ) -> None:
        if node_label:
            Metadata._assert_node_label(node_label)
            query = f"""
            MATCH (n:{node_label} {{id: $id}})
            DETACH DELETE n
            """
        else:
            query = """
            MATCH (n {id: $id})
            DETACH DELETE n
            """
        await arun_cypher(session, query, id=node_id)

    @staticmethod
    async def delete_metric(
        session: Any,
        *,
        metric_id: str,
    ) -> None:
        query = """
        MATCH (m {id: $metricId})
        WHERE m:AtomicMetric OR m:DerivedMetric OR m:CompositeMetric
        DETACH DELETE m
        """
        await arun_cypher(session, query, metricId=metric_id)

    @staticmethod
    async def upsert_wordroot(session: Any, dto: WordRootDTO) -> None:
        """写入 WordRoot 节点（必须先构建 DTO 验证）"""
        query = """
        MERGE (w:WordRoot:Knowledge {id: $id})
        ON CREATE SET
            w.id = $id,
            w.code = $code,
            w.name = $name,
            w.dataType = $dataType,
            w.description = $description,
            w.createdBy = $createdBy,
            w.createdAt = datetime()
        ON MATCH SET
            w.name = COALESCE($name, w.name),
            w.dataType = COALESCE($dataType, w.dataType),
            w.description = COALESCE($description, w.description),
            w.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=dto.id,
            code=dto.code,
            name=dto.name,
            dataType=dto.data_type,
            description=dto.description,
            createdBy=dto.created_by,
        )

    @staticmethod
    async def upsert_modifier(session: Any, dto: ModifierDTO) -> None:
        """写入 Modifier 节点（必须先构建 DTO 验证）"""
        query = """
        MERGE (m:Modifier:Knowledge {id: $id})
        ON CREATE SET
            m.id = $id,
            m.code = $code,
            m.name = $name,
            m.modifierType = $modifierType,
            m.description = $description,
            m.createdBy = $createdBy,
            m.createdAt = datetime()
        ON MATCH SET
            m.name = COALESCE($name, m.name),
            m.modifierType = COALESCE($modifierType, m.modifierType),
            m.description = COALESCE($description, m.description),
            m.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=dto.id,
            code=dto.code,
            name=dto.name,
            modifierType=dto.modifier_type,
            description=dto.description,
            createdBy=dto.created_by,
        )

    @staticmethod
    async def upsert_unit(session: Any, dto: UnitDTO) -> None:
        """写入 Unit 节点（必须先构建 DTO 验证）"""
        query = """
        MERGE (u:Unit:Knowledge {id: $id})
        ON CREATE SET
            u.id = $id,
            u.code = $code,
            u.name = $name,
            u.symbol = $symbol,
            u.description = $description,
            u.createdBy = $createdBy,
            u.createdAt = datetime()
        ON MATCH SET
            u.name = COALESCE($name, u.name),
            u.symbol = COALESCE($symbol, u.symbol),
            u.description = COALESCE($description, u.description),
            u.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=dto.id,
            code=dto.code,
            name=dto.name,
            symbol=dto.symbol,
            description=dto.description,
            createdBy=dto.created_by,
        )

    @staticmethod
    async def upsert_valuedomain(session: Any, dto: ValueDomainDTO) -> None:
        """写入 ValueDomain 节点（必须先构建 DTO 验证）"""
        query = """
        MERGE (v:ValueDomain:Knowledge {id: $id})
        ON CREATE SET
            v.id = $id,
            v.code = $code,
            v.name = $name,
            v.domainType = $domainType,
            v.domainLevel = $domainLevel,
            v.items = $items,
            v.dataType = $dataType,
            v.description = $description,
            v.createdBy = $createdBy,
            v.createdAt = datetime()
        ON MATCH SET
            v.name = COALESCE($name, v.name),
            v.domainType = COALESCE($domainType, v.domainType),
            v.domainLevel = COALESCE($domainLevel, v.domainLevel),
            v.items = COALESCE($items, v.items),
            v.dataType = COALESCE($dataType, v.dataType),
            v.description = COALESCE($description, v.description),
            v.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=dto.id,
            code=dto.code,
            name=dto.name,
            domainType=dto.domain_type,
            domainLevel=dto.domain_level,
            items=dto.items,
            dataType=dto.data_type,
            description=dto.description,
            createdBy=dto.created_by,
        )

    # ==================== Tag 节点（create_tag / alter_tag / drop_tag）====================

    @staticmethod
    async def upsert_tag(
        session: Any,
        *,
        id: str,
        name: str,
        description: str | None,
        properties: Mapping[str, str] | None,
        created_by: str,
    ) -> None:
        """创建或更新 Tag 节点"""
        query = """
        MERGE (t:Tag:Knowledge {id: $id})
        ON CREATE SET
            t.id = $id,
            t.name = $name,
            t.description = $description,
            t.properties = $properties,
            t.createdBy = $createdBy,
            t.createdAt = datetime()
        ON MATCH SET
            t.name = $name,
            t.description = COALESCE($description, t.description),
            t.properties = COALESCE($properties, t.properties),
            t.updatedAt = datetime()
        """
        await arun_cypher(
            session,
            query,
            id=id,
            name=name,
            description=description,
            properties=dict(properties) if properties else None,
            createdBy=created_by,
        )

    @staticmethod
    async def delete_tag(session: Any, *, tag_id: str) -> None:
        """删除 Tag 节点及其所有 HAS_TAG 关系"""
        query = """
        MATCH (t:Tag {id: $tagId})
        DETACH DELETE t
        """
        await arun_cypher(session, query, tagId=tag_id)
