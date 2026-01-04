"""
ETL LangGraph Checkpoint（Redis）

定位（务必区分清楚）：
- Checkpoint：用于“可恢复执行”的状态持久化（断点续跑/中断恢复/容灾）。权威来源是 checkpointer。
- 短记忆：单次会话/单次运行内的临时信息（通常在 AgentState 中），应随 checkpoint 一起被持久化。
- 长记忆：跨会话可复用的知识与偏好，必须落到外部权威存储（Neo4j/Gravitino/MySQL/对象存储等），
  由工具层检索回填到当前 state；不要把长记忆硬塞进 prompt。
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.redis.aio import AsyncRedisSaver

from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


def _checkpoint_ttl_minutes() -> float:
    ttl_seconds = int(settings.get("redis_checkpoint_ttl_seconds", 60 * 60 * 24 * 7))
    if ttl_seconds <= 0:
        return -1
    return ttl_seconds / 60.0


def _redis_url() -> str:
    host = settings.redis_host
    port = settings.redis_port
    db = settings.redis_db
    return f"redis://{host}:{port}/{db}"


@asynccontextmanager
async def etl_checkpointer() -> AsyncIterator[BaseCheckpointSaver]:
    """
    创建 ETL 专用 Redis Checkpointer（Async）。

    约束：
    - `checkpoint_ns` 在 orchestrator 的 RunnableConfig 里统一设置为 `etl`
    - TTL 采用配置 `redis_checkpoint_ttl_seconds`，读刷新 `refresh_on_read=True`
    """
    ttl_minutes = _checkpoint_ttl_minutes()
    ttl_config = {"default_ttl": ttl_minutes, "refresh_on_read": True}
    redis_url = _redis_url()

    logger.info("初始化 ETL Redis Checkpointer: %s (ttl_minutes=%s)", redis_url, ttl_minutes)
    async with AsyncRedisSaver.from_conn_string(
        redis_url,
        connection_args={"password": settings.redis_password or None},
        ttl=ttl_config,
    ) as saver:
        yield saver
