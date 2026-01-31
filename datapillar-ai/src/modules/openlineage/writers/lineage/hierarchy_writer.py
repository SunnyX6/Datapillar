# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import Lineage
from src.modules.openlineage.parsers.plans.types import LineageWritePlans

logger = logging.getLogger(__name__)


class HierarchyWriter:
    """
    结构层级关系写入器（HAS_*）

    - Catalog -[:HAS_SCHEMA]-> Schema
    - Schema -[:HAS_TABLE]-> Table
    - Table -[:HAS_COLUMN]-> Column
    """

    def __init__(self) -> None:
        self._hierarchy_edges_written = 0

    @property
    def hierarchy_edges_written(self) -> int:
        return self._hierarchy_edges_written

    async def write(self, session: AsyncSession, plans: LineageWritePlans) -> None:
        for catalog_id, schema_id in plans.catalog_schema_edges:
            await Lineage.link_catalog_schema(
                session,
                catalog_id=catalog_id,
                schema_id=schema_id,
            )
            self._hierarchy_edges_written += 1

        for schema_id, table_id in plans.schema_table_edges:
            await Lineage.link_schema_table(
                session,
                schema_id=schema_id,
                table_id=table_id,
            )
            self._hierarchy_edges_written += 1

        for table_id, column_ids in plans.table_column_edges:
            await Lineage.link_table_columns(
                session,
                table_id=table_id,
                column_ids=column_ids,
            )
            self._hierarchy_edges_written += len(column_ids)

        if self._hierarchy_edges_written:
            logger.debug(
                "hierarchy_edges_written",
                extra={
                    "data": {
                        "operation": plans.operation,
                        "count": self._hierarchy_edges_written,
                    }
                },
            )
