# @author Sunny
# @date 2026-02-06

"""OpenLineage Sink 服务。"""

from __future__ import annotations

import logging
from typing import Any

from src.modules.openlineage.core.event_processor import (
    QueuedOpenLineageEvent,
    get_event_processor,
)
from src.modules.openlineage.schemas.events import RunEvent
from src.shared.exception import InternalException, ServiceUnavailableException

logger = logging.getLogger(__name__)


class OpenLineageSinkService:
    """OpenLineage Sink 服务（接收事件并入队）。"""

    def __init__(self) -> None:
        self._processor = get_event_processor()

    async def handle_event(
        self,
        event: RunEvent,
        *,
        tenant_id: int,
        operator_user_id: int,
    ) -> dict[str, Any]:
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
                    "tenant_id": tenant_id,
                    "operator_user_id": operator_user_id,
                    "event_type": str(event.eventType) if event.eventType else None,
                    "job_namespace": event.job.namespace,
                    "inputs": input_details,
                    "outputs": output_details,
                }
            },
        )

        queued_event = QueuedOpenLineageEvent(
            tenant_id=tenant_id,
            operator_user_id=operator_user_id,
            event=event,
        )
        try:
            result = await self._processor.put(queued_event)
        except Exception as exc:
            raise InternalException("OpenLineage 事件入队失败", cause=exc) from exc

        if not result.get("success"):
            raise ServiceUnavailableException("事件队列已满")

        return result

    def get_stats(self, *, tenant_id: int | None = None) -> dict[str, Any]:
        if tenant_id is None:
            return self._processor.stats
        return self._processor.get_tenant_stats(tenant_id)
