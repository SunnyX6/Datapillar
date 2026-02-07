# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Embedding 批量处理器（共享）

元数据写入后将需要向量化的节点放入队列，
批量消费、调用 embedding API、回写 Neo4j
"""

from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

from src.infrastructure.llm.embeddings import UnifiedEmbedder
from src.shared.config.runtime import get_default_tenant_id
from src.infrastructure.repository.neo4j_uow import neo4j_async_session
from src.shared.utils.event_queue import AsyncEventQueue, QueueConfig

logger = logging.getLogger(__name__)


@dataclass
class EmbeddingTask:
    """Embedding 任务"""

    tenant_id: int
    node_id: str
    node_label: str
    text: str


@dataclass
class ProcessorStats:
    """处理器统计"""

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
            "last_batch_time": self.last_batch_time.isoformat()
            if self.last_batch_time
            else None,
        }


class EmbeddingProcessor:
    """
    Embedding 批量处理器

    处理流程：
    1. 接收 embedding 任务入队
    2. 批量调用 embedding API（数量达到阈值或超时触发）
    3. 批量回写 Neo4j
    """

    def __init__(self, *, metadata_repo: Any):
        self._metadata_repo = metadata_repo
        self._embedder_cache: dict[int, UnifiedEmbedder] = {}
        self._executor = ThreadPoolExecutor(max_workers=4)
        self._stats = ProcessorStats()

        # 从 embedder 获取批量大小
        batch_size = 20
        try:
            embedder = self._get_embedder(get_default_tenant_id())
            batch_size = embedder.batch_size
        except Exception as exc:
            logger.debug(
                "embedding_processor_get_batch_size_failed",
                extra={"data": {"error": str(exc)}},
            )

        # 初始化队列
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
        """懒加载 Embedder（按租户缓存）"""
        embedder = self._embedder_cache.get(tenant_id)
        if embedder is None:
            embedder = UnifiedEmbedder(tenant_id)
            self._embedder_cache[tenant_id] = embedder
        return embedder

    async def start(self) -> None:
        """启动处理器"""
        await self._queue.start()
        logger.info("embedding_processor_started")

    async def stop(self, timeout: float = 30.0) -> None:
        """停止处理器"""
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
        添加任务到队列

        Args:
            node_id: Neo4j 节点 ID
            node_label: 节点标签（Table, Column, Metric 等）
            text: 需要向量化的文本
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
        批量添加任务

        Args:
            items: [(node_id, node_label, text), ...]

        Returns:
            成功添加的数量
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
        """获取统计信息"""
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
        """批量处理 embedding 任务"""
        if not tasks:
            return

        logger.debug(
            "embedding_batch_start",
            extra={"data": {"count": len(tasks)}},
        )

        try:
            loop = asyncio.get_event_loop()

            # 按租户分组处理，避免不同租户混用模型与密钥
            tasks_by_tenant: dict[int, list[EmbeddingTask]] = {}
            for task in tasks:
                tasks_by_tenant.setdefault(task.tenant_id, []).append(task)

            for tenant_id, group in tasks_by_tenant.items():
                texts = [task.text for task in group]
                embedder = self._get_embedder(tenant_id)
                embeddings = await loop.run_in_executor(
                    self._executor, embedder.embed_batch, texts
                )
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
        """批量回写 embedding 到 Neo4j（同时记录模型 provider 用于增量检测）"""
        # 记录 provider，格式：provider/model_name，用于检测模型变更
        embedding_provider = f"{embedder.provider}/{embedder.model_name}"

        async with neo4j_async_session() as session:
            # 按 label 分组，使用 UNWIND 批量更新
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
