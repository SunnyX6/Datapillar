"""
FastAPI 认证依赖
"""

from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, Request, status
import logging

logger = logging.getLogger(__name__)

from src.auth.jwt_util import JwtTokenUtil
from src.auth.user import CurrentUser
from src.core.config import settings


def get_jwt_util() -> JwtTokenUtil:
    """获取 JWT 工具实例"""
    return JwtTokenUtil(
        secret=settings.jwt_secret,
        issuer=settings.jwt_issuer,
    )


def extract_token(request: Request) -> str:
    """
    从请求中提取 Token
    优先从 Authorization Header 获取，其次从 Cookie 获取

    Args:
        request: FastAPI Request 对象

    Returns:
        JWT Token 字符串

    Raises:
        HTTPException: 未找到 Token
    """
    # 优先从 Authorization Header 获取
    authorization = request.headers.get("authorization") or request.headers.get("Authorization")
    if authorization and authorization.startswith("Bearer "):
        return authorization[7:]

    # 从 Cookie 获取
    auth_token = request.cookies.get("auth-token")
    if auth_token:
        return auth_token

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="未提供认证凭证",
        headers={"WWW-Authenticate": "Bearer"},
    )


def get_current_user_from_state(request: Request) -> CurrentUser:
    """
    从 request.state 获取当前用户
    由全局认证中间件注入

    Args:
        request: FastAPI Request 对象

    Returns:
        当前用户信息

    Raises:
        HTTPException: 未找到用户信息（中间件未执行）
    """
    current_user = getattr(request.state, "current_user", None)
    if current_user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="认证信息丢失",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return current_user


# 【已废弃】旧的认证依赖，保留用于向后兼容
# 推荐使用 get_current_user_from_state
def get_current_user(
    token: Annotated[str, Depends(extract_token)],
    jwt_util: Annotated[JwtTokenUtil, Depends(get_jwt_util)],
) -> CurrentUser:
    """
    获取当前登录用户
    本地验证 JWT Token，无需调用认证中心

    Args:
        token: JWT Token 字符串
        jwt_util: JWT 工具实例

    Returns:
        当前用户信息

    Raises:
        HTTPException: Token 无效或过期
    """
    try:
        # 验证 Token
        if not jwt_util.validate_token(token):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token验证失败",
                headers={"WWW-Authenticate": "Bearer"},
            )

        # 检查 Token 类型
        token_type = jwt_util.get_token_type(token)
        if token_type != "access":
            logger.warning(f"Invalid token type: {token_type}, expected: access")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token类型错误，请使用Access Token",
                headers={"WWW-Authenticate": "Bearer"},
            )

        # 提取用户信息
        user_id = jwt_util.get_user_id(token)
        username = jwt_util.get_username(token)
        email = jwt_util.get_email(token)

        logger.debug(f"Token validation successful for user: {username} (userId: {user_id})")

        return CurrentUser(
            user_id=user_id,
            username=username,
            email=email,
        )

    except jwt.ExpiredSignatureError:
        logger.debug("Token已过期")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token已过期",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError as e:
        logger.warning(f"Token无效: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token无效",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as e:
        logger.error(f"认证过程发生异常: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="认证失败",
            headers={"WWW-Authenticate": "Bearer"},
        )
