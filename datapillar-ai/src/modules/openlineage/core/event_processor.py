# @author Sunny
# @date 2026-01-27

"""
OpenLineage 事件处理器

接收事件 → 队列缓冲 → 批量写入 Neo4j
"""

import logging
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

from src.infrastructure.repository.neo4j_uow import neo4j_async_session
from src.modules.openlineage.core.queue import AsyncEventQueue, QueueConfig
from src.modules.openlineage.parsers.plans import OpenLineagePlanBuilder
from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.writers.lineage_writer import LineageWriter
from src.modules.openlineage.writers.metadata_writer import MetadataWriter
from src.shared.config.runtime import get_runtime_config
from src.shared.context import reset_request_scope, set_request_scope

logger = logging.getLogger(__name__)


@dataclass
class ProcessorStats:
    """处理器统计"""

    events_received: int = 0
    events_queued: int = 0
    events_processed: int = 0
    events_failed: int = 0
    start_time: datetime = field(default_factory=lambda: datetime.now(UTC))

    def to_dict(self) -> dict[str, Any]:
        uptime = (datetime.now(UTC) - self.start_time).total_seconds()
        return {
            "events_received": self.events_received,
            "events_queued": self.events_queued,
            "events_processed": self.events_processed,
            "events_failed": self.events_failed,
            "uptime_seconds": round(uptime, 2),
        }


@dataclass(frozen=True)
class QueuedOpenLineageEvent:
    """OpenLineage 入队事件（含租户与操作人上下文）。"""

    tenant_id: int
    operator_user_id: int
    event: RunEvent


class EventProcessor:
    """
    OpenLineage 事件处理器

    处理流程：
    1. 接收事件入队
    2. 批量处理：MetadataWriter 写入元数据，LineageWriter 写入血缘
    3. 支持暂停/恢复（同步期间可暂停，完成后恢复）
    """

    _instance: "EventProcessor | None" = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return

        self._config = get_runtime_config().openlineage_sink
        self._stats = ProcessorStats()
        self._tenant_stats: dict[int, ProcessorStats] = {}

        # 初始化队列
        queue_config = QueueConfig(
            max_size=self._config.queue.max_size,
            batch_size=self._config.queue.batch_size,
            flush_interval_seconds=self._config.queue.flush_interval_seconds,
        )
        self._queue = AsyncEventQueue(config=queue_config)
        self._queue.set_processor(self._process_batch)

        # 初始化写入器
        self._plan_builder = OpenLineagePlanBuilder()
        self._metadata_writer = MetadataWriter()
        self._lineage_writer = LineageWriter()

        self._initialized = True
        logger.info(
            "event_processor_initialized",
            extra={"data": {"config": self._config.model_dump()}},
        )

    def _get_or_create_tenant_stats(self, tenant_id: int) -> ProcessorStats:
        tenant_stats = self._tenant_stats.get(tenant_id)
        if tenant_stats is None:
            tenant_stats = ProcessorStats()
            self._tenant_stats[tenant_id] = tenant_stats
        return tenant_stats

    async def start(self, paused: bool = True) -> None:
        """
        启动处理器

        Args:
            paused: 是否以暂停状态启动（等待 Gravitino 同步完成后恢复）
        """
        if paused:
            self._queue.pause()
        await self._queue.start()
        logger.info(
            "event_processor_started",
            extra={"data": {"paused": paused}},
        )

    async def stop(self, timeout: float = 30.0) -> None:
        """停止处理器"""
        await self._queue.stop(timeout=timeout)
        logger.info(
            "event_processor_stopped",
            extra={"data": {"stats": self._stats.to_dict()}},
        )

    async def put(self, queued_event: QueuedOpenLineageEvent) -> dict[str, Any]:
        """
        接收事件入队

        Returns:
            处理结果
        """
        self._stats.events_received += 1
        tenant_stats = self._get_or_create_tenant_stats(queued_event.tenant_id)
        tenant_stats.events_received += 1

        if not await self._queue.put(queued_event):
            return {"success": False, "error": "Queue full"}

        self._stats.events_queued += 1
        tenant_stats.events_queued += 1
        return {
            "success": True,
            "queued": True,
            "queue_size": self._queue.stats.current_size,
        }

    def pause(self) -> None:
        """暂停处理（事件仍可入队，但不会被处理）"""
        self._queue.pause()

    def resume(self) -> None:
        """恢复处理"""
        self._queue.resume()

    @property
    def is_paused(self) -> bool:
        """是否处于暂停状态"""
        return self._queue.is_paused

    @property
    def stats(self) -> dict[str, Any]:
        """获取统计信息"""
        result = self._stats.to_dict()

        queue_stats = self._queue.stats
        result["queue"] = {
            "current_size": queue_stats.current_size,
            "total_received": queue_stats.total_received,
            "total_processed": queue_stats.total_processed,
            "total_failed": queue_stats.total_failed,
        }

        result["metadata_writer"] = self._metadata_writer.get_detailed_stats()
        result["lineage_writer"] = self._lineage_writer.get_detailed_stats()
        result["tenants"] = {
            str(tenant_id): tenant_stats.to_dict()
            for tenant_id, tenant_stats in sorted(self._tenant_stats.items())
        }

        return result

    def get_tenant_stats(self, tenant_id: int) -> dict[str, Any]:
        """获取租户维度统计。"""
        tenant_stats = self._get_or_create_tenant_stats(tenant_id)
        result = tenant_stats.to_dict()
        queue_stats = self._queue.stats
        result["queue"] = {
            "current_size": queue_stats.current_size,
            "total_received": queue_stats.total_received,
            "total_processed": queue_stats.total_processed,
            "total_failed": queue_stats.total_failed,
        }
        return result

    async def _process_batch(self, events: list[QueuedOpenLineageEvent]) -> None:
        """批量处理事件"""
        for queued_event in events:
            event = queued_event.event
            tenant_id = queued_event.tenant_id
            tenant_stats = self._get_or_create_tenant_stats(tenant_id)
            token = set_request_scope(tenant_id, queued_event.operator_user_id)
            try:
                async with neo4j_async_session() as session:
                    plans = self._plan_builder.build(event)
                    # 1. 先写入元数据节点
                    await self._metadata_writer.write(session, plans.metadata)
                    # 2. 再写入关系（结构边 + 血缘边）
                    await self._lineage_writer.write(session, plans.lineage)

                self._stats.events_processed += 1
                tenant_stats.events_processed += 1
                logger.debug(
                    "event_processed",
                    extra={
                        "data": {
                            "job": event.job.name,
                            "tenant_id": tenant_id,
                            "operator_user_id": queued_event.operator_user_id,
                        }
                    },
                )

            except Exception as e:
                self._stats.events_failed += 1
                tenant_stats.events_failed += 1
                logger.error(
                    "event_process_failed",
                    extra={
                        "data": {
                            "job": event.job.name,
                            "tenant_id": tenant_id,
                            "operator_user_id": queued_event.operator_user_id,
                            "error": str(e),
                        }
                    },
                )
            finally:
                reset_request_scope(token)


_event_processor: EventProcessor | None = None


def get_event_processor() -> EventProcessor:
    """获取 EventProcessor 单例（延迟初始化）"""
    global _event_processor
    if _event_processor is None:
        _event_processor = EventProcessor()
    return _event_processor
