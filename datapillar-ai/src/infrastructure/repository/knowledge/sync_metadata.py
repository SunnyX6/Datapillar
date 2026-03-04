# @author Sunny
# @date 2026-01-27

"""
Knowledge graph synchronization metadata data access

constraint:- Direct splicing is prohibited within the module/execute Cypher
- All related to"metadata node(Catalog/Schema/Table/Column/Metric/Semantic layer)"relevant Cypher Statements are managed centrally here
- Does not create any relationship(HAS_* / blood relationship);The relationship is unified by Lineage management
- Transaction boundaries are determined by the caller(AsyncSession)control;This layer only does statement and parameter mapping
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Any

from src.infrastructure.database.cypher import arun_cypher
from src.infrastructure.repository.knowledge.dto import (
    ModifierDTO,
    UnitDTO,
    ValueDomainDTO,
    WordRootDTO,
)
from src.shared.context import get_current_tenant_id

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


def _require_tenant_id() -> int:
    tenant_id = get_current_tenant_id()
    if tenant_id is None:
        raise ValueError("missing tenant context")
    return int(tenant_id)


async def _run_with_tenant(session: Any, query: str, **params: Any) -> Any:
    return await arun_cypher(session, query, tenantId=_require_tenant_id(), **params)


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
    """Knowledge graph synchronization metadata Neo4j access layer(Cypher Mapper)"""

    @staticmethod
    def _assert_node_label(node_label: str) -> None:
        if node_label not in _ALLOWED_NODE_LABELS:
            raise ValueError(f"Not supported Neo4j label:\n    {node_label}")

    @staticmethod
    def _assert_column_property(property_name: str) -> None:
        if property_name not in _ALLOWED_COLUMN_PROPERTIES:
            raise ValueError(f"Not supported Column Property update:\n    {property_name}")

    # ==================== Embedding Sync assist ====================

    @staticmethod
    async def list_embedding_ids(
        session: Any,
        *,
        provider: str,
    ) -> set[str]:
        """
        Query embeddingProvider with current provider matching node id collection."""
        query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND n.tenantId = $tenantId
          AND n.embeddingProvider = $provider
        RETURN n.id AS id
        """
        result = await _run_with_tenant(session, query, provider=provider)
        records = await result.data()
        return {r["id"] for r in records if r.get("id")}

    @staticmethod
    async def count_stale_embeddings(
        session: Any,
        *,
        provider: str,
    ) -> int:
        """
        statistics embeddingProvider Number of unmatched nodes(Need to be re-vectorized)."""
        query = """
        MATCH (n:Knowledge)
        WHERE n.embedding IS NOT NULL
          AND n.tenantId = $tenantId
          AND (n.embeddingProvider IS NULL OR n.embeddingProvider <> $provider)
        RETURN count(n) AS count
        """
        result = await _run_with_tenant(session, query, provider=provider)
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
        Batch writeback embedding(press label Single group write after grouping).data:[{"id":"...","embedding":[...]},...]
        """
        Metadata._assert_node_label(node_label)
        query = f"""
        UNWIND $data AS item
        MATCH (n:{node_label} {{id: item.id, tenantId: $tenantId}})
        SET n.embedding = item.embedding,
            n.embeddingProvider = $provider,
            n.embeddingUpdatedAt = datetime()
        """
        await _run_with_tenant(session, query, data=list(data), provider=provider)

    # ==================== base node:Catalog/Schema/Table/Column ====================

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
        MERGE (c:Catalog:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            c.id = $id,
            c.tenantId = $tenantId,
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
            c.tenantId = $tenantId,
            c.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN c.tags as tags\n"

        result = await _run_with_tenant(
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

    # ==================== Schema / Table / Column(Node write only,Don't write about the relationship) ====================

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
        """t write about the relationship) ====================

        @staticmethod
        async def upsert_schema(session:Any,*,id:str,name:str,created_by:str,description:str | None = None,properties:str | None = None,return_tags:bool = False,) -> list[str] | None:
            \"""
        write Schema node(Dont write Catalog->Schema relationship).\"""
        query = \"""
        MERGE (s:Schema:Knowledge {id:$id,tenantId:$tenantId})
        ON CREATE SET
        s.id = $id,s.tenantId = $tenantId,s.name = $name,s.description = $description,s.properties = $properties,s.createdBy = $createdBy,s.createdAt = datetime()
        ON MATCH SET
        s.description = COALESCE($description,s.description),s.properties = COALESCE($properties,s.properties),s.tenantId = $tenantId,s.updatedAt = datetime()
        \"""
        if return_tags:
            query += "\nRETURN s.tags as tags\n"

        result = await _run_with_tenant(session,query,id=id,name=name,description=description,properties=properties,createdBy=created_by,)
        if not return_tags:
            return None
        record = await result.single()
        tags = record["tags"] if record and record.get("tags") else None
        return tags

        @staticmethod
        async def upsert_table(session:Any,*,id:str,name:str,created_by:str,payload:TableUpsertPayload | None = None,return_tags:bool = False,) -> list[str] | None:
            \"""
        write Table node(Dont write Schema->Table relationship).\"""
        payload = payload or TableUpsertPayload()
        query = \"""
        MERGE (t:Table:Knowledge {id:$id,tenantId:$tenantId})
        ON CREATE SET
        t.id = $id,t.tenantId = $tenantId,t.name = $name,t.producer = $producer,t.description = $description,t.properties = $properties,t.partitions = $partitions,t.distribution = $distribution,t.sortOrders = $sortOrders,t.indexes = $indexes,t.creator = $creator,t.createTime = $createTime,t.lastModifier = $lastModifier,t.lastModifiedTime = $lastModifiedTime,t.createdBy = $createdBy,t.createdAt = datetime()
        ON MATCH SET
        t.producer = COALESCE($producer,t.producer),t.description = COALESCE($description,t.description),t.properties = COALESCE($properties,t.properties),t.tenantId = $tenantId,t.updatedAt = datetime()
        \"""
        if return_tags:
            query += "\nRETURN t.tags as tags\n"

        result = await _run_with_tenant(session,query,id=id,name=name,producer=payload.producer,description=payload.description,properties=payload.properties,partitions=payload.partitions,distribution=payload.distribution,sortOrders=payload.sort_orders,indexes=payload.indexes,creator=payload.creator,createTime=payload.create_time,lastModifier=payload.last_modifier,lastModifiedTime=payload.last_modified_time,createdBy=created_by,)
        if not return_tags:
            return None
        record = await result.single()
        tags = record["tags"] if record and record.get("tags") else None
        return tags

        @staticmethod
        async def upsert_columns_event(session:Any,*,columns:Sequence[Mapping[str,Any]],) -> dict[str,list[str] | None]:
            \"""
        write observed Column(Node only,Dont write Table->Column relationship;Keep historical semantics).\"""
        query = \"""
        UNWIND $columns AS col
        MERGE (c:Column:Knowledge {id:col.id,tenantId:$tenantId})
        ON CREATE SET
        c.id = col.id,c.tenantId = $tenantId,c.name = col.name,c.dataType = col.dataType,c.description = col.description,c.nullable = col.nullable,c.autoIncrement = col.autoIncrement,c.defaultValue = col.defaultValue,c.createdBy = col.createdBy,c.createdAt = datetime()
        ON MATCH SET
        c.dataType = COALESCE(col.dataType,c.dataType),c.description = COALESCE(col.description,c.description),c.tenantId = $tenantId,c.updatedAt = datetime()
        RETURN c.id as id,c.tags as tags
        \"""
        result = await _run_with_tenant(session,query,columns=list(columns))
        records = [record async for record in result]
        return {r["id"]:r.get("tags") for r in records if r.get("id")}

        @staticmethod
        async def upsert_column_event(session:Any,*,column_id:str,name:str,data_type:str | None,description:str | None,nullable:bool | None,auto_increment:bool | None,default_value:Any,created_by:str,) -> None:
            \"""
        Write the observed single Column node(Dont write Table->Column relationship).\"""
        query = \"""
        MERGE (c:Column:Knowledge {id:$columnId,tenantId:$tenantId})
        ON CREATE SET
        c.id = $columnId,c.tenantId = $tenantId,c.name = $name,c.dataType = $dataType,c.description = $description,c.nullable = $nullable,c.autoIncrement = $autoIncrement,c.defaultValue = $defaultValue,c.createdBy = $createdBy,c.createdAt = datetime()
        ON MATCH SET
        c.dataType = COALESCE($dataType,c.dataType),c.description = COALESCE($description,c.description),c.tenantId = $tenantId,c.updatedAt = datetime()
        \"""
        await _run_with_tenant(session,query,columnId=column_id,name=name,dataType=data_type,description=description,nullable=nullable,autoIncrement=auto_increment,defaultValue=default_value,createdBy=created_by,)

        @staticmethod
        async def upsert_column_sync(session:Any,*,id:str,name:str,data_type:str,description:str | None,nullable:bool,auto_increment:bool,default_value:Any,) -> None:
            \"""
        write Column Define snapshot(Node only;ON MATCH Cover nullable/autoIncrement/defaultValue,Keep historical semantics).\"""
        query = \"""
        MERGE (col:Column:Knowledge {id:$id,tenantId:$tenantId})
        ON CREATE SET
        col.id = $id,col.tenantId = $tenantId,col.name = $name,col.dataType = $dataType,col.description = $description,col.nullable = $nullable,col.autoIncrement = $autoIncrement,col.defaultValue = $defaultValue,col.createdBy =
        """
        query = """
        MERGE (s:Schema:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            s.id = $id,
            s.tenantId = $tenantId,
            s.name = $name,
            s.description = $description,
            s.properties = $properties,
            s.createdBy = $createdBy,
            s.createdAt = datetime()
        ON MATCH SET
            s.description = COALESCE($description, s.description),
            s.properties = COALESCE($properties, s.properties),
            s.tenantId = $tenantId,
            s.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN s.tags as tags\n"

        result = await _run_with_tenant(
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
        await _run_with_tenant(session,query,catalogId=catalog_id)

        @staticmethod
        async def delete_column(session:Any,*,column_id:str,) -> None:
            query ="""
        payload = payload or TableUpsertPayload()
        query = """
        MERGE (t:Table:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            t.id = $id,
            t.tenantId = $tenantId,
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
            t.tenantId = $tenantId,
            t.updatedAt = datetime()
        """
        if return_tags:
            query += "\nRETURN t.tags as tags\n"

        result = await _run_with_tenant(
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
        await _run_with_tenant(session,query,tableId=table_id,properties=properties)

        @staticmethod
        async def update_column_property(session:Any,*,column_id:str,property_name:str,value:Any,) -> dict[str,Any] | None:
            Metadata._assert_column_property(property_name)
        query = f"""
        query = """
        UNWIND $columns AS col
        MERGE (c:Column:Knowledge {id: col.id, tenantId: $tenantId})
        ON CREATE SET
            c.id = col.id,
            c.tenantId = $tenantId,
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
            c.tenantId = $tenantId,
            c.updatedAt = datetime()
        RETURN c.id as id, c.tags as tags
        """
        result = await _run_with_tenant(session, query, columns=list(columns))
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
        await _run_with_tenant(session,query,id=id,name=name,code=code,description=description,unit=unit,aggregationLogic=aggregation_logic,calculationFormula=calculation_formula,createdBy=created_by,)

        @staticmethod
        async def delete_node(session:Any,*,node_id:str,node_label:str | None = None,) -> None:
            if node_label:
                Metadata._assert_node_label(node_label)
        query = f"""
        query = """
        MERGE (c:Column:Knowledge {id: $columnId, tenantId: $tenantId})
        ON CREATE SET
            c.id = $columnId,
            c.tenantId = $tenantId,
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
            c.tenantId = $tenantId,
            c.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        await _run_with_tenant(session,query,id=node_id)

        @staticmethod
        async def delete_metric(session:Any,*,metric_id:str,) -> None:
            query ="""
        query = """
        MERGE (col:Column:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            col.id = $id,
            col.tenantId = $tenantId,
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
            col.tenantId = $tenantId,
            col.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        MATCH (t:Table {id: $tableId, tenantId: $tenantId})-[:HAS_COLUMN]->(c:Column {tenantId: $tenantId})
        WHERE NOT c.id IN $validColumnIds
        DETACH DELETE c
        RETURN count(c) as deletedCount
        """
        result = await _run_with_tenant(
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
        MATCH (t:Table {id: $tableId, tenantId: $tenantId})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column {tenantId: $tenantId})
        DETACH DELETE t, c
        """
        await _run_with_tenant(session, query, tableId=table_id)

    @staticmethod
    async def delete_schema_cascade(
        session: Any,
        *,
        schema_id: str,
    ) -> None:
        query = """
        MATCH (s:Schema {id: $schemaId, tenantId: $tenantId})
        OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table {tenantId: $tenantId})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column {tenantId: $tenantId})
        OPTIONAL MATCH (s)-[:HAS_METRIC]->(m {tenantId: $tenantId})
        DETACH DELETE s, t, c, m
        """
        await _run_with_tenant(session, query, schemaId=schema_id)

    @staticmethod
    async def delete_catalog_cascade(
        session: Any,
        *,
        catalog_id: str,
    ) -> None:
        query = """
        MATCH (cat:Catalog {id: $catalogId, tenantId: $tenantId})
        OPTIONAL MATCH (cat)-[:HAS_SCHEMA]->(s:Schema {tenantId: $tenantId})
        OPTIONAL MATCH (s)-[:HAS_TABLE]->(t:Table {tenantId: $tenantId})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(c:Column {tenantId: $tenantId})
        OPTIONAL MATCH (s)-[:HAS_METRIC]->(m {tenantId: $tenantId})
        DETACH DELETE cat, s, t, c, m
        """
        await _run_with_tenant(session, query, catalogId=catalog_id)

    @staticmethod
    async def delete_column(
        session: Any,
        *,
        column_id: str,
    ) -> None:
        query = """
        MATCH (c:Column {id: $columnId, tenantId: $tenantId})
        DETACH DELETE c
        """
        await _run_with_tenant(session, query, columnId=column_id)

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
        await _run_with_tenant(session,query,id=dto.id,code=dto.code,name=dto.name,domainType=dto.domain_type,domainLevel=dto.domain_level,items=dto.items,dataType=dto.data_type,description=dto.description,createdBy=dto.created_by,)

        # ==================== Tag node(create_tag / alter_tag / drop_tag)====================

        @staticmethod
        async def upsert_tag(session:Any,*,id:str,name:str,description:str | None,properties:Mapping[str,str] | None,created_by:str,) -> None:
        """
        query = """
        MATCH (c:Column {id: $oldColumnId, tenantId: $tenantId})
        WITH c, c.dataType as dataType, c.description as description,
             c.nullable as nullable, c.autoIncrement as autoIncrement,
             c.defaultValue as defaultValue
        DETACH DELETE c
        WITH dataType, description, nullable, autoIncrement, defaultValue
        MERGE (nc:Column:Knowledge {id: $newColumnId, tenantId: $tenantId})
        ON CREATE SET
            nc.id = $newColumnId,
            nc.tenantId = $tenantId,
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
            nc.tenantId = $tenantId,
            nc.updatedAt = datetime()
        RETURN description
        """
        result = await _run_with_tenant(
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
        MATCH (t:Table {id: $tableId, tenantId: $tenantId})
        SET t.description = $description, t.updatedAt = datetime()
        RETURN t.name AS name
        """
        result = await _run_with_tenant(
            session,
            query,
            tableId=table_id,
            description=description,
        )
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
        MATCH (t:Table {id: $tableId, tenantId: $tenantId})
        SET t.properties = $properties, t.updatedAt = datetime()
        """
        await _run_with_tenant(session, query, tableId=table_id, properties=properties)

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
        MATCH (c:Column {{id: $columnId, tenantId: $tenantId}})
        SET c.{property_name} = $value, c.updatedAt = datetime()
        RETURN c.name AS name, c.description AS description
        """
        result = await _run_with_tenant(session, query, columnId=column_id, value=value)
        record = await result.single()
        return dict(record) if record else None

    # ==================== tags Property synchronization(write tags,No business judgment) ====================

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
        MATCH (n:{node_label} {{id: $nodeId, tenantId: $tenantId}})
        SET n.tags = $tags, n.updatedAt = datetime()
        """
        if return_fields:
            query += "\nRETURN n.id as id, n.name as name, n.description as description\n"

        result = await _run_with_tenant(session, query, nodeId=node_id, tags=list(tags))
        if not return_fields:
            return None
        record = await result.single()
        return dict(record) if record else None

    # ==================== Semantic layer:Metric / WordRoot / Modifier / Unit / ValueDomain ====================

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
        MERGE (m:{label}:Knowledge {{id: $id, tenantId: $tenantId}})
        ON CREATE SET
            m.id = $id,
            m.tenantId = $tenantId,
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
            m.tenantId = $tenantId,
            m.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        Write the observed Metric: only update description on ON MATCH to preserve historical semantics.
        """
        Metadata._assert_node_label(label)
        query = f"""
        MERGE (m:{label}:Knowledge {{id: $id, tenantId: $tenantId}})
        ON CREATE SET
            m.id = $id,
            m.tenantId = $tenantId,
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
            m.tenantId = $tenantId,
            m.updatedAt = datetime()
        """
        await _run_with_tenant(
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
            MATCH (n:{node_label} {{id: $id, tenantId: $tenantId}})
            DETACH DELETE n
            """
        else:
            query = """
            MATCH (n {id: $id})
            WHERE n.tenantId = $tenantId
            DETACH DELETE n
            """
        await _run_with_tenant(session, query, id=node_id)

    @staticmethod
    async def delete_metric(
        session: Any,
        *,
        metric_id: str,
    ) -> None:
        query = """
        MATCH (m {id: $metricId})
        WHERE (m:AtomicMetric OR m:DerivedMetric OR m:CompositeMetric)
          AND m.tenantId = $tenantId
        DETACH DELETE m
        """
        await _run_with_tenant(session, query, metricId=metric_id)

    @staticmethod
    async def upsert_wordroot(session: Any, dto: WordRootDTO) -> None:
        """Upsert WordRoot node (DTO must be built and validated first)."""
        query = """
        MERGE (w:WordRoot:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            w.id = $id,
            w.tenantId = $tenantId,
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
            w.tenantId = $tenantId,
            w.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        """Upsert Modifier node (DTO must be built and validated first)."""
        query = """
        MERGE (m:Modifier:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            m.id = $id,
            m.tenantId = $tenantId,
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
            m.tenantId = $tenantId,
            m.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        """Upsert Unit node (DTO must be built and validated first)."""
        query = """
        MERGE (u:Unit:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            u.id = $id,
            u.tenantId = $tenantId,
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
            u.tenantId = $tenantId,
            u.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        """Upsert ValueDomain node (DTO must be built and validated first)."""
        query = """
        MERGE (v:ValueDomain:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            v.id = $id,
            v.tenantId = $tenantId,
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
            v.tenantId = $tenantId,
            v.updatedAt = datetime()
        """
        await _run_with_tenant(
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

    # ==================== Tag node(create_tag / alter_tag / drop_tag)====================

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
        """Create or update a Tag node."""
        query = """
        MERGE (t:Tag:Knowledge {id: $id, tenantId: $tenantId})
        ON CREATE SET
            t.id = $id,
            t.tenantId = $tenantId,
            t.name = $name,
            t.description = $description,
            t.properties = $properties,
            t.createdBy = $createdBy,
            t.createdAt = datetime()
        ON MATCH SET
            t.name = $name,
            t.description = COALESCE($description, t.description),
            t.properties = COALESCE($properties, t.properties),
            t.tenantId = $tenantId,
            t.updatedAt = datetime()
        """
        await _run_with_tenant(
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
        """Delete a Tag node and all its HAS_TAG relationships."""
        query = """
        MATCH (t:Tag {id: $tagId, tenantId: $tenantId})
        DETACH DELETE t
        """
        await _run_with_tenant(session, query, tagId=tag_id)
