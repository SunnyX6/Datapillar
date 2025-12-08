# -*- coding: utf-8 -*-
"""
Agent API 路由

提供工作流生成相关的 API 接口
"""

import json
from typing import AsyncGenerator, Optional

from fastapi import APIRouter, HTTPException, Request
import logging

logger = logging.getLogger(__name__)
from sse_starlette.sse import EventSourceResponse

from src.agent.orchestrator import Orchestrator
from src.agent.schemas import GenerateWorkflowRequest

router = APIRouter()


def _get_orchestrator(request: Request) -> Orchestrator:
    """获取 Orchestrator 实例"""
    orchestrator: Optional[Orchestrator] = getattr(request.app.state, "orchestrator", None)
    if orchestrator is None:
        raise HTTPException(status_code=503, detail="Orchestrator 尚未就绪")
    return orchestrator


@router.post("/workflow/sse")
async def generate_workflow_stream(
    payload: GenerateWorkflowRequest,
    request: Request,
):
    """多智能体流式生成工作流"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    orchestrator = _get_orchestrator(request)

    async def event_generator() -> AsyncGenerator[dict[str, str], None]:
        if not payload.sessionId:
            raise HTTPException(status_code=400, detail="sessionId 不能为空")

        logger.info(
            f"[Stream] user={current_user.username}, userId={current_user.user_id}, "
            f"userInput={payload.userInput}, sessionId={payload.sessionId}"
        )

        async for stream_event in orchestrator.as_stream(
            user_input=payload.userInput,
            session_id=payload.sessionId,
            user_id=str(current_user.user_id),
            resume_value=payload.resumeValue,
        ):
            try:
                event_obj = json.loads(stream_event)
            except json.JSONDecodeError:
                event_obj = None

            sse_payload: dict[str, str] = {"data": stream_event}
            if isinstance(event_obj, dict):
                event_type = event_obj.get("eventType")
                event_id = event_obj.get("eventId")
                if event_type:
                    sse_payload["event"] = event_type
                if event_id:
                    sse_payload["id"] = event_id

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
    payload: GenerateWorkflowRequest,
    request: Request,
):
    """清除会话历史"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user

    if not payload.sessionId:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    logger.info(
        f"[Clear] user={current_user.username}, userId={current_user.user_id}, "
        f"sessionId={payload.sessionId}"
    )

    orchestrator = _get_orchestrator(request)
    deleted_count = await orchestrator.clear_session(
        user_id=str(current_user.user_id),
        session_id=payload.sessionId,
    )

    return {
        "success": True,
        "message": "历史对话已清除",
        "deletedKeys": deleted_count,
    }
