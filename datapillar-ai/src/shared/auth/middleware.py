# @author Sunny
# @date 2026-01-27

"""Global authentication middleware."""

from __future__ import annotations

import gzip
import logging
from collections.abc import Callable
from typing import Any

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

from src.shared.auth.user import CurrentUser
from src.shared.config.runtime import get_runtime_config
from src.shared.context import reset_request_scope, set_request_scope
from src.shared.exception import ServiceUnavailableException, UnauthorizedException

logger = logging.getLogger(__name__)

HEADER_ISSUER = "X-Principal-Iss"
HEADER_SUBJECT = "X-Principal-Sub"
HEADER_TENANT_ID = "X-Tenant-Id"
HEADER_TENANT_CODE = "X-Tenant-Code"
HEADER_USER_ID = "X-User-Id"
HEADER_USERNAME = "X-Username"
HEADER_EMAIL = "X-User-Email"
HEADER_ROLES = "X-User-Roles"
HEADER_TRACE_ID = "X-Trace-Id"


class AuthMiddleware(BaseHTTPMiddleware):
    """Unified request entry middleware:
    Gzip decode + trusted identity authentication."""

    WHITELIST_PATHS = {
        "/health",
        "/docs",
        "/redoc",
        "/openapi.json",
        "/api/ai/openapi.json",
    }

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        await self._maybe_decode_gzip_request(request)

        path = request.url.path
        if path in self.WHITELIST_PATHS or path.startswith("/docs") or path.startswith("/redoc"):
            return await call_next(request)

        if not self._is_trusted_identity_enabled():
            raise ServiceUnavailableException("Authentication configuration is not available")

        issuer = self._normalize(request.headers.get(HEADER_ISSUER))
        subject = self._normalize(request.headers.get(HEADER_SUBJECT))
        tenant_code = self._normalize(request.headers.get(HEADER_TENANT_CODE))
        username = self._normalize(request.headers.get(HEADER_USERNAME)) or subject
        email = self._normalize(request.headers.get(HEADER_EMAIL))
        trace_id = self._normalize(request.headers.get(HEADER_TRACE_ID))
        user_id = self._parse_positive_int(request.headers.get(HEADER_USER_ID))
        tenant_id = self._parse_positive_int(request.headers.get(HEADER_TENANT_ID))
        roles = self._parse_roles(request.headers.get(HEADER_ROLES))

        if issuer is None or subject is None:
            logger.warning("[Auth] Missing trusted principal headers:path=%s", path)
            raise UnauthorizedException("Missing trusted principal headers")
        if tenant_code is None:
            logger.warning("[Auth] Missing tenant code header:path=%s", path)
            raise UnauthorizedException("Missing tenant code header")
        if user_id is None or tenant_id is None:
            logger.warning("[Auth] Missing user/tenant id headers:path=%s", path)
            raise UnauthorizedException("Missing trusted user context headers")

        current_user = CurrentUser(
            user_id=user_id,
            tenant_id=tenant_id,
            tenant_code=tenant_code,
            username=username or subject,
            issuer=issuer,
            subject=subject,
            email=email,
            roles=tuple(roles),
        )
        request.state.current_user = current_user
        logger.info(
            "[Auth] trusted_identity_resolved iss=%s sub=%s preferred_username=%s tenant_code=%s trace_id=%s",
            issuer,
            subject,
            current_user.username,
            tenant_code,
            trace_id or "",
        )

        token = set_request_scope(tenant_id, user_id, tenant_code)
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
            logger.warning("Gzip decompression failed:%s", exc)
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

    def _is_trusted_identity_enabled(self) -> bool:
        try:
            return bool(get_runtime_config().security.trusted_identity.enabled)
        except Exception as exc:
            logger.error("[Auth] Trusted identity configuration failed to load:%s", exc)
            raise ServiceUnavailableException(
                "Authentication configuration is not available", cause=exc
            ) from exc

    def _normalize(self, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None

    def _parse_positive_int(self, value: str | None) -> int | None:
        normalized = self._normalize(value)
        if normalized is None:
            return None
        try:
            parsed = int(normalized)
        except ValueError:
            return None
        return parsed if parsed > 0 else None

    def _parse_roles(self, value: str | None) -> list[str]:
        normalized = self._normalize(value)
        if normalized is None:
            return []
        roles = []
        for token in normalized.split(","):
            role = self._normalize(token)
            if role is not None:
                roles.append(role.upper())
        return roles
