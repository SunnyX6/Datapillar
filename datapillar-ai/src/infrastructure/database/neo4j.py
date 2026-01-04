"""
Neo4j 数据库连接管理

提供 Neo4j 同步和异步连接池
"""

import logging
from typing import Any

from neo4j import AsyncDriver, AsyncGraphDatabase, Driver, GraphDatabase
from neo4j.time import DateTime as Neo4jDateTime

from src.shared.config.exceptions import Neo4jError
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


def convert_neo4j_types(value: Any) -> Any:
    """
    递归转换 Neo4j 特殊类型为 Python 原生类型
    """
    if isinstance(value, Neo4jDateTime):
        return value.iso_format()
    elif isinstance(value, dict):
        return {k: convert_neo4j_types(v) for k, v in value.items()}
    elif isinstance(value, list):
        return [convert_neo4j_types(item) for item in value]
    return value


class Neo4jClient:
    """Neo4j 连接池管理器（Neo4j Driver 自带连接池）"""

    _driver: Driver | None = None

    @classmethod
    def get_driver(cls) -> Driver:
        """获取 Neo4j Driver（全局单例，自带连接池）"""
        if cls._driver is None:
            try:
                logger.info(f"初始化 Neo4j Driver: {settings.neo4j_uri}")
                cls._driver = GraphDatabase.driver(
                    settings.neo4j_uri,
                    auth=(settings.neo4j_username, settings.neo4j_password),
                    max_connection_pool_size=50,
                    connection_acquisition_timeout=60,
                    max_connection_lifetime=3600,
                )
                cls._driver.verify_connectivity()
                logger.info("Neo4j Driver 初始化成功")
            except Exception as e:
                logger.error(f"Neo4j Driver 初始化失败: {e}")
                raise Neo4jError(f"Neo4j 连接失败: {e}") from e
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

    _driver: AsyncDriver | None = None

    @classmethod
    async def get_driver(cls) -> AsyncDriver:
        """获取 Neo4j AsyncDriver（全局单例，自带连接池）"""
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
                raise Neo4jError(f"Neo4j 异步连接失败: {e}") from e
        return cls._driver

    @classmethod
    async def close(cls):
        """关闭 Neo4j AsyncDriver"""
        if cls._driver:
            await cls._driver.close()
            cls._driver = None
            logger.info("Neo4j AsyncDriver 已关闭")
