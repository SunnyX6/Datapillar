"""
数据库连接池管理（企业级标准）
职责：管理数据库连接池，提供连接实例
不包含任何业务逻辑（查询/写入由 Repository 层负责）
"""

from typing import Optional, Any
from neo4j import GraphDatabase, Driver, AsyncGraphDatabase, AsyncDriver
from neo4j.time import DateTime as Neo4jDateTime
import logging

logger = logging.getLogger(__name__)
import redis.asyncio as redis
from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

from src.config import settings
from src.config.exceptions import Neo4jError, MySQLError, RedisError


# ==================== 工具函数 ====================

def convert_neo4j_types(value: Any) -> Any:
    """
    递归转换 Neo4j 特殊类型为 Python 原生类型
    供 Repository 层使用
    """
    if isinstance(value, Neo4jDateTime):
        return value.iso_format()
    elif isinstance(value, dict):
        return {k: convert_neo4j_types(v) for k, v in value.items()}
    elif isinstance(value, list):
        return [convert_neo4j_types(item) for item in value]
    return value


# ==================== MySQL 连接池 ====================

class MySQLClient:
    """MySQL 连接池管理器（使用 SQLAlchemy）"""

    _engine = None

    @classmethod
    def get_engine(cls):
        """
        获取 SQLAlchemy Engine（全局单例连接池）

        Returns:
            SQLAlchemy Engine 实例

        Example:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(text("SELECT * FROM users"))
        """
        if cls._engine is None:
            db_url = (
                f"mysql+pymysql://{settings.mysql_username}:{settings.mysql_password}"
                f"@{settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}"
                f"?charset=utf8mb4"
            )
            try:
                cls._engine = create_engine(
                    db_url,
                    poolclass=QueuePool,
                    pool_size=10,              # 连接池大小
                    max_overflow=20,           # 最大溢出连接
                    pool_pre_ping=True,        # 预检查连接是否存活
                    pool_recycle=3600,         # 1小时回收连接
                    pool_timeout=30,           # 获取连接超时
                    echo=False,                # 不打印SQL
                )
                logger.info(f"MySQL 连接池已初始化: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")
            except Exception as e:
                logger.error(f"MySQL 连接池初始化失败: {e}")
                raise MySQLError(f"MySQL 连接池初始化失败: {e}")
        return cls._engine

    @classmethod
    def close(cls):
        """关闭连接池"""
        if cls._engine:
            cls._engine.dispose()
            cls._engine = None
            logger.info("MySQL 连接池已关闭")


# ==================== Neo4j 连接池 ====================

class Neo4jClient:
    """Neo4j 连接池管理器（Neo4j Driver 自带连接池）"""

    _driver: Optional[Driver] = None

    @classmethod
    def get_driver(cls) -> Driver:
        """
        获取 Neo4j Driver（全局单例，自带连接池）

        Returns:
            Neo4j Driver 实例

        Example:
            driver = Neo4jClient.get_driver()
            with driver.session(database=settings.neo4j_database) as session:
                result = session.run("MATCH (n) RETURN n LIMIT 10")
        """
        if cls._driver is None:
            try:
                logger.info(f"初始化 Neo4j Driver: {settings.neo4j_uri}")
                cls._driver = GraphDatabase.driver(
                    settings.neo4j_uri,
                    auth=(settings.neo4j_username, settings.neo4j_password),
                    max_connection_pool_size=50,        # 连接池大小
                    connection_acquisition_timeout=60,  # 获取连接超时
                    max_connection_lifetime=3600,       # 连接最大生命周期
                )
                cls._driver.verify_connectivity()
                logger.info("Neo4j Driver 初始化成功")
            except Exception as e:
                logger.error(f"Neo4j Driver 初始化失败: {e}")
                raise Neo4jError(f"Neo4j 连接失败: {e}")
        return cls._driver

    @classmethod
    def close(cls):
        """关闭 Neo4j Driver"""
        if cls._driver:
            cls._driver.close()
            cls._driver = None
            logger.info("Neo4j Driver 已关闭")


class AsyncNeo4jClient:
    """Neo4j 异步连接池管理器"""

    _driver: Optional[AsyncDriver] = None

    @classmethod
    async def get_driver(cls) -> AsyncDriver:
        """
        获取 Neo4j AsyncDriver（全局单例，自带连接池）

        Returns:
            Neo4j AsyncDriver 实例
        """
        if cls._driver is None:
            try:
                logger.info(f"初始化 Neo4j AsyncDriver: {settings.neo4j_uri}")
                cls._driver = AsyncGraphDatabase.driver(
                    settings.neo4j_uri,
                    auth=(settings.neo4j_username, settings.neo4j_password),
                    max_connection_pool_size=50,
                    connection_acquisition_timeout=60,
                )
                await cls._driver.verify_connectivity()
                logger.info("Neo4j AsyncDriver 初始化成功")
            except Exception as e:
                logger.error(f"Neo4j AsyncDriver 初始化失败: {e}")
                raise Neo4jError(f"Neo4j 异步连接失败: {e}")
        return cls._driver

    @classmethod
    async def close(cls):
        """关闭 Neo4j AsyncDriver"""
        if cls._driver:
            await cls._driver.close()
            cls._driver = None
            logger.info("Neo4j AsyncDriver 已关闭")


# ==================== Redis 连接池 ====================

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
