# @author Sunny
# @date 2026-02-07

"""Local file private key storage."""

from __future__ import annotations

from pathlib import Path

from src.infrastructure.keystore.base import KeyStorage
from src.shared.exception import BadRequestException, NotFoundException


class LocalKeyStorage(KeyStorage):
    def __init__(self, base_path: str) -> None:
        if not base_path or not base_path.strip():
            raise ValueError("key_storage.local_path cannot be empty")
        self._base_path = Path(base_path).expanduser().resolve()

    def load_private_key(self, tenant_code: str) -> bytes:
        normalized_tenant_code = str(tenant_code or "").strip()
        if not normalized_tenant_code:
            raise BadRequestException("tenant_code invalid")
        if self._is_unsafe_tenant_code(normalized_tenant_code):
            raise BadRequestException("tenant_code invalid")

        path = self._base_path / normalized_tenant_code / "private.pem"
        if not path.exists():
            raise NotFoundException(f"Tenant private key does not exist: {normalized_tenant_code}")
        return path.read_bytes()

    def _is_unsafe_tenant_code(self, tenant_code: str) -> bool:
        return "/" in tenant_code or "\\" in tenant_code or ".." in tenant_code
