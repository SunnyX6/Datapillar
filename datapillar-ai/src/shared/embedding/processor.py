# @author Sunny
# @date 2026-01-27

"""
Embedding batch processor(share)

After the metadata is written,the nodes that need to be vectorized are put into the queue.,
Bulk consumption,call embedding API,write back Neo4j
"""

from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

from src.infrastructure.llm.embeddings import UnifiedEmbedder
from src.infrastructure.repository.neo4j_uow import neo4j_async_session
from src.shared.config.runtime import get_default_tenant_id
from src.shared.utils.event_queue import AsyncEventQueue, QueueConfig

logger = logging.getLogger(__name__)


@dataclass
class EmbeddingTask:
    """Embedding Task"""

    tenant_id: int
    node_id: str
    node_label: str
    text: str


@dataclass
class ProcessorStats:
    """Processor statistics"""

    total_embedded: int = 0
    total_failed: int = 0
    start_time: datetime = field(default_factory=lambda: datetime.now(UTC))
    last_batch_time: datetime | None = None

    def to_dict(self) -> dict[str, Any]:
        uptime = (datetime.now(UTC) - self.start_time).total_seconds()
        return {
            "total_embedded": self.total_embedded,
            "total_failed": self.total_failed,
            "uptime_seconds": round(uptime, 2),
            "last_batch_time": self.last_batch_time.isoformat() if self.last_batch_time else None,
        }


class EmbeddingProcessor:
    """
    Embedding batch processor

    Processing flow:1.receive embedding Task enqueue
    2.batch call embedding API(Triggered when the quantity reaches the threshold or timeout)
    3.Batch writeback Neo4j
    """

    def __init__(self, *, metadata_repo: Any):
        self._metadata_repo = metadata_repo
        self._embedder_cache: dict[int, UnifiedEmbedder] = {}
        self._executor = ThreadPoolExecutor(max_workers=4)
        self._stats = ProcessorStats()

        # from embedder Get batch size
        batch_size = 20
        try:
            embedder = self._get_embedder(get_default_tenant_id())
            batch_size = embedder.batch_size
        except Exception as exc:
            logger.debug(
                "embedding_processor_get_batch_size_failed",
                extra={"data": {"error": str(exc)}},
            )

        # Initialize queue
        queue_config = QueueConfig(
            max_size=10000,
            batch_size=batch_size,
            flush_interval_seconds=3.0,
        )
        self._queue = AsyncEventQueue(config=queue_config)
        self._queue.set_processor(self._process_batch)

        logger.info(
            "embedding_processor_initialized",
            extra={
                "data": {
                    "batch_size": batch_size,
                    "flush_interval": queue_config.flush_interval_seconds,
                }
            },
        )

    def _get_embedder(self, tenant_id: int) -> UnifiedEmbedder:
        """Lazy loading Embedder(Cache by tenant)"""
        embedder = self._embedder_cache.get(tenant_id)
        if embedder is None:
            embedder = UnifiedEmbedder(tenant_id)
            self._embedder_cache[tenant_id] = embedder
        return embedder

    async def start(self) -> None:
        """Start the processor"""
        await self._queue.start()
        logger.info("embedding_processor_started")

    async def stop(self, timeout: float = 30.0) -> None:
        """stop processor"""
        await self._queue.stop(timeout=timeout)
        self._executor.shutdown(wait=False)
        logger.info(
            "embedding_processor_stopped",
            extra={"data": {"stats": self._stats.to_dict()}},
        )

    async def put(
        self,
        node_id: str,
        node_label: str,
        text: str,
        *,
        tenant_id: int | None = None,
    ) -> bool:
        """
        Add task to queue

        Args:node_id:Neo4j node ID
        node_label:node label(Table,Column,Metric Wait)
        text:Text that needs to be vectorized
        """
        resolved_tenant_id = tenant_id or get_default_tenant_id()
        task = EmbeddingTask(
            tenant_id=resolved_tenant_id,
            node_id=node_id,
            node_label=node_label,
            text=text,
        )
        return await self._queue.put(task)

    async def put_batch(
        self,
        items: list[tuple[str, str, str]],
        *,
        tenant_id: int | None = None,
    ) -> int:
        """
        Add tasks in batches

        Args:items:[(node_id,node_label,text),...]

        Returns:Successfully added quantity
        """
        success_count = 0
        resolved_tenant_id = tenant_id or get_default_tenant_id()
        for node_id, node_label, text in items:
            task = EmbeddingTask(
                tenant_id=resolved_tenant_id,
                node_id=node_id,
                node_label=node_label,
                text=text,
            )
            if await self._queue.put(task):
                success_count += 1
        return success_count

    @property
    def stats(self) -> dict[str, Any]:
        """Get statistics"""
        result = self._stats.to_dict()
        queue_stats = self._queue.stats
        result["queue"] = {
            "current_size": queue_stats.current_size,
            "total_received": queue_stats.total_received,
            "total_processed": queue_stats.total_processed,
            "total_failed": queue_stats.total_failed,
        }
        return result

    async def _process_batch(self, tasks: list[EmbeddingTask]) -> None:
        """Batch processing embedding Task"""
        if not tasks:
            return

        logger.debug(
            "embedding_batch_start",
            extra={"data": {"count": len(tasks)}},
        )

        try:
            loop = asyncio.get_event_loop()

            # Group processing by tenant,Avoid mixing models and keys between different tenants
            tasks_by_tenant: dict[int, list[EmbeddingTask]] = {}
            for task in tasks:
                tasks_by_tenant.setdefault(task.tenant_id, []).append(task)

            for tenant_id, group in tasks_by_tenant.items():
                texts = [task.text for task in group]
                embedder = self._get_embedder(tenant_id)
                embeddings = await loop.run_in_executor(self._executor, embedder.embed_batch, texts)
                await self._write_embeddings(embedder, group, embeddings)

            self._stats.total_embedded += len(tasks)
            self._stats.last_batch_time = datetime.now(UTC)

            logger.info(
                "embedding_batch_complete",
                extra={"data": {"count": len(tasks)}},
            )

        except Exception as e:
            self._stats.total_failed += len(tasks)
            logger.error(
                "embedding_batch_failed",
                extra={"data": {"count": len(tasks), "error": str(e)}},
            )
            raise

    async def _write_embeddings(
        self,
        embedder: UnifiedEmbedder,
        tasks: list[EmbeddingTask],
        embeddings: list[list[float]],
    ) -> None:
        """Batch writeback embedding Arrive Neo4j(Simultaneously record the model provider for incremental detection)"""
        # record provider,Format:provider/model_name,Used to detect model changes
        embedding_provider = f"{embedder.provider}/{embedder.model_name}"

        async with neo4j_async_session() as session:
            # press label Group,use UNWIND Batch update
            label_groups: dict[str, list[tuple[str, list[float]]]] = {}
            for task, embedding in zip(tasks, embeddings, strict=False):
                if task.node_label not in label_groups:
                    label_groups[task.node_label] = []
                label_groups[task.node_label].append((task.node_id, embedding))

            for label, items in label_groups.items():
                data = [{"id": node_id, "embedding": emb} for node_id, emb in items]
                await self._metadata_repo.write_embeddings_batch(
                    session,
                    node_label=label,
                    data=data,
                    provider=embedding_provider,
                )

            logger.debug(
                "embeddings_written_to_neo4j",
                provider=embedding_provider,
                groups={label: len(items) for label, items in label_groups.items()},
            )
