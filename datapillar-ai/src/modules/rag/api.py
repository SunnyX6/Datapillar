# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki API."""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, File, Form, Request, UploadFile
from fastapi.responses import JSONResponse
from sse_starlette.sse import EventSourceResponse

from src.modules.rag.schemas import (
    ChunkEditRequest,
    ChunkJobRequest,
    DocumentUpdateRequest,
    NamespaceCreateRequest,
    NamespaceUpdateRequest,
    RetrieveRequest,
)
from src.modules.rag.service import KnowledgeWikiService
from src.modules.rag.sse import job_event_hub
from src.shared.web import build_error, build_success

logger = logging.getLogger(__name__)

router = APIRouter()
service = KnowledgeWikiService()


def _pagination(limit: int | None, offset: int | None) -> tuple[int, int]:
    safe_limit = min(max(limit or 20, 1), 200)
    safe_offset = max(offset or 0, 0)
    return safe_limit, safe_offset


def _error_response(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content=build_error(request=request, status=status, code=code, message=message),
    )


@router.get("/namespaces")
async def list_namespaces(request: Request, limit: int = 20, offset: int = 0):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    rows, total = service.list_namespaces(current_user.user_id, limit=limit, offset=offset)
    return build_success(
        request=request,
        data=rows,
        limit=limit,
        offset=offset,
        total=total,
    )


@router.post("/namespaces")
async def create_namespace(request: Request, payload: NamespaceCreateRequest):
    current_user = request.state.current_user
    namespace_id = service.create_namespace(current_user.user_id, payload.model_dump())
    return build_success(request=request, data={"namespace_id": namespace_id})


@router.patch("/namespaces/{namespace_id}")
async def update_namespace(request: Request, namespace_id: int, payload: NamespaceUpdateRequest):
    current_user = request.state.current_user
    fields = {k: v for k, v in payload.model_dump().items() if v is not None}
    if not fields:
        return _error_response(request, 400, "INVALID_PARAM", "没有可更新字段")
    updated = service.update_namespace(current_user.user_id, namespace_id, fields)
    if updated == 0:
        return _error_response(request, 404, "NOT_FOUND", "namespace 不存在")
    return build_success(request=request, data={"updated": updated})


@router.delete("/namespaces/{namespace_id}")
async def delete_namespace(request: Request, namespace_id: int):
    current_user = request.state.current_user
    deleted = service.delete_namespace(current_user.user_id, namespace_id)
    if deleted == 0:
        return _error_response(request, 404, "NOT_FOUND", "namespace 不存在")
    return build_success(request=request, data={"deleted": deleted})


@router.get("/namespaces/{namespace_id}/documents")
async def list_documents(
    request: Request,
    namespace_id: int,
    status: str | None = None,
    keyword: str | None = None,
    limit: int = 20,
    offset: int = 0,
):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    rows, total = service.list_documents(
        current_user.user_id,
        namespace_id,
        status=status,
        keyword=keyword,
        limit=limit,
        offset=offset,
    )
    return build_success(
        request=request,
        data=rows,
        limit=limit,
        offset=offset,
        total=total,
    )


@router.post("/namespaces/{namespace_id}/documents/upload")
async def upload_document(
    request: Request,
    namespace_id: int,
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
):
    current_user = request.state.current_user
    if not file:
        return _error_response(request, 400, "INVALID_PARAM", "file 不能为空")
    content = await file.read()
    if not content:
        return _error_response(request, 400, "INVALID_PARAM", "上传文件为空")
    try:
        result = await service.upload_document(
            user_id=current_user.user_id,
            namespace_id=namespace_id,
            filename=file.filename or "document",
            content=content,
            title=title,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data=result)


@router.get("/documents/{document_id}")
async def get_document(request: Request, document_id: int):
    current_user = request.state.current_user
    doc = service.get_document(current_user.user_id, document_id)
    if not doc:
        return _error_response(request, 404, "NOT_FOUND", "document 不存在")
    return build_success(request=request, data=doc)


@router.patch("/documents/{document_id}")
async def update_document(request: Request, document_id: int, payload: DocumentUpdateRequest):
    current_user = request.state.current_user
    fields = {k: v for k, v in payload.model_dump().items() if v is not None}
    if not fields:
        return _error_response(request, 400, "INVALID_PARAM", "没有可更新字段")
    updated = service.update_document(current_user.user_id, document_id, fields)
    if updated == 0:
        return _error_response(request, 404, "NOT_FOUND", "document 不存在")
    return build_success(request=request, data={"updated": updated})


@router.delete("/documents/{document_id}")
async def delete_document(request: Request, document_id: int):
    current_user = request.state.current_user
    deleted = await service.delete_document(user_id=current_user.user_id, document_id=document_id)
    if deleted == 0:
        return _error_response(request, 404, "NOT_FOUND", "document 不存在")
    return build_success(request=request, data={"deleted": deleted})


@router.post("/documents/{document_id}/chunk")
async def start_chunk_job(request: Request, document_id: int, payload: ChunkJobRequest):
    current_user = request.state.current_user
    try:
        result = await service.start_chunk_job(
            user_id=current_user.user_id,
            document_id=document_id,
            chunk_mode=payload.chunk_mode,
            chunk_config_json=payload.chunk_config_json,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data=result)


@router.get("/documents/{document_id}/jobs")
async def list_document_jobs(request: Request, document_id: int, limit: int = 20, offset: int = 0):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    rows, total = service.list_jobs(
        current_user.user_id,
        document_id,
        limit=limit,
        offset=offset,
    )
    return build_success(request=request, data=rows, limit=limit, offset=offset, total=total)


@router.get("/jobs/{job_id}")
async def get_job(request: Request, job_id: int):
    current_user = request.state.current_user
    job = service.get_job(current_user.user_id, job_id)
    if not job:
        return _error_response(request, 404, "NOT_FOUND", "job 不存在")
    return build_success(request=request, data=job)


@router.get("/jobs/{job_id}/sse")
async def job_sse(request: Request, job_id: int):
    current_user = request.state.current_user
    job = service.get_job(current_user.user_id, job_id)
    if not job:
        return _error_response(request, 404, "NOT_FOUND", "job 不存在")

    last_event_id = request.headers.get("Last-Event-ID")
    last_event_id_int: int | None = None
    if last_event_id:
        try:
            last_event_id_int = int(last_event_id)
        except ValueError:
            last_event_id_int = None
    initial_event = None
    if last_event_id_int is None or last_event_id_int < int(job.get("progress_seq") or 0):
        payload = {
            "job_id": job["job_id"],
            "status": job["status"],
            "progress": job["progress"],
            "total_chunks": job["total_chunks"],
            "processed_chunks": job["processed_chunks"],
            "progress_seq": job["progress_seq"],
        }
        initial_event = {
            "event": "progress",
            "id": str(job["progress_seq"]),
            "data": json.dumps(payload, ensure_ascii=False),
        }

    async def event_stream():
        if job["status"] in {"success", "error", "canceled"}:
            done_payload = {
                "job_id": job["job_id"],
                "status": job["status"],
                "progress": job["progress"],
                "processed_chunks": job["processed_chunks"],
                "total_chunks": job["total_chunks"],
            }
            yield {
                "event": "done",
                "id": str(job.get("progress_seq") or 0),
                "data": json.dumps(done_payload, ensure_ascii=False),
            }
            return
        async for event in job_event_hub.subscribe(job_id, initial_event=initial_event):
            yield event

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


@router.get("/documents/{document_id}/chunks")
async def list_chunks(
    request: Request,
    document_id: int,
    keyword: str | None = None,
    limit: int = 20,
    offset: int = 0,
):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    try:
        rows, total = await service.list_chunks(
            user_id=current_user.user_id,
            document_id=document_id,
            limit=limit,
            offset=offset,
            keyword=keyword,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data=rows, limit=limit, offset=offset, total=total)


@router.patch("/chunks/{chunk_id}")
async def edit_chunk(request: Request, chunk_id: str, payload: ChunkEditRequest):
    current_user = request.state.current_user
    try:
        result = await service.edit_chunk(
            user_id=current_user.user_id,
            chunk_id=chunk_id,
            content=payload.content,
        )
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data=result)


@router.delete("/chunks/{chunk_id}")
async def delete_chunk(request: Request, chunk_id: str):
    current_user = request.state.current_user
    try:
        deleted = await service.delete_chunk(user_id=current_user.user_id, chunk_id=chunk_id)
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data={"deleted": deleted})


@router.post("/retrieve")
async def retrieve(request: Request, payload: RetrieveRequest):
    current_user = request.state.current_user
    try:
        data = await service.retrieve(user_id=current_user.user_id, payload=payload.model_dump())
    except ValueError as exc:
        return _error_response(request, 400, "INVALID_PARAM", str(exc))
    return build_success(request=request, data=data)
