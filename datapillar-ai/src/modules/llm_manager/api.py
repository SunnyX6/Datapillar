# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""LLM 模型管理 API 路由。"""

from __future__ import annotations

import logging

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from src.modules.llm_manager.schemas import ModelConnectRequest, ModelCreateRequest, ModelType, ModelUpdateRequest
from src.modules.llm_manager.service import (
    ConflictError,
    ConnectError,
    InvalidParamError,
    LlmManagerService,
    NotFoundError,
)
from src.shared.web import ApiResponse

logger = logging.getLogger(__name__)

router = APIRouter()
_service: LlmManagerService | None = None


def _get_service() -> LlmManagerService:
    global _service
    if _service is None:
        _service = LlmManagerService()
    return _service


def _pagination(limit: int | None, offset: int | None) -> tuple[int, int]:
    safe_limit = min(max(limit or 20, 1), 200)
    safe_offset = max(offset or 0, 0)
    return safe_limit, safe_offset


def _error_response(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content=ApiResponse.error(request=request, status=status, code=code, message=message),
    )


@router.get("/providers")
async def list_providers(request: Request):
    data = _get_service().list_providers()
    return ApiResponse.success(request=request, data=data)


@router.get("/models")
async def list_models(
    request: Request,
    limit: int = 20,
    offset: int = 0,
    keyword: str | None = None,
    provider: str | None = None,
    model_type: ModelType | None = None,
):
    limit, offset = _pagination(limit, offset)
    tenant_id = request.state.current_user.tenant_id
    rows, total = _get_service().list_models(
        limit=limit,
        offset=offset,
        tenant_id=tenant_id,
        keyword=keyword,
        provider_code=provider,
        model_type=model_type.value if model_type else None,
    )
    return ApiResponse.success(request=request, data=rows, limit=limit, offset=offset, total=total)


@router.get("/models/{model_id}")
async def get_model(request: Request, model_id: int):
    tenant_id = request.state.current_user.tenant_id
    row = _get_service().get_model(model_id, tenant_id)
    if not row:
        return _error_response(request, 404, "RESOURCE_NOT_FOUND", "模型不存在")
    return ApiResponse.success(request=request, data=row)


@router.post("/models")
async def create_model(request: Request, payload: ModelCreateRequest):
    current_user = request.state.current_user
    try:
        model = _get_service().create_model(
            user_id=current_user.user_id,
            tenant_id=current_user.tenant_id,
            payload=payload,
        )
    except InvalidParamError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    except ConflictError as exc:
        return _error_response(request, 409, "DUPLICATE_RESOURCE", str(exc))
    except Exception as exc:
        logger.error("创建模型失败: %s", exc, exc_info=True)
        return _error_response(request, 500, "INTERNAL_ERROR", "创建模型失败")
    return ApiResponse.success(request=request, data=model)


@router.patch("/models/{model_id}")
async def update_model(request: Request, model_id: int, payload: ModelUpdateRequest):
    current_user = request.state.current_user
    try:
        model = _get_service().update_model(
            user_id=current_user.user_id,
            tenant_id=current_user.tenant_id,
            model_id=model_id,
            payload=payload,
        )
    except InvalidParamError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    except NotFoundError as exc:
        return _error_response(request, 404, "RESOURCE_NOT_FOUND", str(exc))
    except Exception as exc:
        logger.error("更新模型失败: %s", exc, exc_info=True)
        return _error_response(request, 500, "INTERNAL_ERROR", "更新模型失败")
    return ApiResponse.success(request=request, data=model)


@router.delete("/models/{model_id}")
async def delete_model(request: Request, model_id: int):
    try:
        tenant_id = request.state.current_user.tenant_id
        deleted = _get_service().delete_model(tenant_id=tenant_id, model_id=model_id)
    except NotFoundError as exc:
        return _error_response(request, 404, "RESOURCE_NOT_FOUND", str(exc))
    except Exception as exc:
        logger.error("删除模型失败: %s", exc, exc_info=True)
        return _error_response(request, 500, "INTERNAL_ERROR", "删除模型失败")
    return ApiResponse.success(request=request, data={"deleted": deleted})


@router.post("/models/{model_id}/connect")
async def connect_model(request: Request, model_id: int, payload: ModelConnectRequest):
    current_user = request.state.current_user
    try:
        result = await _get_service().connect_model(
            user_id=current_user.user_id,
            tenant_id=current_user.tenant_id,
            model_id=model_id,
            api_key=payload.api_key,
            base_url=payload.base_url,
        )
    except InvalidParamError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    except NotFoundError as exc:
        return _error_response(request, 404, "RESOURCE_NOT_FOUND", str(exc))
    except ConnectError as exc:
        return _error_response(request, 400, "INVALID_ARGUMENT", str(exc))
    except Exception as exc:
        logger.error("连接模型失败: %s", exc, exc_info=True)
        return _error_response(request, 500, "INTERNAL_ERROR", "连接模型失败")
    return ApiResponse.success(request=request, data=result, message="Connected")
