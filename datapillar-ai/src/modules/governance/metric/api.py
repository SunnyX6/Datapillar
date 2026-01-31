# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
指标 AI 治理 API
"""

from fastapi import APIRouter, Request

from src.modules.governance.metric.schemas import AIFillRequest, AIFillResponse
from src.modules.governance.metric.service import metric_ai_service
from src.shared.web import build_success

router = APIRouter()


@router.post("/fill")
async def ai_fill(request: Request, payload: AIFillRequest):
    """AI 填写指标表单"""
    result: AIFillResponse = await metric_ai_service.fill(payload)
    return build_success(request=request, data=result.model_dump())
