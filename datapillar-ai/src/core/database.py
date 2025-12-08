"""
数据库连接管理器
负责连接 Neo4j、MySQL、Redis 数据库
"""

from typing import Any, Dict, List, Optional
from neo4j import GraphDatabase, Driver, AsyncGraphDatabase, AsyncDriver
from neo4j.time import DateTime as Neo4jDateTime
import logging

logger = logging.getLogger(__name__)
import redis.asyncio as redis
import pymysql
from pymysql.cursors import DictCursor

from src.core.config import settings
from src.core.exceptions import Neo4jError, MySQLError, RedisError


def _convert_neo4j_types(value: Any) -> Any:
    """递归转换 Neo4j 特殊类型为 Python 原生类型"""
    if isinstance(value, Neo4jDateTime):
        return value.iso_format()
    elif isinstance(value, dict):
        return {k: _convert_neo4j_types(v) for k, v in value.items()}
    elif isinstance(value, list):
        return [_convert_neo4j_types(item) for item in value]
    return value


class Neo4jClient:
    """Neo4j客户端（同步版本）"""

    def __init__(self):
        self.driver: Driver | None = None

    def connect(self):
        """连接Neo4j数据库"""
        try:
            logger.info(f"连接Neo4j数据库: {settings.neo4j_uri}")
            self.driver = GraphDatabase.driver(
                settings.neo4j_uri,
                auth=(settings.neo4j_username, settings.neo4j_password),
            )
            self.driver.verify_connectivity()
            logger.info("Neo4j连接成功")
        except Exception as e:
            raise Neo4jError(f"Neo4j 连接失败: {e}")

    def close(self):
        """关闭连接"""
        if self.driver:
            self.driver.close()
            logger.info("Neo4j连接已关闭")

    def execute_query(
        self, query: str, parameters: Dict[str, Any] | None = None
    ) -> List[Dict[str, Any]]:
        """
        执行Cypher查询

        Args:
            query: Cypher查询语句
            parameters: 查询参数

        Returns:
            查询结果列表
        """
        if not self.driver:
            raise Neo4jError("Neo4j 未连接")

        try:
            with self.driver.session(database=settings.neo4j_database) as session:
                result = session.run(query, parameters or {})
                return [_convert_neo4j_types(record.data()) for record in result]
        except Exception as e:
            raise Neo4jError(f"Neo4j 查询失败: {e}")

    def execute_write(self, query: str, parameters: Dict[str, Any] | None = None) -> Any:
        """
        执行写入操作

        Args:
            query: Cypher写入语句
            parameters: 查询参数

        Returns:
            执行结果
        """
        if not self.driver:
            raise Neo4jError("Neo4j 未连接")

        try:
            def _transaction_function(tx):
                result = tx.run(query, parameters or {})
                return result.single()

            with self.driver.session(database=settings.neo4j_database) as session:
                return session.execute_write(_transaction_function)
        except Exception as e:
            raise Neo4jError(f"Neo4j 写入失败: {e}")

    def __enter__(self):
        """上下文管理器入口"""
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器退出"""
        self.close()


class AsyncNeo4jClient:
    """Neo4j客户端（异步版本）"""

    def __init__(self):
        self.driver: AsyncDriver | None = None

    async def connect(self):
        """连接Neo4j数据库"""
        logger.info(f"连接Neo4j数据库(异步): {settings.neo4j_uri}")
        self.driver = AsyncGraphDatabase.driver(
            settings.neo4j_uri,
            auth=(settings.neo4j_username, settings.neo4j_password),
        )
        # 测试连接
        await self.driver.verify_connectivity()
        logger.info("Neo4j连接成功(异步)")

    async def close(self):
        """关闭连接"""
        if self.driver:
            await self.driver.close()
            logger.info("Neo4j连接已关闭(异步)")

    async def execute_query(
        self, query: str, parameters: Dict[str, Any] | None = None
    ) -> List[Dict[str, Any]]:
        """
        执行Cypher查询

        Args:
            query: Cypher查询语句
            parameters: 查询参数

        Returns:
            查询结果列表
        """
        if not self.driver:
            raise RuntimeError("Neo4j未连接，请先调用connect()")

        async with self.driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, parameters or {})
            records = await result.data()
            return records

    async def execute_write(
        self, query: str, parameters: Dict[str, Any] | None = None
    ) -> Any:
        """
        执行写入操作

        Args:
            query: Cypher写入语句
            parameters: 查询参数

        Returns:
            执行结果
        """
        if not self.driver:
            raise RuntimeError("Neo4j未连接，请先调用connect()")

        async def _transaction_function(tx):
            result = await tx.run(query, parameters or {})
            return await result.single()

        async with self.driver.session(database=settings.neo4j_database) as session:
            return await session.execute_write(_transaction_function)

    async def __aenter__(self):
        """异步上下文管理器入口"""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """异步上下文管理器退出"""
        await self.close()


class MySQLClient:
    """MySQL 客户端（同步版本）"""

    def __init__(self):
        self.connection = None

    def connect(self):
        """连接 MySQL 数据库"""
        try:
            logger.info(f"连接MySQL数据库: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")
            self.connection = pymysql.connect(
                host=settings.mysql_host,
                port=settings.mysql_port,
                user=settings.mysql_username,
                password=settings.mysql_password,
                database=settings.mysql_database,
                charset='utf8mb4',
                cursorclass=DictCursor
            )
            logger.info("MySQL连接成功")
        except Exception as e:
            raise MySQLError(f"MySQL 连接失败: {e}")

    def close(self):
        """关闭连接"""
        if self.connection:
            self.connection.close()
            logger.info("MySQL连接已关闭")

    def execute_query(self, query: str, params: tuple = None) -> List[Dict[str, Any]]:
        """
        执行查询

        Args:
            query: SQL 查询语句
            params: 查询参数

        Returns:
            查询结果列表
        """
        if not self.connection:
            raise MySQLError("MySQL 未连接")

        try:
            with self.connection.cursor() as cursor:
                cursor.execute(query, params or ())
                return cursor.fetchall()
        except Exception as e:
            raise MySQLError(f"MySQL 查询失败: {e}")

    def execute_write(self, query: str, params: tuple = None) -> int:
        """
        执行写入操作

        Args:
            query: SQL 写入语句
            params: 查询参数

        Returns:
            受影响的行数
        """
        if not self.connection:
            raise MySQLError("MySQL 未连接")

        try:
            with self.connection.cursor() as cursor:
                affected = cursor.execute(query, params or ())
                self.connection.commit()
                return affected
        except Exception as e:
            raise MySQLError(f"MySQL 写入失败: {e}")

    def __enter__(self):
        """上下文管理器入口"""
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器退出"""
        self.close()


class RedisClient:
    """Redis 客户端（异步版本）"""

    def __init__(self):
        self.client: Optional[redis.Redis] = None

    async def connect(self):
        """连接 Redis 数据库"""
        try:
            logger.info(f"连接Redis: {settings.redis_host}:{settings.redis_port}/{settings.redis_db}")
            self.client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                db=settings.redis_db,
                password=settings.redis_password or None,
                encoding="utf-8",
                decode_responses=True,
            )
            await self.client.ping()
            logger.info("Redis连接成功")
        except Exception as e:
            raise RedisError(f"Redis 连接失败: {e}")

    async def close(self):
        """关闭连接"""
        if self.client:
            await self.client.close()
            logger.info("Redis连接已关闭")

    async def __aenter__(self):
        """异步上下文管理器入口"""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """异步上下文管理器退出"""
        await self.close()


