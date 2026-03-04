# @author Sunny
# @date 2026-01-27

"""
indicator AI governance API
"""

from typing import Any

from fastapi import APIRouter, Request

from src.modules.governance.metric.schemas import AIFillRequest, AIFillResponse
from src.modules.governance.metric.service import metric_ai_service
from src.shared.web import ApiResponse, ApiSuccessResponseSchema

router = APIRouter()


@router.post("/fill", response_model=ApiSuccessResponseSchema)
async def ai_fill(request: Request, payload: AIFillRequest) -> dict[str, Any]:
    """AI Fill out the indicator form"""
    current_user = request.state.current_user
    result: AIFillResponse = await metric_ai_service.fill(
        payload,
        tenant_id=current_user.tenant_id,
        user_id=current_user.user_id,
    )
    return ApiResponse.success(data=result.model_dump())
