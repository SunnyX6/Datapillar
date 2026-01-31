# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
ETL 模块 API 路由

提供工作流生成相关的 API 接口
"""

import logging
from typing import Any

from datapillar_oneagentic import Datapillar
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.sse import StreamManager
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from src.modules.etl.sse_protocol import RunRegistry, adapt_sse_stream
from src.shared.web import build_success

logger = logging.getLogger(__name__)

router = APIRouter()

# 全局 SSE 流管理器
etl_stream_manager = StreamManager()
etl_run_registry = RunRegistry()


class WorkflowRequest(BaseModel):
    """工作流生成请求"""

    user_input: str | None = Field(None, alias="userInput", description="用户输入")
    session_id: str | None = Field(None, alias="sessionId", description="会话ID")
    resume_value: Any | None = Field(None, alias="resumeValue", description="interrupt 恢复数据")
    interrupt_id: str | None = Field(None, alias="interruptId", description="interrupt 标识")

    class Config:
        populate_by_name = True


def _get_team(request: Request) -> Datapillar:
    """获取 ETL 团队实例"""
    team: Datapillar | None = getattr(request.app.state, "etl_team", None)
    if team is None:
        raise HTTPException(status_code=503, detail="ETL 团队尚未就绪")
    return team


def _build_session_key(team: Datapillar, session_id: str, user_id: str) -> SessionKey:
    return SessionKey(namespace=team.namespace, session_id=f"{user_id}:{session_id}")


class _TeamOrchestratorAdapter:
    def __init__(self, team: Datapillar) -> None:
        self._team = team

    async def stream(
        self,
        *,
        query: str | None = None,
        key: SessionKey,
        resume_value: Any | None = None,
    ):
        async for event in self._team.stream(
            query=query,
            session_id=key.session_id,
            resume_value=resume_value,
        ):
            yield event


@router.post("/workflow/chat")
async def chat(payload: WorkflowRequest, request: Request):
    """
    统一的 ETL 多智能体工作流入口

    自动判断场景：
    - 有 resume_value：interrupt 恢复
    - 有 user_input：新消息（后端自动判断是新会话还是续聊）
    """
    current_user = request.state.current_user
    team = _get_team(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    user_id = str(current_user.user_id)
    key = _build_session_key(team, payload.session_id, user_id)
    orchestrator = _TeamOrchestratorAdapter(team)

    if payload.resume_value is not None:
        etl_run_registry.start_run(str(key))
        logger.info(
            f"[ETL Resume] user={current_user.username}, userId={user_id}, sessionId={payload.session_id}"
        )
        await etl_stream_manager.chat(
            orchestrator=orchestrator,
            query=None,
            key=key,
            resume_value=payload.resume_value,
        )
    else:
        if not payload.user_input:
            raise HTTPException(status_code=400, detail="userInput 不能为空")

        etl_run_registry.start_run(str(key))
        logger.info(
            f"[ETL Chat] user={current_user.username}, userId={user_id}, sessionId={payload.session_id}"
        )
        await etl_stream_manager.chat(
            orchestrator=orchestrator,
            query=payload.user_input,
            key=key,
            resume_value=None,
        )

    return build_success(
        request=request,
        data={
            "success": True,
            "stream_url": f"/api/ai/etl/workflow/sse?sessionId={payload.session_id}",
        },
    )


@router.get("/workflow/sse")
async def workflow_sse(request: Request, session_id: str = Query(..., alias="sessionId")):
    """SSE/JSON 事件流：前端可用 Last-Event-ID 重连重放（断线续传）"""
    current_user = request.state.current_user
    team = _get_team(request)

    last_event_id_raw = request.headers.get("Last-Event-ID")
    last_event_id: int | None = None
    if last_event_id_raw:
        try:
            last_event_id = int(last_event_id_raw)
        except ValueError:
            last_event_id = None

    key = _build_session_key(team, session_id, str(current_user.user_id))
    storage_key = str(key)
    run_id = etl_run_registry.get_run(storage_key) or etl_run_registry.start_run(storage_key)

    def _finish_run() -> None:
        etl_run_registry.finish_run(storage_key)

    return EventSourceResponse(
        adapt_sse_stream(
            source=etl_stream_manager.subscribe(
                request=request,
                key=key,
                last_event_id=last_event_id,
            ),
            run_id=run_id,
            on_run_complete=_finish_run,
        ),
        ping=15,
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/session/clear")
async def clear_session(payload: WorkflowRequest, request: Request):
    """清除会话历史"""
    current_user = request.state.current_user
    team = _get_team(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    logger.info(
        f"[Clear] user={current_user.username}, userId={current_user.user_id}, "
        f"sessionId={payload.session_id}"
    )

    key = _build_session_key(team, payload.session_id, str(current_user.user_id))

    try:
        await team.clear_session(session_id=key.session_id)
    except Exception as exc:
        logger.error("清理 checkpoint 失败: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="清理会话失败") from exc

    etl_stream_manager.clear_session(key=key)
    etl_run_registry.finish_run(str(key))

    return build_success(
        request=request,
        data={
            "success": True,
            "message": "历史对话已清除",
        },
    )


@router.post("/workflow/abort")
async def abort_workflow(payload: WorkflowRequest, request: Request):
    """
    打断当前 run

    打断的是 run（当前执行），不是 session（对话历史）。
    用户可以在打断后继续在同一个 session 发送新消息。
    """
    current_user = request.state.current_user

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    user_id = str(current_user.user_id)
    team = _get_team(request)
    key = _build_session_key(team, payload.session_id, user_id)

    logger.info(
        f"[Abort] user={current_user.username}, userId={user_id}, "
        f"sessionId={payload.session_id}"
    )

    if payload.interrupt_id:
        aborted = await etl_stream_manager.abort_interrupt(
            key=key,
            interrupt_id=payload.interrupt_id,
        )
        message = "interrupt 已终止" if aborted else "没有等待中的 interrupt"
    else:
        aborted = await etl_stream_manager.abort(
            key=key,
        )
        message = "已停止" if aborted else "没有正在运行的任务"
    if aborted:
        etl_run_registry.finish_run(str(key))

    return build_success(
        request=request,
        data={
            "success": True,
            "aborted": aborted,
            "message": message,
        },
    )


@router.post("/session/compact")
async def compact_session(payload: WorkflowRequest, request: Request):
    """
    手动压缩会话记忆

    类似 Claude Code 的 /compact 命令。
    当前框架暂未启用压缩能力，会返回 not_implemented。
    """
    current_user = request.state.current_user
    team = _get_team(request)

    if not payload.session_id:
        raise HTTPException(status_code=400, detail="sessionId 不能为空")

    user_id = str(current_user.user_id)
    key = _build_session_key(team, payload.session_id, user_id)

    logger.info(
        f"[Compact] user={current_user.username}, userId={user_id}, "
        f"sessionId={payload.session_id}"
    )

    try:
        result = await team.compact_session(session_id=key.session_id)
    except Exception as exc:
        logger.error("压缩失败: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="压缩失败") from exc

    return build_success(request=request, data=result)


@router.get("/session/stats")
async def get_session_stats(
    request: Request,
    session_id: str = Query(..., alias="sessionId"),
):
    """
    获取会话统计信息

    返回：
    - session_id: 会话 ID
    - namespace: 命名空间
    - exists: 是否存在
    - message_count: 消息数量
    - deliverables_count: 交付物数量
    - active_agent: 当前活跃 Agent
    """
    current_user = request.state.current_user
    team = _get_team(request)
    key = _build_session_key(team, session_id, str(current_user.user_id))

    try:
        stats = await team.get_session_stats(session_id=key.session_id)
    except Exception as exc:
        logger.error("获取统计信息失败: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="获取统计信息失败") from exc

    return build_success(request=request, data=stats)
