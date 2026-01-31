# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import Lineage
from src.modules.openlineage.parsers.plans.types import LineageWritePlans

logger = logging.getLogger(__name__)


class ValueDomainLineageWriter:
    """列值域血缘（HAS_VALUE_DOMAIN）"""

    def __init__(self) -> None:
        self._column_valuedomain_lineage_written = 0

    @property
    def col_domain_edges(self) -> int:
        return self._column_valuedomain_lineage_written

    async def write(self, session: AsyncSession, plans: LineageWritePlans) -> None:
        for column_id, domain_code in plans.column_valuedomain_remove:
            await self._remove_relation(session, column_id, domain_code)

        for column_id, domain_code in plans.column_valuedomain_add:
            await self._add_relation(session, column_id, domain_code)

    async def _add_relation(self, session: AsyncSession, column_id: str, domain_code: str) -> None:
        record = await Lineage.add_column_valuedomain(
            session,
            column_id=column_id,
            domain_code=domain_code,
        )

        if record:
            self._column_valuedomain_lineage_written += 1
            logger.info(
                "column_valuedomain_relation_added",
                extra={
                    "data": {
                        "column_id": column_id,
                        "domain_code": domain_code,
                        "value_domain_id": record["valueDomainId"],
                    }
                },
            )
        else:
            logger.warning(
                "column_valuedomain_relation_add_failed",
                extra={
                    "data": {
                        "column_id": column_id,
                        "domain_code": domain_code,
                        "reason": "column or valuedomain not found",
                    }
                },
            )

    async def _remove_relation(
        self, session: AsyncSession, column_id: str, domain_code: str
    ) -> None:
        record = await Lineage.remove_column_valuedomain(
            session,
            column_id=column_id,
            domain_code=domain_code,
        )

        if record:
            logger.info(
                "column_valuedomain_relation_removed",
                extra={"data": {"column_id": column_id, "domain_code": domain_code}},
            )
        else:
            logger.debug(
                "column_valuedomain_relation_not_found",
                extra={"data": {"column_id": column_id, "domain_code": domain_code}},
            )
