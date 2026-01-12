"""
Checkpoint - LangGraph 状态持久化

封装 LangGraph Checkpoint 的读写操作。

职责：
- 复用 RedisClient 连接池，避免重复创建 Redis 连接
- 封装 checkpoint 的 CRUD 操作
- 提供 saver 给 LangGraph graph.compile() 使用

设计说明：
- Checkpoint 用于"可恢复执行"的状态持久化（断点续跑/中断恢复/容灾）
- 使用 RedisClient.get_binary_client() 获取二进制模式客户端
- TTL 配置从 settings 读取，支持读刷新
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.base import BaseCheckpointSaver, CheckpointTuple
from langgraph.checkpoint.redis.aio import AsyncRedisSaver

from src.infrastructure.database.redis import RedisClient
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Checkpoint:
    """
    Checkpoint 数据访问层

    封装 LangGraph AsyncRedisSaver 的读写操作。

    使用示例：
    ```python
    # 获取 saver 给 LangGraph 使用
    async with Checkpoint.get_saver() as checkpointer:
        graph = builder.compile(checkpointer=checkpointer)

    # 查询 checkpoint
    checkpoint = await Checkpoint.get_checkpoint(config)

    # 删除 thread
    await Checkpoint.delete_thread(thread_id)
    ```
    """

    _saver: AsyncRedisSaver | None = None
    _initialized: bool = False

    @classmethod
    def _get_ttl_minutes(cls) -> float:
        """获取 TTL 配置（分钟）"""
        ttl_seconds = int(settings.get("redis_checkpoint_ttl_seconds", 60 * 60 * 24 * 7))
        if ttl_seconds <= 0:
            return -1
        return ttl_seconds / 60.0

    @classmethod
    async def _ensure_saver(cls) -> AsyncRedisSaver:
        """确保 saver 已初始化"""
        if cls._saver is None or not cls._initialized:
            ttl_minutes = cls._get_ttl_minutes()
            ttl_config = {"default_ttl": ttl_minutes, "refresh_on_read": True}

            # 通过 RedisClient 获取二进制模式客户端
            redis_client = await RedisClient.get_binary_client()

            logger.info(
                "初始化 Checkpoint (复用连接池, ttl_minutes=%s)",
                ttl_minutes,
            )

            cls._saver = AsyncRedisSaver(redis_client=redis_client, ttl=ttl_config)
            await cls._saver.asetup()
            cls._initialized = True

        return cls._saver

    # ==================== 读操作 ====================

    @classmethod
    async def get_checkpoint(cls, config: RunnableConfig) -> CheckpointTuple | None:
        """
        获取指定 thread 的 checkpoint

        Args:
            config: RunnableConfig，包含 thread_id 和 checkpoint_ns

        Returns:
            CheckpointTuple 或 None
        """
        saver = await cls._ensure_saver()
        return await saver.aget_tuple(config)

    @classmethod
    async def list_checkpoints(
        cls,
        config: RunnableConfig,
        *,
        limit: int | None = None,
        before: RunnableConfig | None = None,
    ) -> list[CheckpointTuple]:
        """
        列出指定 thread 的 checkpoints

        Args:
            config: RunnableConfig，包含 thread_id 和 checkpoint_ns
            limit: 最大返回数量
            before: 只返回此 checkpoint 之前的

        Returns:
            CheckpointTuple 列表
        """
        saver = await cls._ensure_saver()
        results = []
        async for checkpoint in saver.alist(config, limit=limit, before=before):
            results.append(checkpoint)
        return results

    # ==================== 写操作 ====================

    @classmethod
    async def save_checkpoint(
        cls,
        config: RunnableConfig,
        checkpoint: dict[str, Any],
        metadata: dict[str, Any],
        new_versions: dict[str, int],
    ) -> RunnableConfig:
        """
        保存 checkpoint

        Args:
            config: RunnableConfig
            checkpoint: checkpoint 数据
            metadata: 元数据
            new_versions: 新版本信息

        Returns:
            更新后的 RunnableConfig
        """
        saver = await cls._ensure_saver()
        return await saver.aput(config, checkpoint, metadata, new_versions)

    @classmethod
    async def save_writes(
        cls,
        config: RunnableConfig,
        writes: list[tuple[str, Any]],
        task_id: str,
    ) -> None:
        """
        保存 pending writes

        Args:
            config: RunnableConfig
            writes: 待写入的 (channel, value) 列表
            task_id: 任务 ID
        """
        saver = await cls._ensure_saver()
        await saver.aput_writes(config, writes, task_id)

    # ==================== 删除操作 ====================

    @classmethod
    async def delete_thread(cls, thread_id: str) -> None:
        """
        删除指定 thread 的所有 checkpoint

        用于会话清理。

        Args:
            thread_id: thread ID
        """
        saver = await cls._ensure_saver()
        await saver.adelete_thread(thread_id)

    # ==================== 给 LangGraph 使用 ====================

    @classmethod
    @asynccontextmanager
    async def get_saver(cls) -> AsyncIterator[BaseCheckpointSaver]:
        """
        获取 checkpointer（给 LangGraph graph.compile 使用）

        用法：
        ```python
        async with Checkpoint.get_saver() as checkpointer:
            graph = builder.compile(checkpointer=checkpointer)
        ```
        """
        saver = await cls._ensure_saver()
        yield saver
        # 不关闭 saver，连接池由 RedisClient 统一管理

    @classmethod
    async def close(cls) -> None:
        """关闭（应用退出时调用）"""
        cls._saver = None
        cls._initialized = False
        logger.info("Checkpoint 已关闭")
