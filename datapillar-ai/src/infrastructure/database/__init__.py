"""
数据库连接管理

提供 Neo4j、MySQL、Redis 连接池
"""

from typing import TYPE_CHECKING

__all__ = [
    "Neo4jClient",
    "AsyncNeo4jClient",
    "convert_neo4j_types",
    "MySQLClient",
    "RedisClient",
]

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
    延迟导入（避免 package import 触发循环依赖）。
    """
    if name in {"Neo4jClient", "AsyncNeo4jClient", "convert_neo4j_types"}:
        from src.infrastructure.database import neo4j as _neo4j

        return getattr(_neo4j, name)
    if name == "MySQLClient":
        from src.infrastructure.database.mysql import MySQLClient

        return MySQLClient
    if name == "RedisClient":
        from src.infrastructure.database.redis import RedisClient

        return RedisClient
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
