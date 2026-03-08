# @author Sunny
# @date 2026-02-07

"""Object storage private key reading(S3/OSS/MinIO)."""

from __future__ import annotations

import boto3

from src.infrastructure.keystore.base import KeyStorage
from src.shared.exception import BadRequestException, NotFoundException


class ObjectStoreKeyStorage(KeyStorage):
    def __init__(self, config: dict[str, object]) -> None:
        bucket = (config.get("bucket") or "").strip()
        if not bucket:
            raise ValueError("key_storage.s3.bucket cannot be empty")
        self._bucket = bucket
        self._prefix = (config.get("prefix") or "privkeys").strip() or "privkeys"
        self._client = boto3.client(
            "s3",
            endpoint_url=(config.get("endpoint_url") or None),
            aws_access_key_id=(config.get("access_key") or None),
            aws_secret_access_key=(config.get("secret_key") or None),
            region_name=(config.get("region") or None),
        )

    def load_private_key(self, tenant_code: str) -> bytes:
        normalized_tenant_code = str(tenant_code or "").strip()
        if not normalized_tenant_code:
            raise BadRequestException("tenant_code invalid")
        if self._is_unsafe_tenant_code(normalized_tenant_code):
            raise BadRequestException("tenant_code invalid")

        key = f"{self._prefix}/{normalized_tenant_code}/private.pem"
        try:
            response = self._client.get_object(Bucket=self._bucket, Key=key)
            return response["Body"].read()
        except self._client.exceptions.NoSuchKey as exc:
            raise NotFoundException(
                f"Tenant private key does not exist: {normalized_tenant_code}"
            ) from exc

    def _is_unsafe_tenant_code(self, tenant_code: str) -> bool:
        return "/" in tenant_code or "\\" in tenant_code or ".." in tenant_code
