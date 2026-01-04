"""
OpenLineage Sink API

提供 HTTP 端点接收 OpenLineage 事件

配置示例（Producer 端 openlineage.yml）：
```yaml
transport:
  type: http
  url: http://datapillar-ai:6003
  endpoint: /api/ai/openlineage
```
"""

from typing import Any

import structlog
from fastapi import APIRouter, HTTPException, Response, status
from pydantic import BaseModel

from src.modules.openlineage.core.event_processor import event_processor
from src.modules.openlineage.schemas.events import RunEvent

logger = structlog.get_logger()

router = APIRouter()


class SinkResponse(BaseModel):
    """Sink 响应"""

    success: bool
    message: str | None = None
    error: str | None = None
    queued: bool | None = None
    queue_size: int | None = None


@router.post(
    "",
    response_model=SinkResponse,
    summary="接收 OpenLineage 事件",
    description="""
接收来自 Flink/Spark/Gravitino 等系统的 OpenLineage RunEvent。

处理流程：
1. 接收事件
2. 入队缓冲
3. 批量写入 Neo4j

注意：retry、rate_limit、filter 由 Producer 端配置
""",
)
async def receive_event(event: RunEvent, response: Response) -> SinkResponse:
    """接收 OpenLineage 事件"""
    try:
        # 调试日志：打印完整 facets
        input_details = []
        for i in event.inputs:
            detail = {"namespace": i.namespace, "name": i.name}
            if i.facets:
                if "symlinks" in i.facets:
                    detail["symlinks"] = i.facets["symlinks"]
                if "catalog" in i.facets:
                    detail["catalog"] = i.facets["catalog"]
            input_details.append(detail)

        output_details = []
        for o in event.outputs:
            detail = {"namespace": o.namespace, "name": o.name}
            if o.facets:
                if "symlinks" in o.facets:
                    detail["symlinks"] = o.facets["symlinks"]
                if "catalog" in o.facets:
                    detail["catalog"] = o.facets["catalog"]
                if "columnLineage" in o.facets:
                    detail["columnLineage"] = o.facets["columnLineage"]
            output_details.append(detail)

        logger.info(
            "openlineage_event_received",
            event_type=str(event.eventType) if event.eventType else None,
            job_namespace=event.job.namespace,
            inputs=input_details,
            outputs=output_details,
        )

        result = await event_processor.put(event)

        if not result.get("success"):
            error = result.get("error", "Unknown error")
            if "Queue full" in error:
                response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
            return SinkResponse(success=False, error=error)

        return SinkResponse(
            success=True,
            queued=True,
            queue_size=result.get("queue_size"),
            message="Event queued",
        )

    except Exception as e:
        logger.error("receive_event_failed", error=str(e))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e),
        ) from e


@router.get("/stats", summary="获取统计信息")
async def get_stats() -> dict[str, Any]:
    """获取统计信息"""
    return event_processor.stats
