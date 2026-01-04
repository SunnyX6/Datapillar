from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import OpenLineageLineageRepository

logger = structlog.get_logger()


class ColumnLineageWriter:
    """列级血缘（DERIVES_FROM）"""

    def __init__(self) -> None:
        self._column_lineage_written = 0

    @property
    def column_lineage_written(self) -> int:
        return self._column_lineage_written

    async def write(self, session: AsyncSession, lineage_data: list[dict]) -> None:
        await OpenLineageLineageRepository.link_column_lineage(
            session,
            lineage_data=lineage_data,
        )
        self._column_lineage_written += len(lineage_data)
        logger.debug("column_lineage_batch_written", count=len(lineage_data))
