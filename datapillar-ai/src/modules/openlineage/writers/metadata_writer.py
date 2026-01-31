# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
元数据写入器

职责边界：
- 只负责写入 Neo4j 的"节点"
- 不写入任何关系（边）；关系统一由 LineageWriter 负责
"""

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.kg.dto import MetricDTO
from src.modules.openlineage.core.embedding_processor import embedding_processor
from src.modules.openlineage.parsers.plans.types import MetadataWritePlans
from src.modules.openlineage.writers.base import BaseWriter
from src.modules.openlineage.writers.metadata import (
    PhysicalAssetsWriter,
    SemanticAssetsWriter,
    TagWriter,
)

logger = logging.getLogger(__name__)


class MetadataWriter(BaseWriter):
    """
    元数据写入器

    负责将所有元数据节点写入 Neo4j（不写关系）：
    - Catalog: 数据目录
    - Schema: 数据库/命名空间
    - Table: 数据表
    - Column: 字段
    - Metric: 指标
    - Tag: 标签

    写入后将 embedding 任务入队，由 EmbeddingProcessor 异步处理
    """

    def __init__(self) -> None:
        super().__init__()
        self._embedding_tasks_queued = 0
        self._tag_embedding_tasks_queued = 0
        self._physical_assets_writer = PhysicalAssetsWriter(
            queue_embedding_task=self._queue_embedding_task
        )
        self._semantic_assets_writer = SemanticAssetsWriter(
            queue_embedding_task=self._queue_embedding_task,
            physical_assets_writer=self._physical_assets_writer,
        )
        self._tag_writer = TagWriter(queue_tag_embedding_task=self._queue_tag_embedding)

    async def _queue_embedding_task(
        self,
        node_id: str,
        node_label: str,
        name: str,
        description: str | None = None,
        tags: list[str] | None = None,
    ) -> None:
        """将 embedding 任务入队（跳过无描述的节点）"""
        # 没有描述的节点不做 embedding，避免低质量向量误导检索
        if not description or not description.strip():
            return

        parts = [name, description]
        if tags:
            parts.extend(tags)
        text = " ".join(parts)
        if await embedding_processor.put(node_id, node_label, text):
            self._embedding_tasks_queued += 1

    async def _queue_tag_embedding(self, node_id: str, text: str) -> None:
        """
        Tag 向量化入队（name 本身有业务含义，不需要 description 必选）

        与普通节点不同，Tag 的 name 通常就包含业务语义，如"金融"、"核心指标"等
        """
        if await embedding_processor.put(node_id, "Tag", text):
            self._tag_embedding_tasks_queued += 1

    @property
    def name(self) -> str:
        return "metadata_writer"

    async def _apply_deletes(self, session: AsyncSession, plans: MetadataWritePlans) -> None:
        if plans.table_ids_to_drop:
            await self._physical_assets_writer.delete_table_metadata(
                session, plans.table_ids_to_drop
            )
        if plans.schema_ids_to_drop:
            await self._physical_assets_writer.delete_schema_metadata(
                session, plans.schema_ids_to_drop
            )
        if plans.catalog_ids_to_drop:
            await self._physical_assets_writer.delete_catalog_metadata(
                session, plans.catalog_ids_to_drop
            )

        if plans.metric_ids_to_drop:
            await self._semantic_assets_writer.delete_metric_metadata(
                session, plans.metric_ids_to_drop
            )
        if plans.wordroot_ids_to_drop:
            await self._semantic_assets_writer.delete_wordroot_metadata(
                session, plans.wordroot_ids_to_drop
            )
        if plans.modifier_ids_to_drop:
            await self._semantic_assets_writer.delete_modifier_metadata(
                session, plans.modifier_ids_to_drop
            )
        if plans.unit_ids_to_drop:
            await self._semantic_assets_writer.delete_unit_metadata(session, plans.unit_ids_to_drop)
        if plans.valuedomain_ids_to_drop:
            await self._semantic_assets_writer.delete_valuedomain_metadata(
                session, plans.valuedomain_ids_to_drop
            )

        if plans.tag_ids_to_drop:
            await self._tag_writer.delete_tags(session, plans.tag_ids_to_drop)

    async def _apply_upserts(self, session: AsyncSession, plans: MetadataWritePlans) -> None:
        if plans.catalog_plans:
            await self._physical_assets_writer.write_catalog_metadata(session, plans.catalog_plans)
        if plans.schema_plans:
            await self._physical_assets_writer.write_schema_metadata(session, plans.schema_plans)
        if plans.table_plans:
            await self._physical_assets_writer.write_table_metadata(session, plans.table_plans)
        if plans.alter_table_plans:
            await self._physical_assets_writer.handle_alter_table(session, plans.alter_table_plans)

        if plans.metric_plans:
            await self._semantic_assets_writer.write_metrics(session, plans.metric_plans)
        if plans.wordroot_nodes:
            await self._semantic_assets_writer.write_wordroot_nodes(session, plans.wordroot_nodes)
        if plans.modifier_nodes:
            await self._semantic_assets_writer.write_modifier_nodes(session, plans.modifier_nodes)
        if plans.unit_nodes:
            await self._semantic_assets_writer.write_unit_nodes(session, plans.unit_nodes)
        if plans.valuedomain_nodes:
            await self._semantic_assets_writer.write_valuedomain_nodes(
                session, plans.valuedomain_nodes
            )

        if plans.tag_plans:
            await self._tag_writer.write_tags(
                session, plans.tag_plans, created_by="openlineage_event"
            )

    @staticmethod
    def _is_empty(plans: MetadataWritePlans) -> bool:
        return not any(
            (
                plans.catalog_plans,
                plans.schema_plans,
                plans.table_plans,
                plans.alter_table_plans,
                plans.metric_plans,
                plans.wordroot_nodes,
                plans.modifier_nodes,
                plans.unit_nodes,
                plans.valuedomain_nodes,
                plans.tag_plans,
                plans.table_ids_to_drop,
                plans.schema_ids_to_drop,
                plans.catalog_ids_to_drop,
                plans.metric_ids_to_drop,
                plans.wordroot_ids_to_drop,
                plans.modifier_ids_to_drop,
                plans.unit_ids_to_drop,
                plans.valuedomain_ids_to_drop,
                plans.tag_ids_to_drop,
            )
        )

    async def write(self, session: AsyncSession, plans: MetadataWritePlans) -> None:
        """写入元数据节点（不写关系）"""
        await self._apply_deletes(session, plans)
        await self._apply_upserts(session, plans)

        if self._is_empty(plans):
            logger.debug(
                "skip_empty_metadata_plans",
                extra={"data": {"operation": plans.operation}},
            )

    async def write_metric(self, session: AsyncSession, metric: MetricDTO) -> None:
        """写入 Metric 节点"""
        await self._semantic_assets_writer.write_metric(session, metric)

    def get_detailed_stats(self) -> dict:
        """获取详细统计"""
        stats = self.get_stats().to_dict()
        stats["catalogs_written"] = self._physical_assets_writer.catalogs_written
        stats["schemas_written"] = self._physical_assets_writer.schemas_written
        stats["tables_written"] = self._physical_assets_writer.tables_written
        stats["columns_written"] = self._physical_assets_writer.columns_written
        stats["metrics_written"] = self._semantic_assets_writer.metrics_written
        stats["tags_written"] = self._tag_writer.tags_written
        stats["embedding_tasks_queued"] = self._embedding_tasks_queued
        stats["tag_embedding_tasks_queued"] = self._tag_embedding_tasks_queued
        return stats
