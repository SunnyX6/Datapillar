# @author Sunny
# @date 2026-02-22

"""request scope context(tenant/user)."""

from __future__ import annotations

from contextvars import ContextVar, Token
from dataclasses import dataclass


@dataclass(frozen=True)
class RequestScope:
    """Request-level identity context."""

    tenant_id: int | None
    user_id: int | None
    tenant_code: str | None


_REQUEST_SCOPE: ContextVar[RequestScope | None] = ContextVar("request_scope", default=None)


def set_request_scope(
    tenant_id: int | None, user_id: int | None, tenant_code: str | None = None
) -> Token:
    """Set request scope context."""
    return _REQUEST_SCOPE.set(
        RequestScope(tenant_id=tenant_id, user_id=user_id, tenant_code=tenant_code)
    )


def reset_request_scope(token: Token) -> None:
    """Reset request scope context."""
    _REQUEST_SCOPE.reset(token)


def get_request_scope() -> RequestScope | None:
    """Get the current request scope context."""
    return _REQUEST_SCOPE.get()


def get_current_tenant_id() -> int | None:
    """Get current tenant ID."""
    scope = get_request_scope()
    return scope.tenant_id if scope else None


def get_current_user_id() -> int | None:
    """Get current user ID."""
    scope = get_request_scope()
    return scope.user_id if scope else None


def get_current_tenant_code() -> str | None:
    """Get the current tenant code."""
    scope = get_request_scope()
    return scope.tenant_code if scope else None
