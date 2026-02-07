# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
全局认证中间件
"""

import logging
from collections.abc import Callable

from fastapi import Request, Response, status
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)

from src.shared.auth.jwt_util import JwtTokenUtil
from src.shared.auth.user import CurrentUser
from src.shared.config.settings import settings
from src.shared.web import ApiResponse


class AuthMiddleware(BaseHTTPMiddleware):
    """
    全局认证中间件
    自动拦截所有请求，验证 JWT Token
    """

    # 白名单：无需认证的路径
    WHITELIST_PATHS = {
        "/health",
        "/docs",
        "/redoc",
        "/openapi.json",
        "/api/v1/lineage",
    }

    def __init__(self, app):
        super().__init__(app)

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """拦截请求，验证认证"""
        path = request.url.path

        tenant_id = self._extract_tenant_id(request)
        if tenant_id is None:
            logger.warning(f"[Auth] 缺少租户信息: {path}")
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content=ApiResponse.error(
                    request=request,
                    status=status.HTTP_401_UNAUTHORIZED,
                    code="UNAUTHORIZED",
                    message="缺少租户信息",
                ),
                headers={"WWW-Authenticate": "Bearer"},
            )

        # 白名单放行
        if (
            path in self.WHITELIST_PATHS
            or path.startswith("/docs")
            or path.startswith("/redoc")
            or path.startswith("/api/v1/lineage")
        ):
            return await call_next(request)

        # 提取 Token
        token = self._extract_token(request)
        if not token:
            logger.warning(f"[Auth] 未提供认证凭证: {path}")
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content=ApiResponse.error(
                    request=request,
                    status=status.HTTP_401_UNAUTHORIZED,
                    code="UNAUTHORIZED",
                    message="未提供认证凭证",
                ),
                headers={"WWW-Authenticate": "Bearer"},
            )

        # 验证 Token
        try:
            jwt_util = JwtTokenUtil(
                secret=settings.jwt_secret,
                issuer=settings.jwt_issuer,
            )

            if not jwt_util.validate_token(token):
                logger.warning(f"[Auth] Token验证失败: {path}")
                return JSONResponse(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    content=ApiResponse.error(
                        request=request,
                        status=status.HTTP_401_UNAUTHORIZED,
                        code="TOKEN_INVALID",
                        message="Token验证失败",
                    ),
                    headers={"WWW-Authenticate": "Bearer"},
                )

            # 检查 Token 类型
            jwt_type = jwt_util.get_token_type(token)
            if jwt_type != "access":
                logger.warning(f"[Auth] Token类型错误: {jwt_type}, path={path}")
                return JSONResponse(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    content=ApiResponse.error(
                        request=request,
                        status=status.HTTP_401_UNAUTHORIZED,
                        code="TOKEN_TYPE_ERROR",
                        message="Token类型错误，请使用Access Token",
                    ),
                    headers={"WWW-Authenticate": "Bearer"},
                )

            # 提取用户信息并注入到 request.state
            user_id = jwt_util.get_user_id(token)
            username = jwt_util.get_username(token)
            email = jwt_util.get_email(token)

            current_user = CurrentUser(
                user_id=user_id,
                tenant_id=tenant_id,
                username=username,
                email=email,
            )

            # 注入到 request.state，后续可通过 request.state.current_user 获取
            request.state.current_user = current_user

            logger.debug(f"[Auth] 认证成功: user={username}, path={path}")

        except Exception as e:
            logger.error(f"[Auth] 认证异常: {e}, path={path}")
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content=ApiResponse.error(
                    request=request,
                    status=status.HTTP_401_UNAUTHORIZED,
                    code="UNAUTHORIZED",
                    message="认证失败",
                ),
                headers={"WWW-Authenticate": "Bearer"},
            )

        # 放行请求
        response = await call_next(request)
        return response

    def _extract_tenant_id(self, request: Request) -> int | None:
        """
        从请求头提取租户ID
        """
        tenant_id = request.headers.get("X-Tenant-Id") or request.headers.get("x-tenant-id")
        if not tenant_id:
            return None
        try:
            return int(tenant_id)
        except (TypeError, ValueError):
            return None

    def _extract_token(self, request: Request) -> str | None:
        """
        从请求中提取 Token
        优先从 Authorization Header 获取，其次从 Cookie 获取
        """
        # 优先从 Authorization Header 获取
        authorization = request.headers.get("authorization") or request.headers.get("Authorization")
        if authorization and authorization.startswith("Bearer "):
            return authorization[7:]

        # 从 Cookie 获取
        auth_token = request.cookies.get("auth-token")
        if auth_token:
            return auth_token

        return None
