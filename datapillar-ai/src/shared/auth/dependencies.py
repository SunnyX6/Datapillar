# @author Sunny
# @date 2026-01-27

"""FastAPI 认证依赖。"""

from fastapi import Request

from src.shared.auth.user import CurrentUser
from src.shared.exception import ForbiddenException, UnauthorizedException

_ADMIN_ROLE = "ADMIN"


def current_user_state(request: Request) -> CurrentUser:
    """从 request.state 获取当前用户。"""
    current_user = getattr(request.state, "current_user", None)
    if current_user is None:
        raise UnauthorizedException("认证信息丢失")
    return current_user


def require_admin_role(request: Request) -> None:
    """校验当前请求是否具备管理员角色。"""
    assertion = getattr(request.state, "gateway_assertion", None)
    roles = getattr(assertion, "roles", []) if assertion is not None else []
    normalized_roles = {
        role.strip().upper() for role in roles if isinstance(role, str) and role.strip()
    }
    if _ADMIN_ROLE not in normalized_roles:
        raise ForbiddenException("需要管理员权限")
