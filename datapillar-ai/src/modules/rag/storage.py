# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 文件存储."""

from __future__ import annotations

import asyncio
import os
import uuid
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse


@dataclass
class StorageResult:
    storage_uri: str
    storage_type: str
    storage_key: str
    size_bytes: int


class StorageManager:
    def __init__(self, config: dict[str, Any]) -> None:
        self._config = config
        self._storage_type = (config.get("type") or "local").lower()
        self._local_dir = config.get("local_dir") or "./data/knowledge_wiki"
        self._s3_cfg = config.get("s3", {})
        self._s3_client = None

        if self._storage_type == "s3":
            self._init_s3_client()

    async def save(self, *, namespace_id: int, filename: str, content: bytes) -> StorageResult:
        if self._storage_type == "s3":
            return await asyncio.to_thread(self._save_s3, namespace_id, filename, content)
        return await asyncio.to_thread(self._save_local, namespace_id, filename, content)

    async def read(self, storage_uri: str) -> bytes:
        if storage_uri.startswith("s3://"):
            return await asyncio.to_thread(self._read_s3, storage_uri)
        if storage_uri.startswith("file://"):
            return await asyncio.to_thread(self._read_local, storage_uri)
        raise ValueError("Unsupported storage_uri")

    def _save_local(self, namespace_id: int, filename: str, content: bytes) -> StorageResult:
        safe_name = _sanitize_filename(filename)
        key = f"{namespace_id}/{uuid.uuid4().hex}_{safe_name}"
        path = os.path.abspath(os.path.join(self._local_dir, key))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(content)
        return StorageResult(
            storage_uri=f"file:///{path.lstrip('/')}",
            storage_type="local",
            storage_key=key,
            size_bytes=len(content),
        )

    def _read_local(self, storage_uri: str) -> bytes:
        parsed = urlparse(storage_uri)
        path = os.path.abspath(parsed.path)
        if not os.path.exists(path):
            raise FileNotFoundError("Local file not found")
        with open(path, "rb") as f:
            return f.read()

    def _init_s3_client(self) -> None:
        import boto3

        endpoint_url = self._s3_cfg.get("endpoint_url") or None
        access_key = self._s3_cfg.get("access_key") or None
        secret_key = self._s3_cfg.get("secret_key") or None
        region = self._s3_cfg.get("region") or None
        self._s3_client = boto3.client(
            "s3",
            endpoint_url=endpoint_url,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
        )

    def _save_s3(self, namespace_id: int, filename: str, content: bytes) -> StorageResult:
        if not self._s3_client:
            raise ValueError("S3 client not configured")
        bucket = self._s3_cfg.get("bucket")
        if not bucket:
            raise ValueError("S3 bucket is required")
        safe_name = _sanitize_filename(filename)
        key = f"{namespace_id}/{uuid.uuid4().hex}_{safe_name}"
        self._s3_client.put_object(Bucket=bucket, Key=key, Body=content)
        return StorageResult(
            storage_uri=f"s3://{bucket}/{key}",
            storage_type="s3",
            storage_key=key,
            size_bytes=len(content),
        )

    def _read_s3(self, storage_uri: str) -> bytes:
        if not self._s3_client:
            raise ValueError("S3 client not configured")
        parsed = urlparse(storage_uri)
        bucket = parsed.netloc
        key = parsed.path.lstrip("/")
        if not bucket or not key:
            raise ValueError("Invalid s3 uri")
        response = self._s3_client.get_object(Bucket=bucket, Key=key)
        body = response.get("Body")
        if not body:
            raise ValueError("S3 object body is empty")
        return body.read()


def _sanitize_filename(name: str) -> str:
    if not name:
        return "document"
    return "".join(ch for ch in name if ch.isalnum() or ch in {"-", "_", ".", " "}).strip() or "document"
