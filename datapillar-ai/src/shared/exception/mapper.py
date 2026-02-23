# @author Sunny
# @date 2026-02-20

"""异常映射器。"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from fastapi import HTTPException
from fastapi.exceptions import RequestValidationError

from src.shared.config.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ConfigurationError,
    DatabaseError,
)
from src.shared.exception.already_exists import AlreadyExistsException
from src.shared.exception.bad_request import BadRequestException
from src.shared.exception.base import DatapillarException
from src.shared.exception.conflict import ConflictException
from src.shared.exception.connection_failed import ConnectionFailedException
from src.shared.exception.forbidden import ForbiddenException
from src.shared.exception.internal import InternalException
from src.shared.exception.not_found import NotFoundException
from src.shared.exception.service_unavailable import ServiceUnavailableException
from src.shared.exception.too_many_requests import TooManyRequestsException
from src.shared.exception.unauthorized import UnauthorizedException
from src.shared.exception.unsupported_operation import UnsupportedOperationException
from src.shared.web.code import Code

_DEFAULT_INTERNAL_MESSAGE = "服务器内部错误"
_DUPLICATE_KEY_HINTS = (
    "duplicate entry",
    "duplicate key",
    "already exists",
    "unique constraint",
)

_HTTP_TYPE_MAPPING: dict[int, str] = {
    Code.BAD_REQUEST: "BAD_REQUEST",
    Code.UNAUTHORIZED: "UNAUTHORIZED",
    Code.FORBIDDEN: "FORBIDDEN",
    Code.NOT_FOUND: "NOT_FOUND",
    Code.METHOD_NOT_ALLOWED: "METHOD_NOT_ALLOWED",
    Code.CONFLICT: "CONFLICT",
    Code.TOO_MANY_REQUESTS: "TOO_MANY_REQUESTS",
    Code.BAD_GATEWAY: "BAD_GATEWAY",
    Code.SERVICE_UNAVAILABLE: "SERVICE_UNAVAILABLE",
}


@dataclass(frozen=True)
class ExceptionDetail:
    http_status: int
    error_code: int
    error_type: str
    message: str
    server_error: bool
    context: dict[str, str]
    trace_id: str | None
    retryable: bool


class ExceptionMapper:
    """统一异常语义映射。"""

    @classmethod
    def resolve(cls, exc: Exception | None) -> ExceptionDetail:
        target = _unwrap_exception_group(exc)
        if target is None:
            target = InternalException(_DEFAULT_INTERNAL_MESSAGE)

        detail = cls._resolve_datapillar_exception(target)
        if detail is None:
            detail = cls._resolve_framework_exception(target)
        if detail is None:
            detail = cls._resolve_system_exception(target)
        if detail is None:
            detail = _build_detail(
                http_status=Code.INTERNAL_ERROR,
                error_code=Code.INTERNAL_ERROR,
                error_type="INTERNAL_ERROR",
                message=_resolve_message(target),
                server_error=True,
                context={},
                retryable=False,
            )
        return detail

    @staticmethod
    def _resolve_datapillar_exception(exc: Exception) -> ExceptionDetail | None:
        if not isinstance(exc, DatapillarException):
            return None
        return _build_detail(
            http_status=exc.code,
            error_code=exc.code,
            error_type=exc.error_type,
            message=_resolve_message(exc),
            server_error=_is_server_error(code=exc.code, error_type=exc.error_type),
            context=exc.context,
            retryable=exc.retryable,
        )

    @classmethod
    def _resolve_framework_exception(cls, exc: Exception) -> ExceptionDetail | None:
        if isinstance(exc, RequestValidationError):
            return _build_detail(
                http_status=Code.BAD_REQUEST,
                error_code=Code.BAD_REQUEST,
                error_type="BAD_REQUEST",
                message="请求参数校验失败",
                server_error=False,
                context={},
                retryable=False,
            )

        if isinstance(exc, HTTPException):
            return cls._resolve_http_exception(exc)

        if isinstance(exc, AuthenticationError):
            return _build_detail(
                http_status=Code.UNAUTHORIZED,
                error_code=Code.UNAUTHORIZED,
                error_type="UNAUTHORIZED",
                message=_resolve_message(exc),
                server_error=False,
                context={},
                retryable=False,
            )

        if isinstance(exc, AuthorizationError):
            return _build_detail(
                http_status=Code.FORBIDDEN,
                error_code=Code.FORBIDDEN,
                error_type="FORBIDDEN",
                message=_resolve_message(exc),
                server_error=False,
                context={},
                retryable=False,
            )

        return None

    @staticmethod
    def _resolve_system_exception(exc: Exception) -> ExceptionDetail | None:
        if _is_duplicate_key_error(exc):
            return _build_detail(
                http_status=Code.CONFLICT,
                error_code=Code.CONFLICT,
                error_type="ALREADY_EXISTS",
                message="数据已存在",
                server_error=False,
                context={},
                retryable=False,
            )

        if isinstance(exc, (asyncio.TimeoutError, TimeoutError)):
            return _build_detail(
                http_status=Code.SERVICE_UNAVAILABLE,
                error_code=Code.SERVICE_UNAVAILABLE,
                error_type="SERVICE_UNAVAILABLE",
                message="服务调用超时",
                server_error=True,
                context={},
                retryable=True,
            )

        if isinstance(exc, (ConnectionError, OSError)):
            return _build_detail(
                http_status=Code.BAD_GATEWAY,
                error_code=Code.BAD_GATEWAY,
                error_type="BAD_GATEWAY",
                message=_resolve_message(exc),
                server_error=True,
                context={},
                retryable=True,
            )

        if isinstance(exc, (ConfigurationError, DatabaseError)):
            return _build_detail(
                http_status=Code.SERVICE_UNAVAILABLE,
                error_code=Code.SERVICE_UNAVAILABLE,
                error_type="SERVICE_UNAVAILABLE",
                message=_resolve_message(exc),
                server_error=True,
                context={},
                retryable=True,
            )

        if isinstance(exc, ValueError):
            return _build_detail(
                http_status=Code.BAD_REQUEST,
                error_code=Code.BAD_REQUEST,
                error_type="BAD_REQUEST",
                message=_resolve_message(exc),
                server_error=False,
                context={},
                retryable=False,
            )

        return None

    @staticmethod
    def _resolve_http_exception(exc: HTTPException) -> ExceptionDetail:
        status_code = int(exc.status_code)
        if status_code == 422:
            status_code = Code.BAD_REQUEST
        if status_code not in _HTTP_TYPE_MAPPING:
            status_code = Code.INTERNAL_ERROR if status_code >= 500 else Code.BAD_REQUEST
        error_type = _HTTP_TYPE_MAPPING.get(status_code, "INTERNAL_ERROR")
        return _build_detail(
            http_status=status_code,
            error_code=status_code,
            error_type=error_type,
            message=_resolve_http_detail(exc.detail),
            server_error=_is_server_error(code=status_code, error_type=error_type),
            context={},
            retryable=status_code in {Code.BAD_GATEWAY, Code.SERVICE_UNAVAILABLE},
        )


def _is_duplicate_key_error(exc: Exception) -> bool:
    message = _resolve_message(exc).lower()
    return any(hint in message for hint in _DUPLICATE_KEY_HINTS)


def _resolve_message(exc: Exception) -> str:
    text = str(exc).strip()
    if text:
        return text
    return _DEFAULT_INTERNAL_MESSAGE


def _resolve_http_detail(detail: Any) -> str:
    if isinstance(detail, str):
        text = detail.strip()
        return text if text else "请求失败"
    if isinstance(detail, dict):
        message = detail.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
    return "请求失败"


def _unwrap_exception_group(exc: Exception | None) -> Exception | None:
    if exc is None:
        return None
    if not isinstance(exc, BaseExceptionGroup):
        return exc
    if not exc.exceptions:
        return None
    first = exc.exceptions[0]
    if isinstance(first, Exception):
        return first
    return InternalException(str(first))


def _is_server_error(*, code: int, error_type: str) -> bool:
    if error_type == "REQUIRED":
        return False
    return code >= Code.INTERNAL_ERROR


def _build_detail(
    *,
    http_status: int,
    error_code: int,
    error_type: str,
    message: str,
    server_error: bool,
    context: dict[str, str],
    retryable: bool,
) -> ExceptionDetail:
    return ExceptionDetail(
        http_status=http_status,
        error_code=error_code,
        error_type=error_type,
        message=message,
        server_error=server_error,
        context=dict(context),
        trace_id=None,
        retryable=retryable,
    )
