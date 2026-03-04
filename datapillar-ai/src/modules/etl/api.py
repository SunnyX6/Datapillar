# @author Sunny
# @date 2026-01-27

"""ETL module API routing."""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic import Datapillar
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.sse import StreamManager
from fastapi import APIRouter, Query, Request
from pydantic import BaseModel, ConfigDict, Field
from sse_starlette.sse import EventSourceResponse

from src.modules.etl.agents import create_etl_team
from src.modules.etl.model_runtime import build_etl_datapillar_config
from src.modules.etl.sse_protocol import RunRegistry, adapt_sse_stream
from src.shared.exception import BadRequestException, ConflictException, InternalException
from src.shared.web import ApiResponse, ApiSuccessResponseSchema

logger = logging.getLogger(__name__)

router = APIRouter()

etl_stream_manager = StreamManager()
etl_run_registry = RunRegistry()


class WorkflowChatModel(BaseModel):
    """Workflow Chat model identification."""

    model_config = ConfigDict(populate_by_name=True)

    ai_model_id: int = Field(..., alias="aiModelId", gt=0)
    provider_model_id: str = Field(..., alias="providerModelId", min_length=1)


class WorkflowRequest(BaseModel):
    """Workflow generation request."""

    model_config = ConfigDict(populate_by_name=True)

    user_input: str | None = Field(None, alias="userInput", description="user input")
    session_id: str | None = Field(None, alias="sessionId", description="sessionID")
    resume_value: Any | None = Field(
        None, alias="resumeValue", description="interrupt Recover data"
    )
    interrupt_id: str | None = Field(None, alias="interruptId", description="interrupt logo")
    model: WorkflowChatModel | None = Field(None, alias="model", description="This session model")


def _get_session_teams(request: Request) -> dict[str, Datapillar]:
    teams: dict[str, Datapillar] | None = getattr(request.app.state, "etl_session_teams", None)
    if teams is None:
        teams = {}
        request.app.state.etl_session_teams = teams
    return teams


def _get_session_models(request: Request) -> dict[str, tuple[int, str]]:
    models: dict[str, tuple[int, str]] | None = getattr(
        request.app.state, "etl_session_models", None
    )
    if models is None:
        models = {}
        request.app.state.etl_session_models = models
    return models


def _build_namespace(tenant_id: int) -> str:
    return f"etl_team_{tenant_id}"


def _build_session_key(*, tenant_id: int, session_id: str, user_id: str) -> SessionKey:
    return SessionKey(
        namespace=_build_namespace(tenant_id),
        session_id=f"{user_id}:{session_id}",
    )


def _normalize_provider_model_id(provider_model_id: str) -> str:
    normalized = provider_model_id.strip()
    if not normalized:
        raise BadRequestException("model.providerModelId cannot be empty")
    return normalized


def _get_bound_team(request: Request, storage_key: str) -> Datapillar | None:
    return _get_session_teams(request).get(storage_key)


def _resolve_session_team(
    *,
    request: Request,
    tenant_id: int,
    tenant_code: str,
    user_id: int,
    key: SessionKey,
    requested_model: tuple[int, str],
) -> Datapillar:
    storage_key = str(key)
    session_models = _get_session_models(request)
    bound_model = session_models.get(storage_key)
    if bound_model and bound_model != requested_model:
        raise ConflictException("same sessionId Switch not allowed model")

    team = _get_bound_team(request, storage_key)
    if team is not None:
        if not bound_model:
            session_models[storage_key] = requested_model
        return team

    config = build_etl_datapillar_config(
        tenant_id=tenant_id,
        user_id=user_id,
        tenant_code=tenant_code,
        ai_model_id=requested_model[0],
        provider_model_id=requested_model[1],
    )
    team = create_etl_team(
        config=config,
        namespace=key.namespace,
        tenant_id=tenant_id,
    )
    _get_session_teams(request)[storage_key] = team
    session_models[storage_key] = requested_model
    return team


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


@router.post("/chat", response_model=ApiSuccessResponseSchema)
async def chat(payload: WorkflowRequest, request: Request) -> dict[str, Any]:
    """unified ETL Multi-agent workflow portal."""
    current_user = request.state.current_user

    if not payload.session_id:
        raise BadRequestException("sessionId cannot be empty")
    if payload.model is None:
        raise BadRequestException("model cannot be empty")

    user_id = current_user.user_id
    user_id_str = str(user_id)
    requested_model = (
        payload.model.ai_model_id,
        _normalize_provider_model_id(payload.model.provider_model_id),
    )
    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=payload.session_id,
        user_id=user_id_str,
    )
    team = _resolve_session_team(
        request=request,
        tenant_id=current_user.tenant_id,
        tenant_code=current_user.tenant_code,
        user_id=user_id,
        key=key,
        requested_model=requested_model,
    )
    orchestrator = _TeamOrchestratorAdapter(team)

    if payload.resume_value is not None:
        etl_run_registry.start_run(str(key))
        logger.info(
            "[ETL Resume] user=%s, userId=%s, sessionId=%s, aiModelId=%s, providerModelId=%s",
            current_user.username,
            user_id_str,
            payload.session_id,
            requested_model[0],
            requested_model[1],
        )
        await etl_stream_manager.chat(
            orchestrator=orchestrator,
            query=None,
            key=key,
            resume_value=payload.resume_value,
        )
    else:
        if not payload.user_input:
            raise BadRequestException("userInput cannot be empty")

        etl_run_registry.start_run(str(key))
        logger.info(
            "[ETL Chat] user=%s, userId=%s, sessionId=%s, aiModelId=%s, providerModelId=%s",
            current_user.username,
            user_id_str,
            payload.session_id,
            requested_model[0],
            requested_model[1],
        )
        await etl_stream_manager.chat(
            orchestrator=orchestrator,
            query=payload.user_input,
            key=key,
            resume_value=None,
        )

    return ApiResponse.success(
        data={
            "success": True,
        },
    )


@router.get(
    "/sse",
    response_class=EventSourceResponse,
    responses={
        200: {
            "description": "SSE event stream",
            "content": {
                "text/event-stream": {
                    "schema": {
                        "type": "string",
                        "example": "event: connected\\ndata: {\"event\":\"connected\"}\\n\\n",
                    }
                }
            },
        }
    },
)
async def workflow_sse(
    request: Request, session_id: str = Query(..., alias="sessionId")
) -> EventSourceResponse:
    """SSE/JSON event stream:
    Frontend available Last-Event-ID Reconnect and playback(Resume upload after disconnection)."""
    current_user = request.state.current_user

    last_event_id_raw = request.headers.get("Last-Event-ID")
    last_event_id: int | None = None
    if last_event_id_raw:
        try:
            last_event_id = int(last_event_id_raw)
        except ValueError:
            last_event_id = None

    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=session_id,
        user_id=str(current_user.user_id),
    )
    storage_key = str(key)
    if storage_key not in _get_session_models(request):
        raise BadRequestException("Session not initialized,Please call first /chat")

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


@router.post("/session/clear", response_model=ApiSuccessResponseSchema)
async def clear_session(payload: WorkflowRequest, request: Request) -> dict[str, Any]:
    """Clear session history."""
    current_user = request.state.current_user

    if not payload.session_id:
        raise BadRequestException("sessionId cannot be empty")

    logger.info(
        "[Clear] user=%s, userId=%s, sessionId=%s",
        current_user.username,
        current_user.user_id,
        payload.session_id,
    )

    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=payload.session_id,
        user_id=str(current_user.user_id),
    )
    storage_key = str(key)
    team = _get_bound_team(request, storage_key)

    if team is not None:
        try:
            await team.clear_session(session_id=key.session_id)
        except Exception as exc:
            logger.error("clean up checkpoint failed:%s", exc, exc_info=True)
            raise InternalException("Cleaning session failed", cause=exc) from exc

    etl_stream_manager.clear_session(key=key)
    etl_run_registry.finish_run(storage_key)
    _get_session_teams(request).pop(storage_key, None)
    _get_session_models(request).pop(storage_key, None)

    return ApiResponse.success(
        data={
            "success": True,
            "message": "Historical conversations have been cleared",
        },
    )


@router.post("/abort", response_model=ApiSuccessResponseSchema)
async def abort_workflow(payload: WorkflowRequest, request: Request) -> dict[str, Any]:
    """interrupt current run."""
    current_user = request.state.current_user

    if not payload.session_id:
        raise BadRequestException("sessionId cannot be empty")

    user_id = str(current_user.user_id)
    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=payload.session_id,
        user_id=user_id,
    )

    logger.info(
        "[Abort] user=%s, userId=%s, sessionId=%s",
        current_user.username,
        user_id,
        payload.session_id,
    )

    if payload.interrupt_id:
        aborted = await etl_stream_manager.abort_interrupt(
            key=key,
            interrupt_id=payload.interrupt_id,
        )
        message = "interrupt terminated" if aborted else "nothing waiting interrupt"
    else:
        aborted = await etl_stream_manager.abort(
            key=key,
        )
        message = "Stopped" if aborted else "No tasks running"

    if aborted:
        etl_run_registry.finish_run(str(key))

    return ApiResponse.success(
        data={
            "success": True,
            "aborted": aborted,
            "message": message,
        },
    )


@router.post("/session/compact", response_model=ApiSuccessResponseSchema)
async def compact_session(payload: WorkflowRequest, request: Request) -> dict[str, Any]:
    """Manually compress session memory."""
    current_user = request.state.current_user

    if not payload.session_id:
        raise BadRequestException("sessionId cannot be empty")

    user_id = str(current_user.user_id)
    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=payload.session_id,
        user_id=user_id,
    )
    team = _get_bound_team(request, str(key))
    if team is None:
        raise BadRequestException("The session does not exist or has expired")

    logger.info(
        "[Compact] user=%s, userId=%s, sessionId=%s",
        current_user.username,
        user_id,
        payload.session_id,
    )

    try:
        result = await team.compact_session(session_id=key.session_id)
    except Exception as exc:
        logger.error("Compression failed:%s", exc, exc_info=True)
        raise InternalException("Compression failed", cause=exc) from exc

    return ApiResponse.success(data=result)


@router.get("/session/stats", response_model=ApiSuccessResponseSchema)
async def get_session_stats(
    request: Request,
    session_id: str = Query(..., alias="sessionId"),
) -> dict[str, Any]:
    """Get session statistics."""
    current_user = request.state.current_user
    key = _build_session_key(
        tenant_id=current_user.tenant_id,
        session_id=session_id,
        user_id=str(current_user.user_id),
    )
    team = _get_bound_team(request, str(key))
    if team is None:
        raise BadRequestException("The session does not exist or has expired")

    try:
        stats = await team.get_session_stats(session_id=key.session_id)
    except Exception as exc:
        logger.error("Failed to obtain statistics:%s", exc, exc_info=True)
        raise InternalException("Failed to obtain statistics", cause=exc) from exc

    return ApiResponse.success(data=stats)
