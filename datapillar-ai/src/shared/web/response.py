# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""
API 统一响应封装（对齐 datapillar-workbench ApiResponse）
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from fastapi import Request


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def build_success(
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
        "timestamp": _now_iso(),
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


def build_error(
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
        "timestamp": _now_iso(),
        "path": request.url.path,
        "traceId": request.headers.get("X-Trace-Id"),
    }
