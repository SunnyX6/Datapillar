# @author Sunny
# @date 2026-01-27

"""FastAPI Authentication dependency."""

from fastapi import Request

from src.shared.auth.user import CurrentUser
from src.shared.exception import ForbiddenException, UnauthorizedException

_ADMIN_ROLE = "ADMIN"


def current_user_state(request: Request) -> CurrentUser:
    """Get current user from request state."""
    current_user = getattr(request.state, "current_user", None)
    if current_user is None:
        raise UnauthorizedException("Authentication information lost")
    return current_user


def require_admin_role(request: Request) -> None:
    """Verify whether the current request has the administrator role."""
    current_user = current_user_state(request)
    normalized_roles = {
        role.strip().upper()
        for role in current_user.roles
        if isinstance(role, str) and role.strip()
    }
    if _ADMIN_ROLE not in normalized_roles:
        raise ForbiddenException("Requires administrator rights")
