from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import Lineage
from src.modules.openlineage.parsers.plans.types import LineageWritePlans

logger = structlog.get_logger()


class MetricRelationshipWriter:
    """指标相关关系（Schema->Metric、Metric 父子）"""

    def __init__(self) -> None:
        self._metric_schema_edges_written = 0
        self._metric_parent_edges_written = 0

    @property
    def metric_schema_edges(self) -> int:
        return self._metric_schema_edges_written

    @property
    def metric_parent_edges(self) -> int:
        return self._metric_parent_edges_written

    async def write(self, session: AsyncSession, plans: LineageWritePlans) -> None:
        for schema_id, metric_id in plans.schema_metric_edges:
            await Lineage.link_schema_metric(
                session,
                schema_id=schema_id,
                metric_id=metric_id,
            )
            self._metric_schema_edges_written += 1

        for item in plans.metric_parent_relationships:
            await Lineage.set_metric_parents(
                session,
                child_label=item["child_label"],
                child_id=item["child_id"],
                rel_type=item["rel_type"],
                parent_ids=item["parent_ids"],
            )
            self._metric_parent_edges_written += len(item["parent_ids"])

        if self._metric_schema_edges_written or self._metric_parent_edges_written:
            logger.debug(
                "metric_relationships_written",
                schema_edges=self._metric_schema_edges_written,
                parent_edges=self._metric_parent_edges_written,
            )
