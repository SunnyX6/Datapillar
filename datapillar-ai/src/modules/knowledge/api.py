# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识图谱 API 路由（使用 Repository 模式）
"""

import asyncio
import json
import logging
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from sse_starlette.sse import EventSourceResponse

logger = logging.getLogger(__name__)

from src.modules.knowledge.schemas import GraphSearchRequest
from src.modules.knowledge.service import KnowledgeGraphService
from src.modules.knowledge.service import GravitinoSyncService, SyncProgressReporter, SyncScope
from src.shared.config import settings
from src.shared.web import ApiResponse

router = APIRouter()
_sync_lock = asyncio.Lock()


def _error_response(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content=ApiResponse.error(request=request, status=status, code=code, message=message),
    )


def _get_service() -> KnowledgeGraphService:
    """获取知识图谱服务（不再需要传递 client）"""
    return KnowledgeGraphService()


def _normalize_scope_value(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _build_sync_scope(
    *,
    metalake: str | None,
    catalog: str | None,
    schema: str | None,
    table: str | None,
) -> SyncScope:
    metalake_value = _normalize_scope_value(metalake) or settings.gravitino_sync_metalake
    catalog_value = _normalize_scope_value(catalog)
    schema_value = _normalize_scope_value(schema)
    table_value = _normalize_scope_value(table)

    if schema_value and not catalog_value:
        raise ValueError("schema 必须依赖 catalog")
    if table_value and (not catalog_value or not schema_value):
        raise ValueError("table 必须依赖 catalog + schema")
    if not metalake_value:
        raise ValueError("metalake 不能为空")

    return SyncScope(
        metalake=metalake_value,
        catalog=catalog_value,
        schema=schema_value,
        table=table_value,
    )


async def _stream_sync(
    request: Request,
    *,
    mode: str,
    scope: SyncScope,
):
    try:
        await asyncio.wait_for(_sync_lock.acquire(), timeout=0)
    except asyncio.TimeoutError:
        return _error_response(request, 409, "SYNC_RUNNING", "已有同步任务进行中")
    queue: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue()

    async def emitter(payload: dict[str, Any]) -> None:
        await queue.put(payload)

    current_user = request.state.current_user
    reporter = SyncProgressReporter(scope=scope, emitter=emitter)
    service = GravitinoSyncService(
        scope=scope,
        reporter=reporter,
        tenant_id=current_user.tenant_id,
    )
    event_processor = getattr(request.app.state, "event_processor", None)

    async def run_sync() -> None:
        was_paused = False
        try:
            if event_processor is not None:
                was_paused = event_processor.is_paused
                if not was_paused:
                    event_processor.pause()

            if mode == "physical":
                await service.sync_physical()
            else:
                await service.sync_semantic()

        except Exception as exc:
            logger.error(
                "gravitino_sync_failed",
                extra={
                    "data": {
                        "mode": mode,
                        "scope": scope.node_payload(),
                        "error": str(exc),
                    }
                },
                exc_info=True,
            )
        finally:
            if event_processor is not None and not was_paused:
                event_processor.resume()
            await queue.put(None)
            _sync_lock.release()

    try:
        asyncio.create_task(run_sync())
    except Exception as exc:
        _sync_lock.release()
        return _error_response(request, 500, "SYNC_START_FAILED", str(exc))

    async def event_stream():
        while True:
            payload = await queue.get()
            if payload is None:
                break
            yield {
                "event": "progress",
                "data": json.dumps(payload, ensure_ascii=False),
            }

    return EventSourceResponse(
        event_stream(),
        ping=15,
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/initial")
async def get_initial_graph(
    request: Request,
    limit: int = 500,
):
    """获取初始图数据（非 SSE，一次性 JSON 返回）"""
    service = _get_service()
    safe_limit = min(max(limit, 1), 2000)

    graph = service.get_initial_graph(limit=safe_limit)
    return ApiResponse.success(request=request, data=graph.model_dump())


@router.post("/search")
async def search_graph(
    request: Request,
    payload: GraphSearchRequest,
):
    """搜索知识图谱（非 SSE，一次性 JSON 返回）"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    service = _get_service()
    logger.info(f"[KG] 搜索: user={current_user.username}, query={payload.query}")
    result: dict[str, Any] = service.search_by_text(
        query=payload.query,
        top_k=payload.top_k,
        search_type="hybrid",
        tenant_id=current_user.tenant_id,
    )
    return ApiResponse.success(request=request, data=result)


@router.get("/sync/physical/sse")
async def sync_physical_sse(
    request: Request,
    metalake: str | None = None,
    catalog: str | None = None,
    schema: str | None = None,
    table: str | None = None,
):
    try:
        scope = _build_sync_scope(
            metalake=metalake,
            catalog=catalog,
            schema=schema,
            table=table,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    return await _stream_sync(request, mode="physical", scope=scope)


@router.get("/sync/semantic/sse")
async def sync_semantic_sse(
    request: Request,
    metalake: str | None = None,
    catalog: str | None = None,
    schema: str | None = None,
    table: str | None = None,
):
    try:
        scope = _build_sync_scope(
            metalake=metalake,
            catalog=catalog,
            schema=schema,
            table=table,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    return await _stream_sync(request, mode="semantic", scope=scope)
