"""
指标 AI 治理 API
"""

from fastapi import APIRouter

from src.modules.governance.metric.ai_service import metric_ai_service
from src.modules.governance.metric.schemas import AIFillRequest, AIFillResponse

router = APIRouter()


@router.post("/fill", response_model=AIFillResponse)
async def ai_fill(request: AIFillRequest) -> AIFillResponse:
    """AI 填写指标表单"""
    return await metric_ai_service.fill(request)
