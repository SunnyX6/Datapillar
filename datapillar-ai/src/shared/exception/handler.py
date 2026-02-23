# @author Sunny
# @date 2026-02-20

"""全局异常处理注册。"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from src.shared.exception.base import DatapillarException
from src.shared.exception.mapper import ExceptionMapper
from src.shared.web import ApiResponse

logger = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI) -> None:
    """注册全局异常处理器。"""

    @app.exception_handler(DatapillarException)
    async def _handle_datapillar_exception(
        request: Request, exc: DatapillarException
    ) -> JSONResponse:
        return _build_error_response(request=request, exc=exc)

    @app.exception_handler(RequestValidationError)
    async def _handle_validation_exception(
        request: Request,
        exc: RequestValidationError,
    ) -> JSONResponse:
        return _build_error_response(request=request, exc=exc)

    @app.exception_handler(StarletteHTTPException)
    async def _handle_http_exception(
        request: Request,
        exc: StarletteHTTPException,
    ) -> JSONResponse:
        return _build_error_response(request=request, exc=exc)

    @app.exception_handler(Exception)
    async def _handle_unknown_exception(request: Request, exc: Exception) -> JSONResponse:
        return _build_error_response(request=request, exc=exc)


def _build_error_response(*, request: Request, exc: Exception) -> JSONResponse:
    detail = ExceptionMapper.resolve(exc)
    trace_id = (
        request.headers.get("x-trace-id") or request.headers.get("trace-id") or detail.trace_id
    )

    if detail.server_error:
        logger.error(
            "请求失败: path=%s, type=%s, message=%s",
            request.url.path,
            detail.error_type,
            detail.message,
            exc_info=exc,
        )
    else:
        logger.warning(
            "请求异常: path=%s, type=%s, message=%s",
            request.url.path,
            detail.error_type,
            detail.message,
        )

    content = ApiResponse.error(
        code=detail.error_code,
        error_type=detail.error_type,
        message=detail.message,
        context=detail.context,
        trace_id=trace_id,
        retryable=detail.retryable,
    )
    return JSONResponse(status_code=detail.http_status, content=content)
