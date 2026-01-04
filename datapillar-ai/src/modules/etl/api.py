"""
Agent API 路由

提供工作流生成相关的 API 接口
"""

import logging
from typing import Any

from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from src.modules.etl.orchestrator_v2 import EtlOrchestratorV2
from src.modules.etl.sse_manager import etl_stream_manager

logger = logging.getLogger(__name__)


router = APIRouter()


class WorkflowRequest(BaseModel):
    """工作流生成请求"""

    user_input: str | None = Field(None, alias="userInput", description="用户输入")
    session_id: str | None = Field(None, alias="sessionId", description="会话ID")
    resume_value: Any | None = Field(None, alias="resumeValue", description="断点恢复数据")

    class Config:
        populate_by_name = True


def _get_orchestrator(request: Request) -> EtlOrchestratorV2:
    """获取 EtlOrchestratorV2 实例"""
    orchestrator: EtlOrchestratorV2 | None = getattr(request.app.state, "orchestrator", None)
    if orchestrator is None:
        raise HTTPException(status_code=503, detail="Orchestrator 尚未就绪")
    return orchestrator


@router.post("/workflow/start")
async def start_workflow_stream(payload: WorkflowRequest, request: Request):
    """启动 ETL 多智能体工作流（SSE/JSON 事件流）"""
    current_user = request.state.current_user
    orchestrator = _get_orchestrator(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")
    if not payload.user_input:
        raise HTTPException(status_code=400, detail="userInput 不能为空")

    logger.info(
        f"[ETL Start] user={current_user.username}, userId={current_user.user_id}, sessionId={payload.session_id}"
    )

    await etl_stream_manager.start(
        orchestrator=orchestrator,
        user_input=payload.user_input,
        session_id=payload.session_id,
        user_id=str(current_user.user_id),
    )

    return {
        "success": True,
        "stream_url": f"/api/ai/etl/workflow/sse?sessionId={payload.session_id}",
    }


@router.post("/workflow/continue")
async def continue_workflow_stream(payload: WorkflowRequest, request: Request):
    """继续 ETL 多智能体工作流（用于 interrupt 人机交互的 resumeValue，不是断线续传）"""
    current_user = request.state.current_user
    orchestrator = _get_orchestrator(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")
    if payload.resume_value is None:
        raise HTTPException(status_code=400, detail="resumeValue 不能为空")

    logger.info(
        f"[ETL Continue] user={current_user.username}, userId={current_user.user_id}, sessionId={payload.session_id}"
    )

    await etl_stream_manager.continue_from_interrupt(
        orchestrator=orchestrator,
        session_id=payload.session_id,
        user_id=str(current_user.user_id),
        resume_value=payload.resume_value,
    )

    return {"success": True}


@router.get("/workflow/sse")
async def workflow_sse(request: Request, session_id: str = Query(..., alias="sessionId")):
    """SSE/JSON 事件流：前端可用 Last-Event-ID 重连重放（断线续传）"""
    current_user = request.state.current_user

    last_event_id_raw = request.headers.get("Last-Event-ID")
    last_event_id: int | None = None
    if last_event_id_raw:
        try:
            last_event_id = int(last_event_id_raw)
        except ValueError:
            last_event_id = None

    return EventSourceResponse(
        etl_stream_manager.subscribe(
            request=request,
            session_id=session_id,
            user_id=str(current_user.user_id),
            last_event_id=last_event_id,
        ),
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
    orchestrator = _get_orchestrator(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    logger.info(
        f"[Clear] user={current_user.username}, userId={current_user.user_id}, "
        f"sessionId={payload.session_id}"
    )

    try:
        await orchestrator.clear_session(
            session_id=payload.session_id,
            user_id=str(current_user.user_id),
        )
    except Exception as exc:
        logger.error("清理 checkpoint 失败: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="清理会话失败") from exc

    etl_stream_manager.clear_session(
        user_id=str(current_user.user_id),
        session_id=payload.session_id,
    )

    return {
        "success": True,
        "message": "历史对话已清除",
    }
