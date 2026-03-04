# @author Sunny
# @date 2026-01-27

"""
Redis Connection management

provide Redis Asynchronous connection pool
"""

import logging
from typing import Optional

import redis.asyncio as redis

from src.shared.config.exceptions import RedisError
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class RedisClient:
    """
    Redis connection pool manager(Singleton)

    Two modes are provided:- Default mode(decode_responses=True):for general business,return string
    - binary mode(decode_responses=False):used for LangGraph Checkpointer Wait for scenarios that require raw bytes
    """

    _instance: Optional["RedisClient"] = None
    _pool: redis.ConnectionPool | None = None
    _binary_pool: redis.ConnectionPool | None = None  # used for Checkpointer

    def __init__(self):
        if RedisClient._pool is None:
            raise RuntimeError("Please use RedisClient.get_instance() Get instance")
        self.client: redis.Redis = redis.Redis(connection_pool=RedisClient._pool)

    @classmethod
    async def get_instance(cls) -> "RedisClient":
        """Get RedisClient Singleton"""
        if cls._instance is None:
            if cls._pool is None:
                try:
                    logger.info(
                        f"initialization Redis connection pool:{settings.redis_host}:{settings.redis_port}/{settings.redis_db}"
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
                    logger.info("Redis The connection pool has been initialized")
                except Exception as e:
                    logger.error(f"Redis Connection pool initialization failed:{e}")
                    raise RedisError(f"Redis Connection failed:{e}") from e
            cls._instance = cls()
        return cls._instance

    @classmethod
    async def get_binary_client(cls) -> redis.Redis:
        """
        Get binary mode Redis client(decode_responses=False)

        used for LangGraph Checkpointer Wait for scenarios that require raw bytes."""
        if cls._binary_pool is None:
            try:
                logger.info(
                    f"initialization Redis Binary connection pool:{settings.redis_host}:{settings.redis_port}/{settings.redis_db}"
                )
                cls._binary_pool = redis.ConnectionPool(
                    host=settings.redis_host,
                    port=settings.redis_port,
                    db=settings.redis_db,
                    password=settings.redis_password or None,
                    decode_responses=False,  # binary mode
                    max_connections=50,
                )
                logger.info("Redis Binary connection pool initialized")
            except Exception as e:
                logger.error(f"Redis Binary connection pool initialization failed:{e}")
                raise RedisError(f"Redis Connection failed:{e}") from e
        return redis.Redis(connection_pool=cls._binary_pool)

    @classmethod
    async def close(cls):
        """close Redis connection pool"""
        if cls._pool:
            await cls._pool.disconnect()
            cls._pool = None
            logger.info("Redis Connection pool is closed")
        if cls._binary_pool:
            await cls._binary_pool.disconnect()
            cls._binary_pool = None
            logger.info("Redis Binary connection pool is closed")
        cls._instance = None

    @classmethod
    async def ping(cls) -> bool:
        """
        health check:Verify Redis Is the connection normal?Returns:True:The connection is normal
        False:Connection abnormality
        """
        try:
            instance = await cls.get_instance()
            return await instance.client.ping()
        except Exception as e:
            logger.warning(f"Redis ping failed:{e}")
            return False
