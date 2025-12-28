"""
OpenLineage 事件处理器

接收事件 → 队列缓冲 → 批量写入 Neo4j
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import structlog

from src.infrastructure.database.neo4j import AsyncNeo4jClient
from src.modules.openlineage.config import OpenLineageSinkConfig
from src.modules.openlineage.core.queue import AsyncEventQueue, QueueConfig
from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.writers.lineage_writer import LineageWriter
from src.modules.openlineage.writers.metadata_writer import MetadataWriter
from src.shared.config import settings

logger = structlog.get_logger()


@dataclass
class ProcessorStats:
    """处理器统计"""

    events_received: int = 0
    events_queued: int = 0
    events_processed: int = 0
    events_failed: int = 0
    start_time: datetime = field(default_factory=datetime.utcnow)

    def to_dict(self) -> dict[str, Any]:
        uptime = (datetime.utcnow() - self.start_time).total_seconds()
        return {
            "events_received": self.events_received,
            "events_queued": self.events_queued,
            "events_processed": self.events_processed,
            "events_failed": self.events_failed,
            "uptime_seconds": round(uptime, 2),
        }


class EventProcessor:
    """
    OpenLineage 事件处理器

    处理流程：
    1. 接收事件入队
    2. 批量处理：MetadataWriter 写入元数据，LineageWriter 写入血缘
    3. 支持暂停/恢复（启动时暂停，等待 Gravitino 同步完成后恢复）
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

        self._config = OpenLineageSinkConfig.from_settings(settings)
        self._stats = ProcessorStats()

        # 初始化队列
        queue_config = QueueConfig(
            max_size=self._config.queue.max_size,
            batch_size=self._config.queue.batch_size,
            flush_interval_seconds=self._config.queue.flush_interval_seconds,
        )
        self._queue = AsyncEventQueue(config=queue_config)
        self._queue.set_processor(self._process_batch)

        # 初始化写入器
        self._metadata_writer = MetadataWriter()
        self._lineage_writer = LineageWriter()

        self._initialized = True
        logger.info(
            "event_processor_initialized",
            config=self._config.model_dump(),
        )

    async def start(self, paused: bool = True) -> None:
        """
        启动处理器

        Args:
            paused: 是否以暂停状态启动（等待 Gravitino 同步完成后恢复）
        """
        if paused:
            self._queue.pause()
        await self._queue.start()
        logger.info("event_processor_started", paused=paused)

    async def stop(self, timeout: float = 30.0) -> None:
        """停止处理器"""
        await self._queue.stop(timeout=timeout)
        logger.info("event_processor_stopped", stats=self._stats.to_dict())

    async def put(self, event: RunEvent) -> dict[str, Any]:
        """
        接收事件入队

        Returns:
            处理结果
        """
        self._stats.events_received += 1

        if not await self._queue.put(event):
            return {"success": False, "error": "Queue full"}

        self._stats.events_queued += 1
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

        return result

    async def _process_batch(self, events: list[RunEvent]) -> None:
        """批量处理事件"""
        driver = await AsyncNeo4jClient.get_driver()

        for event in events:
            try:
                async with driver.session(database=settings.neo4j_database) as session:
                    # 1. 先写入元数据节点
                    await self._metadata_writer.write(session, event)
                    # 2. 再写入血缘关系
                    await self._lineage_writer.write(session, event)

                self._stats.events_processed += 1
                logger.debug("event_processed", job=event.job.name)

            except Exception as e:
                self._stats.events_failed += 1
                logger.error("event_process_failed", job=event.job.name, error=str(e))


# 全局单例
event_processor = EventProcessor()
