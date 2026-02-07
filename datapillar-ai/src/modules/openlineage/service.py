# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-06

"""
OpenLineage Sink 服务

负责接收 OpenLineage 事件并入队处理。
"""

import logging
from typing import Any

from src.modules.openlineage.core.event_processor import get_event_processor
from src.modules.openlineage.schemas.events import RunEvent

logger = logging.getLogger(__name__)


class OpenLineageSinkService:
    """OpenLineage Sink 服务（接收事件并入队）"""

    def __init__(self) -> None:
        self._processor = get_event_processor()

    async def handle_event(self, event: RunEvent) -> dict[str, Any]:
        input_details = []
        for item in event.inputs:
            detail = {"namespace": item.namespace, "name": item.name}
            if item.facets:
                if "symlinks" in item.facets:
                    detail["symlinks"] = item.facets["symlinks"]
                if "catalog" in item.facets:
                    detail["catalog"] = item.facets["catalog"]
            input_details.append(detail)

        output_details = []
        for item in event.outputs:
            detail = {"namespace": item.namespace, "name": item.name}
            if item.facets:
                if "symlinks" in item.facets:
                    detail["symlinks"] = item.facets["symlinks"]
                if "catalog" in item.facets:
                    detail["catalog"] = item.facets["catalog"]
                if "columnLineage" in item.facets:
                    detail["columnLineage"] = item.facets["columnLineage"]
            output_details.append(detail)

        logger.info(
            "openlineage_event_received",
            extra={
                "data": {
                    "event_type": str(event.eventType) if event.eventType else None,
                    "job_namespace": event.job.namespace,
                    "inputs": input_details,
                    "outputs": output_details,
                }
            },
        )

        return await self._processor.put(event)

    def get_stats(self) -> dict[str, Any]:
        return self._processor.stats
