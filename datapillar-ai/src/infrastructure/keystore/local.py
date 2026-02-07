# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-07

"""本地文件私钥存储。"""

from __future__ import annotations

from pathlib import Path

from src.infrastructure.keystore.base import KeyStorage


class LocalKeyStorage(KeyStorage):
    def __init__(self, base_path: str) -> None:
        if not base_path or not base_path.strip():
            raise ValueError("key_storage.local_path 不能为空")
        self._base_path = Path(base_path).expanduser().resolve()

    def load_private_key(self, tenant_id: int) -> bytes:
        if tenant_id <= 0:
            raise ValueError("tenant_id 无效")
        path = self._base_path / str(tenant_id) / "private.pem"
        if not path.exists():
            raise FileNotFoundError(f"租户私钥不存在: {tenant_id}")
        return path.read_bytes()
