# @author Sunny
# @date 2026-01-27

"""OpenLineage Sink API。"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Request

from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.service import OpenLineageSinkService
from src.shared.web import ApiResponse, ApiSuccessResponseSchema

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
    response_model=ApiSuccessResponseSchema,
)
async def receive_event(request: Request, event: RunEvent) -> dict[str, Any]:
    """接收 OpenLineage 事件。"""
    current_user = request.state.current_user
    result = await _get_service().handle_event(
        event,
        tenant_id=current_user.tenant_id,
        operator_user_id=current_user.user_id,
    )
    return ApiResponse.success(
        data={
            "success": True,
            "queued": True,
            "queue_size": result.get("queue_size"),
            "message": "Event queued",
        },
    )


@router.get("/stats", summary="获取统计信息")
async def get_stats(request: Request) -> dict[str, Any]:
    """获取统计信息。"""
    current_user = request.state.current_user
    return ApiResponse.success(data=_get_service().get_stats(tenant_id=current_user.tenant_id))
