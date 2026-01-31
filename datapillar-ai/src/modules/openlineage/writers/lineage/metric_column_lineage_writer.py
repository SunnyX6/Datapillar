# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import Lineage

logger = logging.getLogger(__name__)


class MetricColumnLineageWriter:
    """AtomicMetric 与 Column 的血缘（MEASURES / FILTERS_BY）"""

    def __init__(self) -> None:
        self._metric_lineage_written = 0

    @property
    def metric_lineage_written(self) -> int:
        return self._metric_lineage_written

    async def write_measures(
        self, session: AsyncSession, *, metric_id: str, column_ids: list[str]
    ) -> None:
        await Lineage.set_metric_measures(
            session,
            metric_id=metric_id,
            column_ids=column_ids,
        )
        self._metric_lineage_written += len(column_ids)
        logger.debug(
            "metric_measures_written",
            extra={"data": {"metric_id": metric_id, "count": len(column_ids)}},
        )

    async def write_filters(
        self, session: AsyncSession, *, metric_id: str, column_ids: list[str]
    ) -> None:
        await Lineage.set_metric_filters(
            session,
            metric_id=metric_id,
            column_ids=column_ids,
        )
        self._metric_lineage_written += len(column_ids)
        logger.debug(
            "metric_filters_written",
            extra={"data": {"metric_id": metric_id, "count": len(column_ids)}},
        )
