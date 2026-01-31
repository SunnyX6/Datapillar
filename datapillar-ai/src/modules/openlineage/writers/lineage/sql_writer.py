# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.kg.dto import SQLDTO
from src.infrastructure.repository.openlineage import Lineage

logger = logging.getLogger(__name__)


class SQLWriter:
    def __init__(self) -> None:
        self._sql_written = 0

    @property
    def sql_written(self) -> int:
        return self._sql_written

    async def write(self, session: AsyncSession, sql: SQLDTO) -> None:
        await Lineage.upsert_sql(
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
        logger.debug(
            "sql_written",
            extra={"data": {"id": sql.id}},
        )
