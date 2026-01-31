# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j 会话管理（Repository 层通用能力）

目的：
- 业务模块（src/modules/*）禁止直接触碰 Neo4j Driver/Client
- Repository 目录下提供统一的 session 获取方式，业务模块只依赖“会话上下文”

说明：
- driver 的生命周期仍由 src/infrastructure/database/neo4j.py 管理（全局连接池）
- 这里仅负责提供同步/异步 session 的 context manager
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
    获取 Neo4j 异步 Session。

    约束：仅允许 Repository 层调用 Neo4jClient/AsyncNeo4jClient。
    """
    driver = await AsyncNeo4jClient.get_driver()
    async with driver.session(database=(database or settings.neo4j_database)) as session:
        yield session


@contextmanager
def neo4j_session(*, database: str | None = None) -> Iterator[Session]:
    """
    获取 Neo4j 同步 Session。

    约束：仅允许 Repository 层调用 Neo4jClient/AsyncNeo4jClient。
    """
    driver = Neo4jClient.get_driver()
    with driver.session(database=(database or settings.neo4j_database)) as session:
        yield session
