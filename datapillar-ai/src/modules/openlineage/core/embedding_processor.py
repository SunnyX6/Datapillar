"""
Embedding 批量处理器

元数据写入后将需要向量化的节点放入队列，
批量消费、调用 embedding API、回写 Neo4j
"""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import structlog

from src.infrastructure.database.neo4j import AsyncNeo4jClient
from src.infrastructure.llm.embeddings import UnifiedEmbedder
from src.modules.openlineage.core.queue import AsyncEventQueue, QueueConfig
from src.shared.config import settings

logger = structlog.get_logger()


@dataclass
class EmbeddingTask:
    """Embedding 任务"""

    node_id: str
    node_label: str
    text: str


@dataclass
class ProcessorStats:
    """处理器统计"""

    total_embedded: int = 0
    total_failed: int = 0
    start_time: datetime = field(default_factory=datetime.utcnow)
    last_batch_time: datetime | None = None

    def to_dict(self) -> dict[str, Any]:
        uptime = (datetime.utcnow() - self.start_time).total_seconds()
        return {
            "total_embedded": self.total_embedded,
            "total_failed": self.total_failed,
            "uptime_seconds": round(uptime, 2),
            "last_batch_time": self.last_batch_time.isoformat() if self.last_batch_time else None,
        }


class EmbeddingProcessor:
    """
    Embedding 批量处理器

    处理流程：
    1. 接收 embedding 任务入队
    2. 批量调用 embedding API（数量达到阈值或超时触发）
    3. 批量回写 Neo4j
    """

    _instance: "EmbeddingProcessor | None" = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return

        self._embedder: UnifiedEmbedder | None = None
        self._executor = ThreadPoolExecutor(max_workers=4)
        self._stats = ProcessorStats()

        # 从 embedder 获取批量大小
        batch_size = 20
        try:
            embedder = self._get_embedder()
            batch_size = embedder.batch_size
        except Exception:
            pass

        # 初始化队列
        queue_config = QueueConfig(
            max_size=10000,
            batch_size=batch_size,
            flush_interval_seconds=3.0,
        )
        self._queue = AsyncEventQueue(config=queue_config)
        self._queue.set_processor(self._process_batch)

        self._initialized = True
        logger.info(
            "embedding_processor_initialized",
            batch_size=batch_size,
            flush_interval=queue_config.flush_interval_seconds,
        )

    def _get_embedder(self) -> UnifiedEmbedder:
        """懒加载 Embedder"""
        if self._embedder is None:
            self._embedder = UnifiedEmbedder()
        return self._embedder

    async def start(self) -> None:
        """启动处理器"""
        await self._queue.start()
        logger.info("embedding_processor_started")

    async def stop(self, timeout: float = 30.0) -> None:
        """停止处理器"""
        await self._queue.stop(timeout=timeout)
        self._executor.shutdown(wait=False)
        logger.info("embedding_processor_stopped", stats=self._stats.to_dict())

    async def put(self, node_id: str, node_label: str, text: str) -> bool:
        """
        添加任务到队列

        Args:
            node_id: Neo4j 节点 ID
            node_label: 节点标签（Table, Column, Metric 等）
            text: 需要向量化的文本
        """
        task = EmbeddingTask(node_id=node_id, node_label=node_label, text=text)
        return await self._queue.put(task)

    async def put_batch(self, items: list[tuple[str, str, str]]) -> int:
        """
        批量添加任务

        Args:
            items: [(node_id, node_label, text), ...]

        Returns:
            成功添加的数量
        """
        success_count = 0
        for node_id, node_label, text in items:
            task = EmbeddingTask(node_id=node_id, node_label=node_label, text=text)
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

        logger.debug("embedding_batch_start", count=len(tasks))

        try:
            # 1. 提取文本列表
            texts = [task.text for task in tasks]

            # 2. 批量调用 embedding API（在线程池中执行）
            loop = asyncio.get_event_loop()
            embedder = self._get_embedder()
            embeddings = await loop.run_in_executor(
                self._executor, embedder.embed_batch, texts
            )

            # 3. 批量回写 Neo4j
            await self._write_embeddings_to_neo4j(tasks, embeddings)

            self._stats.total_embedded += len(tasks)
            self._stats.last_batch_time = datetime.utcnow()

            logger.info("embedding_batch_complete", count=len(tasks))

        except Exception as e:
            self._stats.total_failed += len(tasks)
            logger.error("embedding_batch_failed", count=len(tasks), error=str(e))
            raise

    async def _write_embeddings_to_neo4j(
        self, tasks: list[EmbeddingTask], embeddings: list[list[float]]
    ) -> None:
        """批量回写 embedding 到 Neo4j"""
        driver = await AsyncNeo4jClient.get_driver()

        async with driver.session(database=settings.neo4j_database) as session:
            # 按 label 分组，使用 UNWIND 批量更新
            label_groups: dict[str, list[tuple[str, list[float]]]] = {}
            for task, embedding in zip(tasks, embeddings):
                if task.node_label not in label_groups:
                    label_groups[task.node_label] = []
                label_groups[task.node_label].append((task.node_id, embedding))

            for label, items in label_groups.items():
                data = [{"id": node_id, "embedding": emb} for node_id, emb in items]

                query = f"""
                UNWIND $data AS item
                MATCH (n:{label} {{id: item.id}})
                SET n.embedding = item.embedding, n.embeddingUpdatedAt = datetime()
                """

                await session.run(query, data=data)

            logger.debug(
                "embeddings_written_to_neo4j",
                groups={label: len(items) for label, items in label_groups.items()},
            )


# 全局单例
embedding_processor = EmbeddingProcessor()
