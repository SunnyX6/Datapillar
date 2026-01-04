"""
OpenLineage 语义资产写入器

范围：
- Metric 节点（不写 Schema->Metric、Metric 父子关系）
- WordRoot / Modifier / Unit / ValueDomain

约束：
- 不直接执行 Cypher（必须走 Repository）
- 不处理 tags（associate_tags 独立由 TagWriter 处理）
"""

from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import OpenLineageMetadataRepository
from src.modules.openlineage.parsers.plans.metadata import MetricWritePlan
from src.modules.openlineage.schemas.neo4j import (
    MetricNode,
    ModifierNode,
    UnitNode,
    ValueDomainNode,
    WordRootNode,
    get_metric_label,
)
from src.modules.openlineage.writers.metadata.physical_assets_writer import PhysicalAssetsWriter
from src.modules.openlineage.writers.metadata.types import QueueEmbeddingTask

logger = structlog.get_logger()


class SemanticAssetsWriter:
    # 指标相关的操作
    METRIC_OPERATIONS = {"register_metric", "alter_metric"}
    # 语义层操作
    WORDROOT_OPERATIONS = {"create_wordroot", "alter_wordroot"}
    MODIFIER_OPERATIONS = {"create_modifier", "alter_modifier"}
    UNIT_OPERATIONS = {"create_unit", "alter_unit"}
    VALUEDOMAIN_OPERATIONS = {"create_valuedomain", "alter_valuedomain"}
    # 删除操作（统一用 drop）
    DROP_METRIC_OPERATIONS = {"drop_metric"}
    DROP_WORDROOT_OPERATIONS = {"drop_wordroot"}
    DROP_MODIFIER_OPERATIONS = {"drop_modifier"}
    DROP_UNIT_OPERATIONS = {"drop_unit"}
    DROP_VALUEDOMAIN_OPERATIONS = {"drop_valuedomain"}

    def __init__(
        self,
        *,
        queue_embedding_task: QueueEmbeddingTask,
        physical_assets_writer: PhysicalAssetsWriter,
    ) -> None:
        self._queue_embedding_task = queue_embedding_task
        self._physical_assets_writer = physical_assets_writer
        self._metrics_written = 0

    @property
    def metrics_written(self) -> int:
        return self._metrics_written

    async def delete_metric_metadata(self, session: AsyncSession, metric_ids: list[str]) -> None:
        """
        删除 Metric 元数据

        事件格式：
        - namespace: gravitino://{metalake}/{catalog}
        - name: {schema}.{metric}
        """
        for metric_id in metric_ids:
            await OpenLineageMetadataRepository.delete_metric(session, metric_id=metric_id)
            logger.info("metric_deleted", metric_id=metric_id)

    async def delete_wordroot_metadata(
        self, session: AsyncSession, wordroot_ids: list[str]
    ) -> None:
        """删除 WordRoot 元数据"""
        for wordroot_id in wordroot_ids:
            await OpenLineageMetadataRepository.delete_node(
                session,
                node_id=wordroot_id,
                node_label="WordRoot",
            )
            logger.info("wordroot_deleted", wordroot_id=wordroot_id)

    async def delete_modifier_metadata(
        self, session: AsyncSession, modifier_ids: list[str]
    ) -> None:
        """删除 Modifier 元数据"""
        for modifier_id in modifier_ids:
            await OpenLineageMetadataRepository.delete_node(
                session,
                node_id=modifier_id,
                node_label="Modifier",
            )
            logger.info("modifier_deleted", modifier_id=modifier_id)

    async def delete_unit_metadata(self, session: AsyncSession, unit_ids: list[str]) -> None:
        """删除 Unit 元数据"""
        for unit_id in unit_ids:
            await OpenLineageMetadataRepository.delete_node(
                session,
                node_id=unit_id,
                node_label="Unit",
            )
            logger.info("unit_deleted", unit_id=unit_id)

    async def delete_valuedomain_metadata(
        self, session: AsyncSession, valuedomain_ids: list[str]
    ) -> None:
        """删除 ValueDomain 元数据"""
        for valuedomain_id in valuedomain_ids:
            await OpenLineageMetadataRepository.delete_node(
                session,
                node_id=valuedomain_id,
                node_label="ValueDomain",
            )
            logger.info(
                "valuedomain_deleted",
                valuedomain_id=valuedomain_id,
            )

    async def write_metrics(self, session: AsyncSession, plans: list[MetricWritePlan]) -> None:
        """写入 Metric 节点（plans 由 parser 预先生成）"""
        for plan in plans:
            await self._physical_assets_writer.write_catalog(session, plan.catalog)
            await self._physical_assets_writer.write_schema(session, plan.schema)
            await self._write_metric_schema(
                session,
                metric=plan.metric,
                metric_label=plan.metric_label,
            )

    async def _write_metric_schema(
        self,
        session: AsyncSession,
        metric: MetricNode,
        metric_label: str,
    ) -> None:
        """写入 Metric 节点（不写 Schema->Metric、Metric 父子关系）"""
        await OpenLineageMetadataRepository.upsert_metric_event(
            session,
            label=metric_label,
            id=metric.id,
            name=metric.name,
            code=metric.code,
            description=metric.description,
            unit=metric.unit,
            aggregation_logic=metric.aggregation_logic,
            calculation_formula=metric.calculation_formula,
            created_by="OPENLINEAGE",
        )

        # 将 Metric embedding 任务入队
        await self._queue_embedding_task(metric.id, metric_label, metric.name, metric.description)

        self._metrics_written += 1
        logger.debug("metric_written", id=metric.id, name=metric.name)

    async def write_wordroot_nodes(self, session: AsyncSession, nodes: list[WordRootNode]) -> None:
        """写入词根节点"""
        for node in nodes:
            await self._write_wordroot(session, node)

    async def _write_wordroot(self, session: AsyncSession, wordroot: WordRootNode) -> None:
        """写入 WordRoot 节点"""
        await OpenLineageMetadataRepository.upsert_wordroot(
            session,
            id=wordroot.id,
            code=wordroot.code,
            name=wordroot.name,
            data_type=wordroot.data_type,
            description=wordroot.description,
            created_by="OPENLINEAGE",
        )

        # 将 WordRoot embedding 任务入队
        await self._queue_embedding_task(
            wordroot.id, "WordRoot", wordroot.name or wordroot.code, wordroot.description
        )

        logger.debug("wordroot_written", id=wordroot.id, code=wordroot.code)

    async def write_modifier_nodes(self, session: AsyncSession, nodes: list[ModifierNode]) -> None:
        """写入修饰符节点"""
        for node in nodes:
            await self._write_modifier(session, node)

    async def _write_modifier(self, session: AsyncSession, modifier: ModifierNode) -> None:
        """写入 Modifier 节点"""
        await OpenLineageMetadataRepository.upsert_modifier(
            session,
            id=modifier.id,
            code=modifier.code,
            modifier_type=modifier.modifier_type,
            description=modifier.description,
            created_by="OPENLINEAGE",
        )

        # 将 Modifier embedding 任务入队
        await self._queue_embedding_task(
            modifier.id, "Modifier", modifier.code, modifier.description
        )

        logger.debug("modifier_written", id=modifier.id, code=modifier.code)

    async def write_unit_nodes(self, session: AsyncSession, nodes: list[UnitNode]) -> None:
        """写入单位节点"""
        for node in nodes:
            await self._write_unit(session, node)

    async def _write_unit(self, session: AsyncSession, unit: UnitNode) -> None:
        """写入 Unit 节点"""
        await OpenLineageMetadataRepository.upsert_unit(
            session,
            id=unit.id,
            code=unit.code,
            name=unit.name,
            symbol=unit.symbol,
            description=unit.description,
            created_by="OPENLINEAGE",
        )

        # 将 Unit embedding 任务入队
        await self._queue_embedding_task(unit.id, "Unit", unit.name or unit.code, unit.description)

        logger.debug("unit_written", id=unit.id, code=unit.code)

    async def write_valuedomain_nodes(
        self, session: AsyncSession, nodes: list[ValueDomainNode]
    ) -> None:
        """写入值域节点"""
        for node in nodes:
            await self._write_valuedomain(session, node)

    async def _write_valuedomain(self, session: AsyncSession, valuedomain: ValueDomainNode) -> None:
        """写入 ValueDomain 节点"""
        await OpenLineageMetadataRepository.upsert_valuedomain(
            session,
            id=valuedomain.id,
            domain_code=valuedomain.domain_code,
            domain_name=valuedomain.domain_name,
            domain_type=valuedomain.domain_type,
            domain_level=valuedomain.domain_level,
            items=valuedomain.items,
            data_type=valuedomain.data_type,
            description=valuedomain.description,
            created_by="OPENLINEAGE",
        )

        # 将 ValueDomain embedding 任务入队（包含 items 信息）
        embedding_text = valuedomain.domain_name or valuedomain.domain_code
        if valuedomain.items:
            embedding_text += f" {valuedomain.items}"
        await self._queue_embedding_task(
            valuedomain.id, "ValueDomain", embedding_text, valuedomain.description
        )

        logger.debug("valuedomain_written", id=valuedomain.id, domainCode=valuedomain.domain_code)

    async def write_metric(self, session: AsyncSession, metric: MetricNode) -> None:
        """写入 Metric 节点（不写任何关系）"""
        label = get_metric_label(metric.metric_type)
        await OpenLineageMetadataRepository.upsert_metric_event(
            session,
            label=label,
            id=metric.id,
            name=metric.name,
            code=metric.code,
            description=metric.description,
            unit=metric.unit,
            aggregation_logic=metric.aggregation_logic,
            calculation_formula=metric.calculation_formula,
            created_by="OPENLINEAGE",
        )

        # 将 Metric embedding 任务入队
        await self._queue_embedding_task(metric.id, label, metric.name, metric.description)

        self._metrics_written += 1
        logger.debug("metric_written", id=metric.id, name=metric.name)
