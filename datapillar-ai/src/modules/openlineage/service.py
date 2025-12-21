"""
OpenLineage Sink 服务

Sink 端只负责：接收事件 → 队列缓冲 → 写入 Neo4j
retry、rate_limit、filter 由 Producer 端配置
"""

import asyncio
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import structlog

from src.infrastructure.database.neo4j import AsyncNeo4jClient
from src.modules.openlineage.config import OpenLineageSinkConfig
from src.modules.openlineage.core.queue import AsyncEventQueue
from src.modules.openlineage.core.queue import QueueConfig as CoreQueueConfig
from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.writers.lineage_writer import LineageWriter
from src.modules.openlineage.writers.metadata_writer import MetadataWriter
from src.shared.config import settings

logger = structlog.get_logger()


@dataclass
class SinkStats:
    """Sink 统计"""

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


class OpenLineageSinkService:
    """
    OpenLineage Sink 服务

    简化的事件处理服务：
    - 接收事件
    - 队列缓冲（二次保护）
    - 批量写入 Neo4j

    写入流程：
    1. MetadataWriter: 写入元数据节点（Catalog -> Schema -> Table -> Column）
    2. LineageWriter: 写入血缘关系（SQL、表级血缘、列级血缘）
    """

    _instance: "OpenLineageSinkService | None" = None
    _initialized: bool = False

    def __init__(self):
        self._config = OpenLineageSinkConfig.from_settings(settings)
        self._stats = SinkStats()
        self._queue: AsyncEventQueue | None = None
        self._metadata_writer: MetadataWriter | None = None
        self._lineage_writer: LineageWriter | None = None
        self._init_lock = asyncio.Lock()

    @classmethod
    def get_instance(cls) -> "OpenLineageSinkService":
        """获取单例实例"""
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    async def _ensure_initialized(self) -> None:
        """确保服务已初始化（懒加载）"""
        if self._initialized:
            return

        async with self._init_lock:
            if self._initialized:
                return

            logger.info("OpenLineage Sink 初始化中...")

            # 初始化写入器
            self._metadata_writer = MetadataWriter()
            self._lineage_writer = LineageWriter()

            # 初始化队列
            queue_config = CoreQueueConfig(
                max_size=self._config.queue.max_size,
                batch_size=self._config.queue.batch_size,
                flush_interval_seconds=self._config.queue.flush_interval_seconds,
            )
            self._queue = AsyncEventQueue(config=queue_config)
            self._queue.set_processor(self._process_batch)
            await self._queue.start()

            self._initialized = True
            logger.info("OpenLineage Sink 初始化完成", config=self._config.model_dump())

    async def receive_event(self, event: RunEvent) -> dict[str, Any]:
        """接收事件"""
        self._stats.events_received += 1

        # 懒加载初始化
        await self._ensure_initialized()

        # 入队（队列满时会拒绝）
        if not await self._queue.put(event):
            return {
                "success": False,
                "error": "Queue full",
            }

        self._stats.events_queued += 1
        return {
            "success": True,
            "queued": True,
            "queue_size": self._queue.stats.current_size,
        }

    async def _process_batch(self, events: list[Any]) -> None:
        """处理一批事件"""
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

    def get_stats(self) -> dict[str, Any]:
        """获取统计信息"""
        stats = self._stats.to_dict()

        if self._queue:
            queue_stats = self._queue.stats
            stats["queue"] = {
                "current_size": queue_stats.current_size,
                "total_received": queue_stats.total_received,
                "total_processed": queue_stats.total_processed,
                "total_failed": queue_stats.total_failed,
            }

        if self._metadata_writer:
            stats["metadata_writer"] = self._metadata_writer.get_detailed_stats()

        if self._lineage_writer:
            stats["lineage_writer"] = self._lineage_writer.get_detailed_stats()

        return stats
