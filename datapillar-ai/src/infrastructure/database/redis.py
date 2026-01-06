"""
Redis 连接管理

提供 Redis 异步连接池
"""

import logging
from typing import Optional

import redis.asyncio as redis

from src.shared.config.exceptions import RedisError
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class RedisClient:
    """
    Redis 连接池管理器（单例）

    提供两种模式：
    - 默认模式（decode_responses=True）：用于一般业务，返回字符串
    - 二进制模式（decode_responses=False）：用于 LangGraph Checkpointer 等需要原始字节的场景
    """

    _instance: Optional["RedisClient"] = None
    _pool: redis.ConnectionPool | None = None
    _binary_pool: redis.ConnectionPool | None = None  # 用于 Checkpointer

    def __init__(self):
        if RedisClient._pool is None:
            raise RuntimeError("请使用 RedisClient.get_instance() 获取实例")
        self.client: redis.Redis = redis.Redis(connection_pool=RedisClient._pool)

    @classmethod
    async def get_instance(cls) -> "RedisClient":
        """获取 RedisClient 单例"""
        if cls._instance is None:
            if cls._pool is None:
                try:
                    logger.info(
                        f"初始化 Redis 连接池: {settings.redis_host}:{settings.redis_port}/{settings.redis_db}"
                    )
                    cls._pool = redis.ConnectionPool(
                        host=settings.redis_host,
                        port=settings.redis_port,
                        db=settings.redis_db,
                        password=settings.redis_password or None,
                        encoding="utf-8",
                        decode_responses=True,
                        max_connections=50,
                    )
                    logger.info("Redis 连接池已初始化")
                except Exception as e:
                    logger.error(f"Redis 连接池初始化失败: {e}")
                    raise RedisError(f"Redis 连接失败: {e}") from e
            cls._instance = cls()
        return cls._instance

    @classmethod
    async def get_binary_client(cls) -> redis.Redis:
        """
        获取二进制模式 Redis 客户端（decode_responses=False）

        用于 LangGraph Checkpointer 等需要原始字节的场景。
        """
        if cls._binary_pool is None:
            try:
                logger.info(
                    f"初始化 Redis 二进制连接池: {settings.redis_host}:{settings.redis_port}/{settings.redis_db}"
                )
                cls._binary_pool = redis.ConnectionPool(
                    host=settings.redis_host,
                    port=settings.redis_port,
                    db=settings.redis_db,
                    password=settings.redis_password or None,
                    decode_responses=False,  # 二进制模式
                    max_connections=50,
                )
                logger.info("Redis 二进制连接池已初始化")
            except Exception as e:
                logger.error(f"Redis 二进制连接池初始化失败: {e}")
                raise RedisError(f"Redis 连接失败: {e}") from e
        return redis.Redis(connection_pool=cls._binary_pool)

    @classmethod
    async def close(cls):
        """关闭 Redis 连接池"""
        if cls._pool:
            await cls._pool.disconnect()
            cls._pool = None
            logger.info("Redis 连接池已关闭")
        if cls._binary_pool:
            await cls._binary_pool.disconnect()
            cls._binary_pool = None
            logger.info("Redis 二进制连接池已关闭")
        cls._instance = None

    @classmethod
    async def ping(cls) -> bool:
        """
        健康检查：验证 Redis 连接是否正常

        Returns:
            True: 连接正常
            False: 连接异常
        """
        try:
            instance = await cls.get_instance()
            return await instance.client.ping()
        except Exception as e:
            logger.warning(f"Redis ping 失败: {e}")
            return False
