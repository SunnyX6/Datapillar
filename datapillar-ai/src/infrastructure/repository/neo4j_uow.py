# @author Sunny
# @date 2026-01-27

"""
Neo4j Session management(Repository Layer common capabilities)

purpose:- Business module(src/modules/*)No direct contact Neo4j Driver/Client
- Repository Directory provides a unified session How to get it,The business module only depends on"session context"

Description:- driver The life cycle still consists of src/infrastructure/database/neo4j.py management(global connection pool)
- This is only responsible for providing synchronization/asynchronous session of context manager
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator
from contextlib import asynccontextmanager, contextmanager

from neo4j import AsyncSession, Session

from src.infrastructure.database.neo4j import AsyncNeo4jClient, Neo4jClient
from src.shared.config.settings import settings


@asynccontextmanager
async def neo4j_async_session(*, database: str | None = None) -> AsyncIterator[AsyncSession]:
    """
    Get Neo4j asynchronous Session.constraint:only allowed Repository layer call Neo4jClient/AsyncNeo4jClient.
    """
    driver = await AsyncNeo4jClient.get_driver()
    async with driver.session(database=(database or settings.neo4j_database)) as session:
        yield session


@contextmanager
def neo4j_session(*, database: str | None = None) -> Iterator[Session]:
    """
    Get Neo4j sync Session.constraint:only allowed Repository layer call Neo4jClient/AsyncNeo4jClient.
    """
    driver = Neo4jClient.get_driver()
    with driver.session(database=(database or settings.neo4j_database)) as session:
        yield session
