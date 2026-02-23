# @author Sunny
# @date 2026-02-22

"""请求作用域上下文（tenant/user）。"""

from __future__ import annotations

from contextvars import ContextVar, Token
from dataclasses import dataclass


@dataclass(frozen=True)
class RequestScope:
    """请求级身份上下文。"""

    tenant_id: int | None
    user_id: int | None
    tenant_code: str | None


_REQUEST_SCOPE: ContextVar[RequestScope | None] = ContextVar("request_scope", default=None)


def set_request_scope(
    tenant_id: int | None, user_id: int | None, tenant_code: str | None = None
) -> Token:
    """设置请求作用域上下文。"""
    return _REQUEST_SCOPE.set(
        RequestScope(tenant_id=tenant_id, user_id=user_id, tenant_code=tenant_code)
    )


def reset_request_scope(token: Token) -> None:
    """重置请求作用域上下文。"""
    _REQUEST_SCOPE.reset(token)


def get_request_scope() -> RequestScope | None:
    """获取当前请求作用域上下文。"""
    return _REQUEST_SCOPE.get()


def get_current_tenant_id() -> int | None:
    """获取当前租户 ID。"""
    scope = get_request_scope()
    return scope.tenant_id if scope else None


def get_current_user_id() -> int | None:
    """获取当前用户 ID。"""
    scope = get_request_scope()
    return scope.user_id if scope else None


def get_current_tenant_code() -> str | None:
    """获取当前租户编码。"""
    scope = get_request_scope()
    return scope.tenant_code if scope else None
