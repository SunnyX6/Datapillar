from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import OpenLineageLineageRepository
from src.modules.openlineage.schemas.neo4j import SQLNode

logger = structlog.get_logger()


class SQLWriter:
    def __init__(self) -> None:
        self._sql_written = 0

    @property
    def sql_written(self) -> int:
        return self._sql_written

    async def write(self, session: AsyncSession, sql: SQLNode) -> None:
        await OpenLineageLineageRepository.upsert_sql(
            session,
            id=sql.id,
            content=sql.content,
            dialect=sql.dialect,
            engine=sql.engine,
            job_namespace=sql.job_namespace,
            job_name=sql.job_name,
            created_by="OPENLINEAGE",
        )
        self._sql_written += 1
        logger.debug("sql_written", id=sql.id)
