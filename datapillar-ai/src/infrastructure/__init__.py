"""
基础设施层

提供数据库连接、外部服务等基础设施。
"""

__all__ = [
    # Database
    "Neo4jClient",
    "AsyncNeo4jClient",
    "MySQLClient",
    "RedisClient",
    "convert_neo4j_types",
]

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from src.infrastructure.database.mysql import MySQLClient as MySQLClient
    from src.infrastructure.database.neo4j import (
        AsyncNeo4jClient as AsyncNeo4jClient,
    )
    from src.infrastructure.database.neo4j import (
        Neo4jClient as Neo4jClient,
    )
    from src.infrastructure.database.neo4j import (
        convert_neo4j_types as convert_neo4j_types,
    )
    from src.infrastructure.database.redis import RedisClient as RedisClient


def __getattr__(name: str):
    """
    延迟导入（避免 package import 触发数据库/配置的循环依赖）。

    说明：
    - `import src.infrastructure.llm...` 也会执行本文件；
      如果这里 eager import database，将导致 database->config->repository->database 的循环依赖。
    - 使用 __getattr__ 让需要 Database client 的模块仍能通过
      `from src.infrastructure import MySQLClient` 这种写法获取对象。
    """
    if name in {
        "Neo4jClient",
        "AsyncNeo4jClient",
        "MySQLClient",
        "RedisClient",
        "convert_neo4j_types",
    }:
        from src.infrastructure import database as _db

        return getattr(_db, name)
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
