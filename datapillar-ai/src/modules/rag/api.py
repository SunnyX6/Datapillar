# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki API。"""

from __future__ import annotations

import json
from typing import Annotated

from fastapi import APIRouter, File, Form, Request, UploadFile
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
from src.shared.exception import BadRequestException, NotFoundException
from src.shared.web import ApiResponse

router = APIRouter()
_service: KnowledgeWikiService | None = None


def _get_service() -> KnowledgeWikiService:
    global _service
    if _service is None:
        _service = KnowledgeWikiService()
    return _service


def _pagination(limit: int | None, offset: int | None) -> tuple[int, int]:
    safe_limit = min(max(limit or 20, 1), 200)
    safe_offset = max(offset or 0, 0)
    return safe_limit, safe_offset


@router.get("/namespaces")
async def list_namespaces(request: Request, limit: int = 20, offset: int = 0):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    rows, total = _get_service().list_namespaces(
        current_user.tenant_id,
        current_user.user_id,
        limit=limit,
        offset=offset,
    )
    return ApiResponse.success(data=rows, limit=limit, offset=offset, total=total)


@router.post("/namespaces")
async def create_namespace(request: Request, payload: NamespaceCreateRequest):
    current_user = request.state.current_user
    namespace_id = _get_service().create_namespace(
        current_user.tenant_id,
        current_user.user_id,
        payload.model_dump(),
    )
    return ApiResponse.success(data={"namespace_id": namespace_id})


@router.patch("/namespaces/{namespace_id}")
async def update_namespace(request: Request, namespace_id: int, payload: NamespaceUpdateRequest):
    current_user = request.state.current_user
    fields = {key: value for key, value in payload.model_dump().items() if value is not None}
    if not fields:
        raise BadRequestException("没有可更新字段")

    updated = _get_service().update_namespace(
        current_user.tenant_id,
        current_user.user_id,
        namespace_id,
        fields,
    )
    if updated == 0:
        raise NotFoundException("namespace 不存在")

    return ApiResponse.success(data={"updated": updated})


@router.delete("/namespaces/{namespace_id}")
async def delete_namespace(request: Request, namespace_id: int):
    current_user = request.state.current_user
    deleted = _get_service().delete_namespace(
        current_user.tenant_id,
        current_user.user_id,
        namespace_id,
    )
    if deleted == 0:
        raise NotFoundException("namespace 不存在")
    return ApiResponse.success(data={"deleted": deleted})


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
    rows, total = _get_service().list_documents(
        current_user.tenant_id,
        current_user.user_id,
        namespace_id,
        status=status,
        keyword=keyword,
        limit=limit,
        offset=offset,
    )
    return ApiResponse.success(data=rows, limit=limit, offset=offset, total=total)


@router.post("/namespaces/{namespace_id}/documents/upload")
async def upload_document(
    request: Request,
    namespace_id: int,
    file: Annotated[UploadFile, File(...)],
    title: Annotated[str | None, Form(default=None)] = None,
):
    current_user = request.state.current_user
    if not file:
        raise BadRequestException("file 不能为空")

    content = await file.read()
    if not content:
        raise BadRequestException("上传文件为空")

    result = await _get_service().upload_document(
        tenant_id=current_user.tenant_id,
        user_id=current_user.user_id,
        namespace_id=namespace_id,
        filename=file.filename or "document",
        content=content,
        title=title,
    )
    return ApiResponse.success(data=result)


@router.get("/documents/{document_id}")
async def get_document(request: Request, document_id: int):
    current_user = request.state.current_user
    doc = _get_service().get_document(current_user.tenant_id, current_user.user_id, document_id)
    if not doc:
        raise NotFoundException("document 不存在")
    return ApiResponse.success(data=doc)


@router.patch("/documents/{document_id}")
async def update_document(request: Request, document_id: int, payload: DocumentUpdateRequest):
    current_user = request.state.current_user
    fields = {key: value for key, value in payload.model_dump().items() if value is not None}
    if not fields:
        raise BadRequestException("没有可更新字段")

    updated = _get_service().update_document(
        current_user.tenant_id,
        current_user.user_id,
        document_id,
        fields,
    )
    if updated == 0:
        raise NotFoundException("document 不存在")

    return ApiResponse.success(data={"updated": updated})


@router.delete("/documents/{document_id}")
async def delete_document(request: Request, document_id: int):
    current_user = request.state.current_user
    deleted = await _get_service().delete_document(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        document_id=document_id,
    )
    return ApiResponse.success(data={"deleted": deleted})


@router.post("/documents/{document_id}/chunk")
async def start_chunk_job(request: Request, document_id: int, payload: ChunkJobRequest):
    current_user = request.state.current_user
    result = await _get_service().start_chunk_job(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        document_id=document_id,
        chunk_mode=payload.chunk_mode,
        chunk_config_json=payload.chunk_config_json,
    )
    return ApiResponse.success(data=result)


@router.get("/documents/{document_id}/jobs")
async def list_document_jobs(request: Request, document_id: int, limit: int = 20, offset: int = 0):
    current_user = request.state.current_user
    limit, offset = _pagination(limit, offset)
    rows, total = _get_service().list_jobs(
        current_user.tenant_id,
        current_user.user_id,
        document_id,
        limit=limit,
        offset=offset,
    )
    return ApiResponse.success(data=rows, limit=limit, offset=offset, total=total)


@router.get("/jobs/{job_id}")
async def get_job(request: Request, job_id: int):
    current_user = request.state.current_user
    job = _get_service().get_job(current_user.tenant_id, current_user.user_id, job_id)
    if not job:
        raise NotFoundException("job 不存在")
    return ApiResponse.success(data=job)


@router.get("/jobs/{job_id}/sse")
async def job_sse(request: Request, job_id: int):
    current_user = request.state.current_user
    job = _get_service().get_job(current_user.tenant_id, current_user.user_id, job_id)
    if not job:
        raise NotFoundException("job 不存在")

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
    rows, total = await _get_service().list_chunks(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        document_id=document_id,
        limit=limit,
        offset=offset,
        keyword=keyword,
    )
    return ApiResponse.success(data=rows, limit=limit, offset=offset, total=total)


@router.patch("/chunks/{chunk_id}")
async def edit_chunk(request: Request, chunk_id: str, payload: ChunkEditRequest):
    current_user = request.state.current_user
    result = await _get_service().edit_chunk(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        chunk_id=chunk_id,
        content=payload.content,
    )
    return ApiResponse.success(data=result)


@router.delete("/chunks/{chunk_id}")
async def delete_chunk(request: Request, chunk_id: str):
    current_user = request.state.current_user
    deleted = await _get_service().delete_chunk(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        chunk_id=chunk_id,
    )
    return ApiResponse.success(data={"deleted": deleted})


@router.post("/retrieve")
async def retrieve(request: Request, payload: RetrieveRequest):
    current_user = request.state.current_user
    data = await _get_service().retrieve(
        user_id=current_user.user_id,
        tenant_id=current_user.tenant_id,
        payload=payload.model_dump(),
    )
    return ApiResponse.success(data=data)
