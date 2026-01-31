# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import Lineage

logger = logging.getLogger(__name__)


class ColumnLineageWriter:
    """列级血缘（DERIVES_FROM）"""

    def __init__(self) -> None:
        self._column_lineage_written = 0

    @property
    def column_lineage_written(self) -> int:
        return self._column_lineage_written

    async def write(self, session: AsyncSession, lineage_data: list[dict]) -> None:
        await Lineage.link_column_lineage(
            session,
            lineage_data=lineage_data,
        )
        self._column_lineage_written += len(lineage_data)
        logger.debug(
            "column_lineage_batch_written",
            extra={"data": {"count": len(lineage_data)}},
        )
