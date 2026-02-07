# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""
API 统一响应封装（对齐 datapillar-studio ApiResponse）
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from fastapi import Request


class ApiResponse:
    """统一 API 响应构造器（对齐 datapillar-studio ApiResponse）"""

    @staticmethod
    def _now_iso() -> str:
        return datetime.now(timezone.utc).isoformat()

    @classmethod
    def success(
        cls,
        *,
        request: Request,
        data: Any,
        message: str = "Success",
        limit: int | None = None,
        offset: int | None = None,
        total: int | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "status": 200,
            "code": "OK",
            "message": message,
            "data": data,
            "timestamp": cls._now_iso(),
            "path": request.url.path,
            "traceId": request.headers.get("X-Trace-Id"),
        }
        if limit is not None:
            payload["limit"] = limit
        if offset is not None:
            payload["offset"] = offset
        if total is not None:
            payload["total"] = total
        return payload

    @classmethod
    def error(
        cls,
        *,
        request: Request,
        status: int,
        code: str,
        message: str,
    ) -> dict[str, Any]:
        return {
            "status": status,
            "code": code,
            "message": message,
            "data": None,
            "timestamp": cls._now_iso(),
            "path": request.url.path,
            "traceId": request.headers.get("X-Trace-Id"),
        }


def build_success(
    *,
    request: Request,
    data: Any,
    message: str = "Success",
    limit: int | None = None,
    offset: int | None = None,
    total: int | None = None,
) -> dict[str, Any]:
    return ApiResponse.success(
        request=request,
        data=data,
        message=message,
        limit=limit,
        offset=offset,
        total=total,
    )


def build_error(
    *,
    request: Request,
    status: int,
    code: str,
    message: str,
) -> dict[str, Any]:
    return ApiResponse.error(request=request, status=status, code=code, message=message)
