# @author Sunny
# @date 2026-01-27

"""
Neo4j Database connection management

provide Neo4j Synchronous and asynchronous connection pools
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
    recursive conversion Neo4j The special type is Python primitive type
    """
    if isinstance(value, Neo4jDateTime):
        return value.iso_format()
    elif isinstance(value, dict):
        return {k: convert_neo4j_types(v) for k, v in value.items()}
    elif isinstance(value, list):
        return [convert_neo4j_types(item) for item in value]
    return value


class Neo4jClient:
    """Neo4j connection pool manager（Neo4j Driver Comes with connection pool）"""

    _driver: Driver | None = None

    @classmethod
    def get_driver(cls) -> Driver:
        """Get Neo4j Driver（Global singleton，Comes with connection pool）"""
        if cls._driver is None:
            try:
                logger.info(f"initialization Neo4j Driver: {settings.neo4j_uri}")
                cls._driver = GraphDatabase.driver(
                    settings.neo4j_uri,
                    auth=(settings.neo4j_username, settings.neo4j_password),
                    max_connection_pool_size=50,
                    connection_acquisition_timeout=60,
                    max_connection_lifetime=3600,
                )
                cls._driver.verify_connectivity()
                logger.info("Neo4j Driver Initialization successful")
            except Exception as e:
                logger.error(f"Neo4j Driver Initialization failed: {e}")
                raise Neo4jError(f"Neo4j Connection failed: {e}") from e
        return cls._driver

    @classmethod
    def close(cls):
        """close Neo4j Driver"""
        if cls._driver:
            cls._driver.close()
            cls._driver = None
            logger.info("Neo4j Driver Closed")


class AsyncNeo4jClient:
    """Neo4j Asynchronous connection pool manager"""

    _driver: AsyncDriver | None = None

    @classmethod
    async def get_driver(cls) -> AsyncDriver:
        """Get Neo4j AsyncDriver（Global singleton，Comes with connection pool）"""
        if cls._driver is None:
            try:
                logger.info(f"initialization Neo4j AsyncDriver: {settings.neo4j_uri}")
                cls._driver = AsyncGraphDatabase.driver(
                    settings.neo4j_uri,
                    auth=(settings.neo4j_username, settings.neo4j_password),
                    max_connection_pool_size=50,
                    connection_acquisition_timeout=60,
                )
                await cls._driver.verify_connectivity()
                logger.info("Neo4j AsyncDriver Initialization successful")
            except Exception as e:
                logger.error(f"Neo4j AsyncDriver Initialization failed: {e}")
                raise Neo4jError(f"Neo4j Asynchronous connection failed: {e}") from e
        return cls._driver

    @classmethod
    async def close(cls):
        """close Neo4j AsyncDriver"""
        if cls._driver:
            await cls._driver.close()
            cls._driver = None
            logger.info("Neo4j AsyncDriver Closed")
