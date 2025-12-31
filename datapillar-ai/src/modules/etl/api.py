# -*- coding: utf-8 -*-
"""
Agent API 路由

提供工作流生成相关的 API 接口
"""

import json
from typing import AsyncGenerator, Optional, Any

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field
import logging

logger = logging.getLogger(__name__)
from sse_starlette.sse import EventSourceResponse

from src.modules.etl.orchestrator import EtlOrchestrator


router = APIRouter()


class WorkflowRequest(BaseModel):
    """工作流生成请求"""
    user_input: Optional[str] = Field(None, alias="userInput", description="用户输入")
    session_id: Optional[str] = Field(None, alias="sessionId", description="会话ID")
    resume_value: Optional[Any] = Field(None, alias="resumeValue", description="断点恢复数据")

    class Config:
        populate_by_name = True


def _get_orchestrator(request: Request) -> EtlOrchestrator:
    """获取 EtlOrchestrator 实例"""
    orchestrator: Optional[EtlOrchestrator] = getattr(request.app.state, "orchestrator", None)
    if orchestrator is None:
        raise HTTPException(status_code=503, detail="Orchestrator 尚未就绪")
    return orchestrator


@router.post("/workflow/sse")
async def generate_workflow_stream(
    payload: WorkflowRequest,
    request: Request,
):
    """多智能体流式生成工作流"""
    current_user = request.state.current_user
    orchestrator = _get_orchestrator(request)

    async def event_generator() -> AsyncGenerator[dict[str, str], None]:
        if not payload.session_id:
            raise HTTPException(status_code=400, detail="sessionId 不能为空")

        logger.info(
            f"[Stream] user={current_user.username}, userId={current_user.user_id}, "
            f"userInput={payload.user_input}, sessionId={payload.session_id}"
        )

        async for event in orchestrator.stream(
            user_input=payload.user_input,
            session_id=payload.session_id,
            user_id=str(current_user.user_id),
            resume_value=payload.resume_value,
        ):
            event_type = event.get("event_type", "unknown")
            event_data = event.get("data", {})

            sse_payload = {
                "data": json.dumps({
                    "eventType": event_type,
                    "agent": event.get("agent"),
                    "tool": event.get("tool"),
                    "data": event_data,
                }, ensure_ascii=False),
                "event": event_type,
            }
            yield sse_payload

    return EventSourceResponse(
        event_generator(),
        ping=15,
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/session/clear")
async def clear_session(
    payload: WorkflowRequest,
    request: Request,
):
    """清除会话历史"""
    current_user = request.state.current_user

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    logger.info(
        f"[Clear] user={current_user.username}, userId={current_user.user_id}, "
        f"sessionId={payload.session_id}"
    )

    return {
        "success": True,
        "message": "历史对话已清除",
    }
