"""
OpenLineage 物理资产写入器

范围：
- Catalog / Schema / Table / Column 的创建、变更、删除

约束：
- 不直接执行 Cypher（必须走 Repository）
- 仅承载“物理资产”相关业务语义，不处理 metric/语义层资产、不处理 tags
"""

from __future__ import annotations

import json
from typing import Any

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.kg.dto import CatalogDTO, ColumnDTO, SchemaDTO, TableDTO
from src.infrastructure.repository.openlineage import Metadata
from src.infrastructure.repository.openlineage.metadata import TableUpsertPayload
from src.modules.openlineage.parsers.plans.metadata import (
    AddColumnAction,
    AlterTablePlan,
    CatalogWritePlan,
    DeleteColumnAction,
    RenameColumnAction,
    RenameTableAction,
    SchemaWritePlan,
    TableWritePlan,
    UpdateColumnPropertyAction,
    UpdateTableCommentAction,
    UpdateTablePropertiesAction,
)
from src.modules.openlineage.writers.metadata.types import QueueEmbeddingTask

logger = structlog.get_logger()


class PhysicalAssetsWriter:
    # 表相关的操作（create/load 使用统一处理）
    TABLE_OPERATIONS = {"create_table", "load_table"}
    # alter_table 单独处理（需要解析 changes）
    ALTER_TABLE_OPERATIONS = {"alter_table"}
    # Schema 相关的操作
    SCHEMA_OPERATIONS = {"create_schema", "alter_schema", "load_schema"}
    # Catalog 相关的操作
    CATALOG_OPERATIONS = {"create_catalog", "alter_catalog"}
    # 删除操作（统一用 drop）
    DROP_TABLE_OPERATIONS = {"drop_table"}
    DROP_SCHEMA_OPERATIONS = {"drop_schema"}
    DROP_CATALOG_OPERATIONS = {"drop_catalog"}

    def __init__(self, *, queue_embedding_task: QueueEmbeddingTask) -> None:
        self._queue_embedding_task = queue_embedding_task
        self._catalogs_written = 0
        self._schemas_written = 0
        self._tables_written = 0
        self._columns_written = 0

    @property
    def catalogs_written(self) -> int:
        return self._catalogs_written

    @property
    def schemas_written(self) -> int:
        return self._schemas_written

    @property
    def tables_written(self) -> int:
        return self._tables_written

    @property
    def columns_written(self) -> int:
        return self._columns_written

    async def write_table_metadata(
        self, session: AsyncSession, plans: list[TableWritePlan]
    ) -> None:
        """写入表和列元数据（plans 由 parser 预先生成）"""
        for plan in plans:
            await self.write_catalog(session, plan.catalog)
            await self.write_schema(session, plan.schema)
            await self.write_table(session, plan.table)

            if plan.columns:
                column_ids = [col.id for col in plan.columns]
                await self._delete_orphan_columns(session, plan.table.id, column_ids)
                await self._write_columns_batch(session, plan.columns, plan.table.id)

    async def handle_alter_table(self, session: AsyncSession, plans: list[AlterTablePlan]) -> None:
        """
        处理 alter_table 事件

        根据 changes 列表中的变更类型执行对应操作：
        - RENAME_TABLE: 删除旧表节点，创建新表节点
        - UPDATE_COMMENT: 更新表注释
        - ADD_COLUMN: 添加列节点
        - DELETE_COLUMN: 删除列节点
        - RENAME_COLUMN: 删除旧列节点，创建新列节点
        - UPDATE_COLUMN_*: 更新列属性
        """
        for plan in plans:
            await self._apply_alter_plan(session, plan)

    async def write_schema_metadata(
        self, session: AsyncSession, plans: list[SchemaWritePlan]
    ) -> None:
        """
        写入 Schema 元数据

        Schema 事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}
        """
        for plan in plans:
            await self.write_catalog(session, plan.catalog)
            await self.write_schema(session, plan.schema)

    async def write_catalog_metadata(
        self, session: AsyncSession, plans: list[CatalogWritePlan]
    ) -> None:
        """
        写入 Catalog 元数据

        Catalog 事件格式：
        - namespace: gravitino://{metalake}
        - name: {catalog}
        """
        for plan in plans:
            await self.write_catalog(session, plan.catalog)

    async def delete_table_metadata(self, session: AsyncSession, table_ids: list[str]) -> None:
        """
        删除表元数据

        级联删除：Table + 所有 Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}.{table}
        """
        for table_id in table_ids:
            await Metadata.delete_table(session, table_id=table_id)
            logger.info("table_deleted", table_id=table_id)

    async def delete_schema_metadata(self, session: AsyncSession, schema_ids: list[str]) -> None:
        """
        删除 Schema 元数据

        级联删除：Schema + 所有 Table/Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}
        """
        for schema_id in schema_ids:
            await Metadata.delete_schema_cascade(session, schema_id=schema_id)
            logger.info("schema_deleted", schema_id=schema_id)

    async def delete_catalog_metadata(self, session: AsyncSession, catalog_ids: list[str]) -> None:
        """
        删除 Catalog 元数据

        级联删除：Catalog + 所有 Schema/Table/Column + 清理血缘边
        事件格式：
        - namespace: gravitino://{metalake}
        - name: {catalog}
        """
        for catalog_id in catalog_ids:
            await Metadata.delete_catalog_cascade(session, catalog_id=catalog_id)
            logger.info("catalog_deleted", catalog_id=catalog_id)

    async def write_catalog(self, session: AsyncSession, catalog: CatalogDTO) -> None:
        """写入 Catalog 节点"""
        tags = await Metadata.upsert_catalog(
            session,
            id=catalog.id,
            name=catalog.name,
            metalake=catalog.metalake,
            created_by="OPENLINEAGE",
            return_tags=True,
        )

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(catalog.id, "Catalog", catalog.name, None, tags)

        self._catalogs_written += 1
        logger.debug("catalog_written", id=catalog.id, name=catalog.name)

    async def write_schema(self, session: AsyncSession, schema: SchemaDTO) -> None:
        """写入 Schema 节点（不写 Catalog->Schema 关系）"""
        tags = await Metadata.upsert_schema(
            session,
            id=schema.id,
            name=schema.name,
            description=schema.description,
            properties=None,
            created_by="OPENLINEAGE",
            return_tags=True,
        )

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(schema.id, "Schema", schema.name, schema.description, tags)

        self._schemas_written += 1
        logger.debug("schema_written", id=schema.id, name=schema.name)

    async def write_table(self, session: AsyncSession, table: TableDTO) -> None:
        """写入 Table 节点（不写 Schema->Table 关系）"""
        properties_json = json.dumps(table.properties) if table.properties else None

        tags = await Metadata.upsert_table(
            session,
            id=table.id,
            name=table.name,
            created_by="OPENLINEAGE",
            payload=TableUpsertPayload(
                producer=table.producer,
                description=table.description,
                properties=properties_json,
                partitions=table.partitions,
                distribution=table.distribution,
                sort_orders=table.sort_orders,
                indexes=table.indexes,
                creator=table.creator,
                create_time=table.create_time,
                last_modifier=table.last_modifier,
                last_modified_time=table.last_modified_time,
            ),
            return_tags=True,
        )

        # 入队 embedding（带 tags）
        await self._queue_embedding_task(table.id, "Table", table.name, table.description, tags)

        self._tables_written += 1
        logger.debug("table_written", id=table.id, name=table.name)

    async def _apply_alter_plan(self, session: AsyncSession, plan: AlterTablePlan) -> None:
        for action in plan.actions:
            if isinstance(action, RenameTableAction):
                await Metadata.delete_table(session, table_id=action.old_table_id)
                await self.write_table(session, action.new_table)
                logger.info(
                    "table_renamed",
                    old_table_id=action.old_table_id,
                    new_table_id=action.new_table.id,
                )
                continue

            if isinstance(action, UpdateTableCommentAction):
                await self._update_table_comment(session, action.table_id, action.new_comment)
                continue

            if isinstance(action, UpdateTablePropertiesAction):
                await self._update_table_properties(session, action.table_id, action.properties)
                continue

            if isinstance(action, AddColumnAction):
                await Metadata.upsert_column_event(
                    session,
                    column_id=action.column.id,
                    name=action.column.name,
                    data_type=action.column.data_type,
                    description=action.column.description,
                    nullable=action.nullable,
                    auto_increment=action.auto_increment,
                    default_value=action.default_value,
                    created_by="OPENLINEAGE",
                )
                await self._queue_embedding_task(
                    action.column.id,
                    "Column",
                    action.column.name,
                    action.column.description,
                )
                continue

            if isinstance(action, DeleteColumnAction):
                await Metadata.delete_column(session, column_id=action.column_id)
                continue

            if isinstance(action, RenameColumnAction):
                description = await Metadata.rename_column(
                    session,
                    old_column_id=action.old_column_id,
                    new_column_id=action.new_column_id,
                    new_column_name=action.new_column_name,
                    created_by="OPENLINEAGE",
                )
                await self._queue_embedding_task(
                    action.new_column_id,
                    "Column",
                    action.new_column_name,
                    description,
                )
                continue

            if isinstance(action, UpdateColumnPropertyAction):
                await self._update_column_property(
                    session,
                    column_id=action.column_id,
                    property_name=action.property_name,
                    value=action.value,
                )
                continue

    async def _update_table_comment(
        self, session: AsyncSession, table_id: str, new_comment: str | None
    ) -> None:
        """更新表注释"""
        table_name = await Metadata.update_table_comment(
            session,
            table_id=table_id,
            description=new_comment,
        )
        if table_name:
            # 重新入队 embedding（描述更新了）
            await self._queue_embedding_task(table_id, "Table", table_name, new_comment)

    async def _update_table_properties(
        self, session: AsyncSession, table_id: str, properties: dict | None
    ) -> None:
        """更新表属性"""
        if properties is None:
            return
        await Metadata.update_table_properties(
            session,
            table_id=table_id,
            properties=properties,
        )

    async def _update_column_property(
        self, session: AsyncSession, column_id: str, property_name: str, value: Any
    ) -> None:
        """更新列的单个属性"""
        record = await Metadata.update_column_property(
            session,
            column_id=column_id,
            property_name=property_name,
            value=value,
        )
        # 如果更新的是描述，重新入队 embedding
        if record and property_name == "description":
            await self._queue_embedding_task(column_id, "Column", record["name"], value)

    async def _delete_orphan_columns(
        self, session: AsyncSession, table_id: str, valid_column_ids: list[str]
    ) -> None:
        """删除表下不再存在的列节点（处理 alter_table 删除列的情况）"""
        deleted_count = await Metadata.delete_orphan_columns(
            session,
            table_id=table_id,
            valid_column_ids=valid_column_ids,
        )
        if deleted_count > 0:
            logger.info(
                "orphan_columns_deleted",
                table_id=table_id,
                deleted_count=deleted_count,
            )

    async def _write_columns_batch(
        self, session: AsyncSession, columns: list[ColumnDTO], table_id: str
    ) -> None:
        """批量写入 Column 节点（不写 Table->Column 关系）"""
        # 构建批量数据
        column_data = [
            {
                "id": col.id,
                "name": col.name,
                "dataType": col.data_type,
                "description": col.description,
                "nullable": col.nullable,
                "autoIncrement": col.auto_increment,
                "defaultValue": col.default_value,
                "createdBy": "OPENLINEAGE",
            }
            for col in columns
        ]

        tags_map = await Metadata.upsert_columns_event(
            session,
            columns=column_data,
        )

        # 将 Column embedding 任务入队（带 tags）
        for col in columns:
            tags = tags_map.get(col.id)
            await self._queue_embedding_task(col.id, "Column", col.name, col.description, tags)

        self._columns_written += len(columns)
        logger.debug("columns_batch_written", count=len(columns), table_id=table_id)

    async def write_column(self, session: AsyncSession, column: ColumnDTO, table_id: str) -> None:
        """写入 Column 节点（不写 Table->Column 关系）"""
        await Metadata.upsert_column_event(
            session,
            column_id=column.id,
            name=column.name,
            data_type=column.data_type,
            description=column.description,
            nullable=column.nullable,
            auto_increment=column.auto_increment,
            default_value=column.default_value,
            created_by="OPENLINEAGE",
        )

        # 将 Column embedding 任务入队
        await self._queue_embedding_task(column.id, "Column", column.name, column.description)

        self._columns_written += 1
        logger.debug("column_written", id=column.id, name=column.name)
