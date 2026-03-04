# @author Sunny
# @date 2026-01-27

"""
infrastructure layer

Provide database connection,Infrastructure such as external services."""

__all__ = [  # Database
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
    Delayed import(avoid package import trigger database/Configuration circular dependencies).Description:- `import src.infrastructure.llm...` This file will also be executed;If here eager import database,will result in database->config->repository->database circular dependencies.- use __getattr__ let need Database client modules can still pass
    `from src.infrastructure import MySQLClient` This way of writing gets the object."""
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
