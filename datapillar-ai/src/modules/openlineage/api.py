# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

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

import logging
from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from src.modules.openlineage.core.event_processor import event_processor
from src.modules.openlineage.schemas.events import RunEvent
from src.shared.web import build_error, build_success

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post(
    "",
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
async def receive_event(request: Request, event: RunEvent):
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
            extra={
                "data": {
                    "event_type": str(event.eventType) if event.eventType else None,
                    "job_namespace": event.job.namespace,
                    "inputs": input_details,
                    "outputs": output_details,
                }
            },
        )

        result = await event_processor.put(event)

        if not result.get("success"):
            error = result.get("error", "Unknown error")
            if "Queue full" in error:
                status_code = status.HTTP_503_SERVICE_UNAVAILABLE
                code = "SERVICE_UNAVAILABLE"
            else:
                status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
                code = "SERVER_ERROR"
            return JSONResponse(
                status_code=status_code,
                content=build_error(
                    request=request,
                    status=status_code,
                    code=code,
                    message=error,
                ),
            )

        return build_success(
            request=request,
            data={
                "success": True,
                "queued": True,
                "queue_size": result.get("queue_size"),
                "message": "Event queued",
            },
        )

    except Exception as e:
        logger.error(
            "receive_event_failed",
            extra={"data": {"error": str(e)}},
        )
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=build_error(
                request=request,
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
                code="SERVER_ERROR",
                message=str(e),
            ),
        )


@router.get("/stats", summary="获取统计信息")
async def get_stats(request: Request) -> dict[str, Any]:
    """获取统计信息"""
    return build_success(request=request, data=event_processor.stats)
