# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-07

"""对象存储私钥读取（S3/OSS/MinIO）。"""

from __future__ import annotations

import boto3

from src.infrastructure.keystore.base import KeyStorage


class ObjectStoreKeyStorage(KeyStorage):
    def __init__(self, config: dict[str, object]) -> None:
        bucket = (config.get("bucket") or "").strip()
        if not bucket:
            raise ValueError("key_storage.s3.bucket 不能为空")
        self._bucket = bucket
        self._prefix = (config.get("prefix") or "privkeys").strip() or "privkeys"
        self._client = boto3.client(
            "s3",
            endpoint_url=(config.get("endpoint_url") or None),
            aws_access_key_id=(config.get("access_key") or None),
            aws_secret_access_key=(config.get("secret_key") or None),
            region_name=(config.get("region") or None),
        )

    def load_private_key(self, tenant_id: int) -> bytes:
        if tenant_id <= 0:
            raise ValueError("tenant_id 无效")
        key = f"{self._prefix}/{tenant_id}/private.pem"
        response = self._client.get_object(Bucket=self._bucket, Key=key)
        return response["Body"].read()
