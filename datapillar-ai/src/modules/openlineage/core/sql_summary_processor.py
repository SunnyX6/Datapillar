"""
SQL 摘要批量处理器

从 OpenLineage 事件解析的 SQL 可生成语义摘要，用于智能检索。
通过批量处理和去重机制控制 LLM 调用成本。

处理流程：
1. 接收 SQL 摘要任务入队
2. 批量调用 LLM 生成摘要（数量达到阈值或超时触发）
3. 批量回写 Neo4j（summary + embedding）
"""

import asyncio
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import UTC, datetime
from functools import lru_cache
from typing import Any

import structlog
import xxhash
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.providers.llm import LLMProvider
from pydantic import BaseModel, Field

from src.infrastructure.llm.embeddings import UnifiedEmbedder
from src.infrastructure.llm.config import get_datapillar_config
from src.infrastructure.repository.neo4j_uow import neo4j_async_session
from src.modules.openlineage.core.queue import AsyncEventQueue, QueueConfig
from src.shared.config.settings import settings

logger = structlog.get_logger()


@lru_cache(maxsize=1)
def _get_llm_provider() -> LLMProvider:
    config = get_datapillar_config()
    return LLMProvider(config.llm)


# ==================== 配置 ====================


@dataclass
class SQLSummaryConfig:
    """SQL 摘要生成配置"""

    enabled: bool = True
    batch_size: int = 5
    flush_interval_seconds: float = 300.0
    max_queue_size: int = 1000
    max_sql_length: int = 10000
    min_sql_length: int = 50

    @classmethod
    def from_settings(cls) -> "SQLSummaryConfig":
        """从配置文件加载"""
        cfg = settings.get("sql_summary", {})
        return cls(
            enabled=cfg.get("enabled", True),
            batch_size=cfg.get("batch_size", 5),
            flush_interval_seconds=cfg.get("flush_interval_seconds", 300.0),
            max_queue_size=cfg.get("max_queue_size", 1000),
            max_sql_length=cfg.get("max_sql_length", 10000),
            min_sql_length=cfg.get("min_sql_length", 50),
        )


# ==================== 数据结构 ====================


@dataclass
class SQLSummaryTask:
    """SQL 摘要任务"""

    sql_id: str
    sql_node_id: str
    sql_content: str
    input_tables: list[str]
    output_tables: list[str]
    dialect: str | None = None


class SQLSummaryResult(BaseModel):
    """单条 SQL 摘要结果"""

    index: int = Field(description="SQL 序号")
    summary: str = Field(description="语义摘要（1-2句话）")
    tags: str = Field(description="逗号分隔的标签")


class BatchSummaryResult(BaseModel):
    """批量摘要结果"""

    results: list[SQLSummaryResult] = Field(description="摘要结果列表")


@dataclass
class ProcessorStats:
    """处理器统计"""

    total_processed: int = 0
    total_failed: int = 0
    total_skipped: int = 0
    start_time: datetime = field(default_factory=lambda: datetime.now(UTC))
    last_batch_time: datetime | None = None

    def to_dict(self) -> dict[str, Any]:
        uptime = (datetime.now(UTC) - self.start_time).total_seconds()
        return {
            "total_processed": self.total_processed,
            "total_failed": self.total_failed,
            "total_skipped": self.total_skipped,
            "uptime_seconds": round(uptime, 2),
            "last_batch_time": (self.last_batch_time.isoformat() if self.last_batch_time else None),
        }


# ==================== 处理器 ====================


class SQLSummaryProcessor:
    """
    SQL 摘要批量处理器

    处理流程：
    1. 接收 SQL 摘要任务入队（自动去重）
    2. 批量调用 LLM 生成摘要（数量达到阈值或超时触发）
    3. 批量生成 embedding
    4. 批量回写 Neo4j
    """

    _instance: "SQLSummaryProcessor | None" = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return

        self._config = SQLSummaryConfig.from_settings()
        self._embedder: UnifiedEmbedder | None = None
        self._executor = ThreadPoolExecutor(max_workers=2)
        self._stats = ProcessorStats()
        self._processed_ids: set[str] = set()

        if not self._config.enabled:
            logger.info("sql_summary_processor_disabled")
            self._queue = None
            self._initialized = True
            return

        queue_config = QueueConfig(
            max_size=self._config.max_queue_size,
            batch_size=self._config.batch_size,
            flush_interval_seconds=self._config.flush_interval_seconds,
        )
        self._queue = AsyncEventQueue(config=queue_config)
        self._queue.set_processor(self._process_batch)

        self._initialized = True
        logger.info(
            "sql_summary_processor_initialized",
            batch_size=self._config.batch_size,
            flush_interval=self._config.flush_interval_seconds,
        )

    def _get_embedder(self) -> UnifiedEmbedder:
        """懒加载 Embedder"""
        if self._embedder is None:
            self._embedder = UnifiedEmbedder()
        return self._embedder

    @staticmethod
    def _normalize_sql(sql: str) -> str:
        """标准化 SQL（用于去重）"""
        s = re.sub(r"[\r\n\t]+", " ", sql)
        s = re.sub(r" +", " ", s)
        return s.strip().lower()

    @staticmethod
    def _sql_hash(sql: str) -> str:
        """生成 SQL 哈希（用于去重）"""
        normalized = SQLSummaryProcessor._normalize_sql(sql)
        return xxhash.xxh64(normalized.encode()).hexdigest()[:16]

    async def start(self) -> None:
        """启动处理器"""
        if self._queue:
            await self._queue.start()
            logger.info("sql_summary_processor_started")

    async def stop(self, timeout: float = 30.0) -> None:
        """停止处理器"""
        if self._queue:
            await self._queue.stop(timeout=timeout)
        self._executor.shutdown(wait=False)
        logger.info("sql_summary_processor_stopped", stats=self._stats.to_dict())

    async def enqueue(
        self,
        sql_node_id: str,
        sql_content: str,
        input_tables: list[str],
        output_tables: list[str],
        dialect: str | None = None,
    ) -> bool:
        """
        将 SQL 加入待处理队列

        Args:
            sql_node_id: Neo4j 中的 SQL 节点 ID
            sql_content: SQL 内容
            input_tables: 输入表列表
            output_tables: 输出表列表
            dialect: SQL 方言

        Returns:
            True=已入队, False=跳过（禁用/已存在/不符合条件）
        """
        if not self._config.enabled or not self._queue:
            return False

        # 长度检查
        sql_len = len(sql_content)
        if sql_len < self._config.min_sql_length:
            self._stats.total_skipped += 1
            logger.debug("sql_summary_skipped_too_short", length=sql_len)
            return False

        # 去重检查
        sql_id = self._sql_hash(sql_content)
        if sql_id in self._processed_ids:
            self._stats.total_skipped += 1
            return False

        # 截断过长的 SQL
        if sql_len > self._config.max_sql_length:
            sql_content = sql_content[: self._config.max_sql_length] + "\n-- [已截断]"

        task = SQLSummaryTask(
            sql_id=sql_id,
            sql_node_id=sql_node_id,
            sql_content=sql_content,
            input_tables=input_tables,
            output_tables=output_tables,
            dialect=dialect,
        )
        return await self._queue.put(task)

    @property
    def stats(self) -> dict[str, Any]:
        """获取统计信息"""
        result = self._stats.to_dict()
        if self._queue:
            queue_stats = self._queue.stats
            result["queue"] = {
                "current_size": queue_stats.current_size,
                "total_received": queue_stats.total_received,
                "total_processed": queue_stats.total_processed,
            }
        return result

    async def _process_batch(self, tasks: list[SQLSummaryTask]) -> None:
        """批量处理 SQL 摘要任务"""
        if not tasks:
            return

        logger.info("sql_summary_batch_start", count=len(tasks))

        try:
            # 1. 批量调用 LLM 生成摘要
            summaries = await self._generate_summaries(tasks)

            # 2. 批量生成 embedding
            embeddings = await self._generate_embeddings([s.summary for s in summaries])

            # 3. 批量回写 Neo4j
            await self._write_summaries(tasks, summaries, embeddings)

            # 4. 更新已处理集合
            for task in tasks:
                self._processed_ids.add(task.sql_id)

            self._stats.total_processed += len(tasks)
            self._stats.last_batch_time = datetime.now(UTC)

            logger.info("sql_summary_batch_complete", count=len(tasks))

        except Exception as e:
            self._stats.total_failed += len(tasks)
            logger.error("sql_summary_batch_failed", count=len(tasks), error=str(e))

    async def _generate_summaries(self, tasks: list[SQLSummaryTask]) -> list[SQLSummaryResult]:
        """批量调用 LLM 生成摘要（使用结构化输出）"""
        prompt = self._build_batch_prompt(tasks)

        llm = _get_llm_provider()(output_schema=BatchSummaryResult)
        messages = Messages(
            [
                Message.system(self._get_system_prompt()),
                Message.user(prompt),
            ]
        )

        result = await llm.ainvoke(messages)

        if isinstance(result, BatchSummaryResult):
            return result.results
        return []

    def _get_system_prompt(self) -> str:
        """获取系统提示词"""
        return """你是一个 ETL SQL 分析专家。你的任务是分析 SQL 查询并生成简洁的语义摘要。

要求：
1. summary: 1-2句话描述 SQL 做什么（数据从哪来、到哪去、做什么转换）
2. tags: 提取关键标签（逗号分隔），如：聚合、关联、过滤、去重、排序等
3. 使用中文
4. 保持简洁，便于语义检索"""

    def _build_batch_prompt(self, tasks: list[SQLSummaryTask]) -> str:
        """构建批量处理的 prompt"""
        sql_list = []
        for i, task in enumerate(tasks, 1):
            input_str = ", ".join(task.input_tables) if task.input_tables else "未知"
            output_str = ", ".join(task.output_tables) if task.output_tables else "未知"
            sql_list.append(
                f"""
### SQL #{i}
- 输入表: {input_str}
- 输出表: {output_str}
```sql
{task.sql_content}
```"""
            )

        return f"""分析以下 {len(tasks)} 条 ETL SQL，为每条生成语义摘要。

{chr(10).join(sql_list)}"""

    async def _generate_embeddings(self, summaries: list[str]) -> list[list[float]]:
        """批量生成 embedding"""
        loop = asyncio.get_event_loop()
        embedder = self._get_embedder()
        return await loop.run_in_executor(self._executor, embedder.embed_batch, summaries)

    async def _write_summaries(
        self,
        tasks: list[SQLSummaryTask],
        summaries: list[SQLSummaryResult],
        embeddings: list[list[float]],
    ) -> None:
        """批量回写摘要到 Neo4j"""
        embedder = self._get_embedder()
        embedding_provider = f"{embedder.provider}/{embedder.model_name}"

        # 构建更新数据
        data = []
        for task, summary, embedding in zip(tasks, summaries, embeddings, strict=False):
            data.append(
                {
                    "id": task.sql_node_id,
                    "summary": summary.summary,
                    "tags": summary.tags,
                    "embedding": embedding,
                    "embeddingProvider": embedding_provider,
                }
            )

        async with neo4j_async_session() as session:
            query = """
            UNWIND $data AS item
            MATCH (s:SQL {id: item.id})
            SET s.summary = item.summary,
                s.tags = item.tags,
                s.embedding = item.embedding,
                s.embeddingProvider = item.embeddingProvider,
                s.summaryGeneratedAt = datetime()
            """
            await session.run(query, data=data)

        logger.debug("sql_summaries_written", count=len(data))


# 全局单例
sql_summary_processor = SQLSummaryProcessor()
