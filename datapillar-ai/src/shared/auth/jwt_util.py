"""
JWT Token 工具类
本地验证 JWT Token，无需调用认证中心
"""

import logging
from datetime import UTC, datetime

import jwt

logger = logging.getLogger(__name__)


class JwtTokenUtil:
    """JWT Token 验证工具"""

    def __init__(self, secret: str, issuer: str):
        """
        初始化 JWT 工具

        Args:
            secret: JWT 密钥，必须至少 32 个字符
            issuer: Token 签发者
        """
        if not secret or len(secret) < 32:
            raise ValueError("JWT secret 必须至少 32 个字符")

        self.secret = secret
        self.issuer = issuer

    def parse_token(self, token: str) -> dict:
        """
        解析 Token

        Args:
            token: JWT Token 字符串

        Returns:
            Token 的 payload (claims)

        Raises:
            jwt.InvalidTokenError: Token 无效或解析失败
        """
        try:
            payload = jwt.decode(
                token,
                self.secret,
                algorithms=["HS256"],
                options={"verify_signature": True, "verify_exp": True},
            )
            return payload
        except jwt.ExpiredSignatureError:
            logger.debug("Token已过期")
            raise
        except jwt.InvalidTokenError as e:
            logger.warning(f"Token解析失败: {e}")
            raise

    def validate_token(self, token: str) -> bool:
        """
        验证 Token (本地验证签名和过期时间)

        Args:
            token: JWT Token 字符串

        Returns:
            Token 是否有效
        """
        try:
            claims = self.parse_token(token)

            # 检查过期时间
            if self._is_expired(claims):
                logger.debug("Token已过期")
                return False

            # 检查签发者
            if claims.get("iss") != self.issuer:
                logger.warning(
                    f"Token签发者不匹配: expected={self.issuer}, actual={claims.get('iss')}"
                )
                return False

            return True
        except Exception as e:
            logger.debug(f"Token验证失败: {e}")
            return False

    def _is_expired(self, claims: dict) -> bool:
        """检查 Token 是否过期"""
        exp_timestamp = claims.get("exp")
        if not exp_timestamp:
            return True

        exp_time = datetime.fromtimestamp(exp_timestamp, tz=UTC)
        return exp_time < datetime.now(UTC)

    def get_user_id(self, token: str) -> int:
        """
        从 Token 提取 userId

        Args:
            token: JWT Token 字符串

        Returns:
            用户 ID
        """
        claims = self.parse_token(token)
        user_id = claims.get("userId")

        if isinstance(user_id, int):
            return user_id
        if isinstance(user_id, str):
            return int(user_id)

        # 从 sub 字段获取
        return int(claims.get("sub", 0))

    def get_username(self, token: str) -> str:
        """
        从 Token 提取 username

        Args:
            token: JWT Token 字符串

        Returns:
            用户名
        """
        claims = self.parse_token(token)
        return claims.get("username", "")

    def get_email(self, token: str) -> str | None:
        """
        从 Token 提取 email

        Args:
            token: JWT Token 字符串

        Returns:
            邮箱地址
        """
        claims = self.parse_token(token)
        return claims.get("email")

    def get_token_type(self, token: str) -> str:
        """
        获取 Token 类型

        Args:
            token: JWT Token 字符串

        Returns:
            Token 类型 (access/refresh)
        """
        claims = self.parse_token(token)
        return claims.get("tokenType", "")

    def extract_token_signature(self, token: str) -> str:
        """
        提取 Token 签名（用于SSO验证）
        JWT格式: header.payload.signature
        只提取 signature 部分用于数据库验证

        Args:
            token: JWT Token 字符串

        Returns:
            Token 签名部分
        """
        if not token:
            raise ValueError("Token不能为空")

        parts = token.split(".")
        if len(parts) != 3:
            raise ValueError("Invalid JWT format")

        return parts[2]
