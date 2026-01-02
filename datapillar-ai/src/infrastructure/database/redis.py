"""
Redis 连接管理

提供 Redis 异步连接池
"""

from typing import Optional
import logging

import redis.asyncio as redis

from src.shared.config.settings import settings
from src.shared.config.exceptions import RedisError

logger = logging.getLogger(__name__)


class RedisClient:
    """Redis 连接池管理器（单例）"""

    _instance: Optional["RedisClient"] = None
    _pool: Optional[redis.ConnectionPool] = None

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
                    logger.info(f"初始化 Redis 连接池: {settings.redis_host}:{settings.redis_port}/{settings.redis_db}")
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
                    raise RedisError(f"Redis 连接失败: {e}")
            cls._instance = cls()
        return cls._instance

    @classmethod
    async def close(cls):
        """关闭 Redis 连接池"""
        if cls._pool:
            await cls._pool.disconnect()
            cls._pool = None
            cls._instance = None
            logger.info("Redis 连接池已关闭")
