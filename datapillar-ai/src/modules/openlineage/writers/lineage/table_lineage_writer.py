# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.kg.dto import SQLDTO
from src.infrastructure.repository.openlineage import Lineage

logger = logging.getLogger(__name__)


class TableLineageWriter:
    """表级血缘（INPUT_OF/OUTPUT_TO）"""

    def __init__(self) -> None:
        self._table_lineage_written = 0

    @property
    def table_lineage_written(self) -> int:
        return self._table_lineage_written

    async def write(
        self,
        session: AsyncSession,
        *,
        sql: SQLDTO,
        input_table_ids: list[str],
        output_table_ids: list[str],
    ) -> None:
        if input_table_ids:
            await Lineage.link_sql_inputs(
                session,
                sql_id=sql.id,
                table_ids=input_table_ids,
            )
            self._table_lineage_written += len(input_table_ids)
            logger.debug(
                "table_input_lineage_batch_written",
                extra={"data": {"count": len(input_table_ids)}},
            )

        if output_table_ids:
            await Lineage.link_sql_outputs(
                session,
                sql_id=sql.id,
                table_ids=output_table_ids,
            )
            self._table_lineage_written += len(output_table_ids)
            logger.debug(
                "table_output_lineage_batch_written",
                extra={"data": {"count": len(output_table_ids)}},
            )
