# @author Sunny
# @date 2026-01-27

"""全局认证中间件。"""

from __future__ import annotations

import gzip
import logging
from collections.abc import Callable
from typing import Any

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

from src.shared.auth.gateway_assertion import (
    GatewayAssertionConfig,
    GatewayAssertionError,
    GatewayAssertionVerifier,
)
from src.shared.auth.user import CurrentUser
from src.shared.config.runtime import get_runtime_config
from src.shared.context import reset_request_scope, set_request_scope
from src.shared.exception import ServiceUnavailableException, UnauthorizedException

logger = logging.getLogger(__name__)


class AuthMiddleware(BaseHTTPMiddleware):
    """统一请求入口中间件：Gzip 解压 + 网关断言认证。"""

    WHITELIST_PATHS = {
        "/health",
        "/docs",
        "/redoc",
        "/openapi.json",
        "/api/ai/openapi.json",
    }

    def __init__(self, app):
        super().__init__(app)
        self._gateway_assertion_verifier: GatewayAssertionVerifier | None = None

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        await self._maybe_decode_gzip_request(request)

        path = request.url.path
        if path in self.WHITELIST_PATHS or path.startswith("/docs") or path.startswith("/redoc"):
            return await call_next(request)

        verifier = self._get_gateway_assertion_verifier()
        assertion = request.headers.get(verifier.header_name)
        if not assertion:
            logger.warning("[Auth] 缺少网关断言: %s", path)
            raise UnauthorizedException("缺少网关断言")

        try:
            context = verifier.verify(assertion, request.method, path)
        except GatewayAssertionError as exc:
            logger.warning("[Auth] 网关断言校验失败: path=%s, reason=%s", path, exc)
            raise UnauthorizedException("网关断言无效", cause=exc) from exc

        request.state.current_user = CurrentUser(
            user_id=context.user_id,
            tenant_id=context.tenant_id,
            tenant_code=context.tenant_code,
            username=context.username,
            email=context.email,
        )
        request.state.gateway_assertion = context
        logger.debug("[Auth] 网关断言注入成功: user=%s, path=%s", context.user_id, path)
        token = set_request_scope(context.tenant_id, context.user_id, context.tenant_code)
        try:
            return await call_next(request)
        finally:
            reset_request_scope(token)

    async def _maybe_decode_gzip_request(self, request: Request) -> None:
        content_encoding = request.headers.get("content-encoding", "").lower()
        if content_encoding != "gzip":
            return

        compressed_body = await request.body()
        try:
            decompressed_body = gzip.decompress(compressed_body)
        except Exception as exc:
            logger.warning("Gzip 解压失败: %s", exc)
            decompressed_body = compressed_body

        request.scope["headers"] = [
            (key, value)
            for key, value in request.scope.get("headers", [])
            if key.lower() not in (b"content-encoding", b"content-length")
        ]
        request.scope["headers"].append((b"content-length", str(len(decompressed_body)).encode()))
        if hasattr(request, "_headers"):
            delattr(request, "_headers")
        request._body = decompressed_body  # type: ignore[attr-defined]
        if hasattr(request, "_json"):
            delattr(request, "_json")
        request._receive = self._build_replay_receive(decompressed_body)  # type: ignore[attr-defined]

    @staticmethod
    def _build_replay_receive(body: bytes) -> Callable[[], Any]:
        body_sent = False

        async def replay_receive() -> dict[str, Any]:
            nonlocal body_sent
            if not body_sent:
                body_sent = True
                return {"type": "http.request", "body": body, "more_body": False}
            return {"type": "http.disconnect"}

        return replay_receive

    def _get_gateway_assertion_verifier(self) -> GatewayAssertionVerifier:
        if self._gateway_assertion_verifier is not None:
            return self._gateway_assertion_verifier

        try:
            runtime_config = get_runtime_config()
            assertion = runtime_config.security.gateway_assertion
            if not assertion.enabled:
                raise ServiceUnavailableException("认证配置不可用")

            self._gateway_assertion_verifier = GatewayAssertionVerifier(
                GatewayAssertionConfig(
                    enabled=assertion.enabled,
                    header_name=assertion.header_name,
                    issuer=assertion.issuer,
                    audience=assertion.audience,
                    key_id=assertion.key_id,
                    public_key_path=assertion.public_key_path,
                    previous_key_id=assertion.previous_key_id,
                    previous_public_key_path=assertion.previous_public_key_path,
                    max_clock_skew_seconds=assertion.max_clock_skew_seconds,
                )
            )
            return self._gateway_assertion_verifier
        except ServiceUnavailableException:
            raise
        except Exception as exc:
            logger.error("[Auth] 网关断言配置加载失败: %s", exc)
            raise ServiceUnavailableException("认证配置不可用", cause=exc) from exc
