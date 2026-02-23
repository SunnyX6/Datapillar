# @author Sunny
# @date 2026-02-19

"""
网关断言校验器
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import jwt
from jwt import InvalidTokenError

CLAIM_TENANT_ID = "tenantId"
CLAIM_TENANT_CODE = "tenantCode"
CLAIM_USERNAME = "username"
CLAIM_EMAIL = "email"
CLAIM_ROLES = "roles"
CLAIM_IMPERSONATION = "impersonation"
CLAIM_ACTOR_USER_ID = "actorUserId"
CLAIM_ACTOR_TENANT_ID = "actorTenantId"
CLAIM_METHOD = "method"
CLAIM_PATH = "path"


class GatewayAssertionError(RuntimeError):
    """网关断言校验异常"""


@dataclass(frozen=True)
class GatewayAssertionConfig:
    enabled: bool
    header_name: str
    issuer: str
    audience: str
    key_id: str
    public_key_path: str
    previous_key_id: str | None
    previous_public_key_path: str | None
    max_clock_skew_seconds: int


@dataclass(frozen=True)
class GatewayAssertionContext:
    user_id: int
    tenant_id: int
    tenant_code: str
    username: str
    email: str | None
    roles: list[str]
    impersonation: bool
    actor_user_id: int | None
    actor_tenant_id: int | None
    token_id: str


class GatewayAssertionVerifier:
    """网关断言验签器"""

    def __init__(self, config: GatewayAssertionConfig):
        self._config = config
        self._public_key = self._load_public_key(config.public_key_path)
        if config.previous_public_key_path:
            self._previous_public_key = self._load_public_key(config.previous_public_key_path)
        else:
            self._previous_public_key = None

    @property
    def header_name(self) -> str:
        return self._config.header_name

    def verify(self, token: str, request_method: str, request_path: str) -> GatewayAssertionContext:
        if not token or not token.strip():
            raise GatewayAssertionError("gateway_assertion_header_missing")

        verify_key = self._resolve_verify_key(token)
        claims = self._decode_claims(token, verify_key)
        self._validate_binding(claims, request_method, request_path)
        return self._build_context(claims)

    def _decode_claims(self, token: str, verify_key: Any) -> dict[str, Any]:
        try:
            claims = jwt.decode(
                token,
                key=verify_key,
                algorithms=["EdDSA"],
                audience=self._config.audience,
                issuer=self._config.issuer,
                options={"require": ["exp", "iat", "sub", "jti"]},
                leeway=max(0, self._config.max_clock_skew_seconds),
            )
        except jwt.ExpiredSignatureError as exc:
            raise GatewayAssertionError("gateway_assertion_expired") from exc
        except jwt.InvalidAudienceError as exc:
            raise GatewayAssertionError("gateway_assertion_audience_invalid") from exc
        except jwt.InvalidIssuerError as exc:
            raise GatewayAssertionError("gateway_assertion_issuer_invalid") from exc
        except InvalidTokenError as exc:
            raise GatewayAssertionError("gateway_assertion_invalid") from exc
        if not isinstance(claims, dict):
            raise GatewayAssertionError("gateway_assertion_payload_invalid")
        return claims

    def _validate_binding(
        self, claims: dict[str, Any], request_method: str, request_path: str
    ) -> None:
        asserted_method = self._normalize(claims.get(CLAIM_METHOD))
        if asserted_method is None or asserted_method.upper() != request_method.upper():
            raise GatewayAssertionError("gateway_assertion_method_mismatch")

        asserted_path = self._normalize(claims.get(CLAIM_PATH))
        if asserted_path is None or asserted_path != request_path:
            raise GatewayAssertionError("gateway_assertion_path_mismatch")

    def _build_context(self, claims: dict[str, Any]) -> GatewayAssertionContext:
        user_id = self._parse_positive_int(claims.get("sub"))
        tenant_id = self._parse_positive_int(claims.get(CLAIM_TENANT_ID))
        tenant_code = self._normalize(claims.get(CLAIM_TENANT_CODE))
        token_id = self._normalize(claims.get("jti"))
        if user_id is None or tenant_id is None or tenant_code is None or token_id is None:
            raise GatewayAssertionError("gateway_assertion_subject_invalid")

        username = self._normalize(claims.get(CLAIM_USERNAME)) or ""
        email = self._normalize(claims.get(CLAIM_EMAIL))
        roles = self._parse_roles(claims.get(CLAIM_ROLES))

        return GatewayAssertionContext(
            user_id=user_id,
            tenant_id=tenant_id,
            tenant_code=tenant_code,
            username=username,
            email=email,
            roles=roles,
            impersonation=bool(claims.get(CLAIM_IMPERSONATION)),
            actor_user_id=self._parse_positive_int(claims.get(CLAIM_ACTOR_USER_ID)),
            actor_tenant_id=self._parse_positive_int(claims.get(CLAIM_ACTOR_TENANT_ID)),
            token_id=token_id,
        )

    def _resolve_verify_key(self, token: str) -> Any:
        try:
            header = jwt.get_unverified_header(token)
        except InvalidTokenError as exc:
            raise GatewayAssertionError("gateway_assertion_header_invalid") from exc

        algorithm = self._normalize(header.get("alg"))
        if algorithm != "EdDSA":
            raise GatewayAssertionError("gateway_assertion_algorithm_invalid")

        kid = self._normalize(header.get("kid"))
        primary_kid = self._normalize(self._config.key_id)
        previous_kid = self._normalize(self._config.previous_key_id)

        if primary_kid is None:
            return self._public_key
        if kid is None:
            raise GatewayAssertionError("gateway_assertion_kid_missing")
        if kid == primary_kid:
            return self._public_key
        if kid == previous_kid and self._previous_public_key is not None:
            return self._previous_public_key
        raise GatewayAssertionError("gateway_assertion_kid_invalid")

    def _load_public_key(self, key_path: str) -> Any:
        resolved = self._resolve_path(key_path)
        if not resolved.exists() or not resolved.is_file():
            raise GatewayAssertionError(f"gateway_assertion_public_key_not_found:{resolved}")
        try:
            key_bytes = resolved.read_bytes()
            return key_bytes.decode("utf-8")
        except Exception as exc:
            raise GatewayAssertionError("gateway_assertion_public_key_invalid") from exc

    def _resolve_path(self, key_path: str) -> Path:
        normalized = self._normalize(key_path)
        if normalized is None:
            raise GatewayAssertionError("gateway_assertion_public_key_path_missing")

        if normalized.startswith("classpath:"):
            relative_path = normalized.removeprefix("classpath:").lstrip("/")
            classpath_root = Path(__file__).resolve().parents[2]
            return classpath_root / relative_path

        path = Path(normalized)
        if path.is_absolute():
            return path
        return Path.cwd() / path

    def _parse_roles(self, value: Any) -> list[str]:
        if value is None:
            return []
        if isinstance(value, str):
            normalized = self._normalize(value)
            return [] if normalized is None else [normalized]
        if not isinstance(value, list):
            return []

        result: list[str] = []
        for role in value:
            normalized = self._normalize(role)
            if normalized is not None:
                result.append(normalized)
        return result

    def _parse_positive_int(self, value: Any) -> int | None:
        if isinstance(value, bool):
            return None
        if isinstance(value, int):
            return value if value > 0 else None
        if isinstance(value, str):
            normalized = self._normalize(value)
            if normalized is None:
                return None
            try:
                parsed = int(normalized)
            except ValueError:
                return None
            return parsed if parsed > 0 else None
        return None

    def _normalize(self, value: Any) -> str | None:
        if not isinstance(value, str):
            return None
        normalized = value.strip()
        return normalized if normalized else None
