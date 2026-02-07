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
  url: http://datapillar-ai:7003
  endpoint: /api/ai/openlineage
```
"""

from typing import Any

import logging
from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from src.modules.openlineage.service import OpenLineageSinkService
from src.modules.openlineage.schemas.events import RunEvent
from src.shared.web import ApiResponse

logger = logging.getLogger(__name__)

router = APIRouter()
_service: OpenLineageSinkService | None = None


def _get_service() -> OpenLineageSinkService:
    global _service
    if _service is None:
        _service = OpenLineageSinkService()
    return _service


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
        service = _get_service()
        result = await service.handle_event(event)

        if not result.get("success"):
            error = result.get("error", "Unknown error")
            if "Queue full" in error:
                status_code = status.HTTP_503_SERVICE_UNAVAILABLE
                code = "SERVICE_UNAVAILABLE"
            else:
                status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
                code = "INTERNAL_ERROR"
            return JSONResponse(
                status_code=status_code,
                content=ApiResponse.error(
                    request=request,
                    status=status_code,
                    code=code,
                    message=error,
                ),
            )

        return ApiResponse.success(
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
            content=ApiResponse.error(
                request=request,
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
                code="INTERNAL_ERROR",
                message=str(e),
            ),
        )


@router.get("/stats", summary="获取统计信息")
async def get_stats(request: Request) -> dict[str, Any]:
    """获取统计信息"""
    service = _get_service()
    return ApiResponse.success(request=request, data=service.get_stats())
