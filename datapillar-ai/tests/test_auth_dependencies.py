from __future__ import annotations

from types import SimpleNamespace

import pytest
from starlette.requests import Request

from src.shared.auth.dependencies import current_user_state, require_admin_role
from src.shared.exception import ForbiddenException, UnauthorizedException


def _make_request(*, roles: list[str] | None, with_user: bool = True) -> Request:
    scope = {
        "type": "http",
        "method": "GET",
        "path": "/api/ai/admin/llms/chat",
        "headers": [],
        "query_string": b"",
        "scheme": "http",
        "server": ("testserver", 80),
        "client": ("testclient", 1234),
    }
    request = Request(scope)
    if with_user:
        request.state.current_user = SimpleNamespace(user_id=1, roles=roles or [])
    return request


def test_require_admin_role_accepts_admin_case_insensitive() -> None:
    request = _make_request(roles=["admin"])

    require_admin_role(request)


def test_require_admin_role_rejects_non_admin_role() -> None:
    request = _make_request(roles=["USER"])

    with pytest.raises(ForbiddenException) as exc_info:
        require_admin_role(request)

    assert str(exc_info.value) == "Requires administrator rights"


def test_require_admin_role_rejects_missing_user_context() -> None:
    scope = {
        "type": "http",
        "method": "GET",
        "path": "/api/ai/admin/llms/chat",
        "headers": [],
        "query_string": b"",
        "scheme": "http",
        "server": ("testserver", 80),
        "client": ("testclient", 1234),
    }
    request = Request(scope)

    with pytest.raises(UnauthorizedException) as exc_info:
        require_admin_role(request)

    assert str(exc_info.value) == "Authentication information lost"


def test_current_user_state_raises_unauthorized_when_missing() -> None:
    request = _make_request(roles=["ADMIN"], with_user=False)

    with pytest.raises(UnauthorizedException) as exc_info:
        current_user_state(request)

    assert str(exc_info.value) == "Authentication information lost"
