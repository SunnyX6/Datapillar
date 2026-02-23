# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""API 统一响应封装（统一 code/type/context 标准）。"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from src.shared.web.code import Code


class ApiResponse:
    """统一 API 响应构造器。"""

    SUCCESS_CODE = Code.OK

    @classmethod
    def success(
        cls,
        *,
        data: Any | None = None,
        limit: int | None = None,
        offset: int | None = None,
        total: int | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"code": cls.SUCCESS_CODE}
        if data is not None:
            payload["data"] = data
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
        code: int,
        error_type: str,
        message: str,
        context: dict[str, str] | None = None,
        trace_id: str | None = None,
        retryable: bool | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "code": code,
            "type": error_type,
            "message": message,
        }
        if context:
            payload["context"] = context
        if trace_id:
            payload["traceId"] = trace_id
        if retryable is not None:
            payload["retryable"] = retryable
        return payload


class ApiSuccessResponseSchema(BaseModel):
    """OpenAPI 文档用成功响应结构。"""

    code: int = Field(default=ApiResponse.SUCCESS_CODE, description="业务状态码，0 表示成功")
    data: Any | None = Field(default=None, description="业务数据")


def build_success(
    *,
    data: Any | None = None,
    limit: int | None = None,
    offset: int | None = None,
    total: int | None = None,
) -> dict[str, Any]:
    return ApiResponse.success(
        data=data,
        limit=limit,
        offset=offset,
        total=total,
    )


def build_error(
    *,
    code: int,
    error_type: str,
    message: str,
    context: dict[str, str] | None = None,
    trace_id: str | None = None,
    retryable: bool | None = None,
) -> dict[str, Any]:
    return ApiResponse.error(
        code=code,
        error_type=error_type,
        message=message,
        context=context,
        trace_id=trace_id,
        retryable=retryable,
    )
