# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-07

"""私钥存储入口。"""

from __future__ import annotations

from functools import lru_cache

from src.infrastructure.keystore.base import KeyStorage
from src.infrastructure.keystore.local import LocalKeyStorage
from src.infrastructure.keystore.object_store import ObjectStoreKeyStorage
from src.shared.config.runtime import get_key_storage_config


@lru_cache(maxsize=1)
def get_key_storage() -> KeyStorage:
    config = get_key_storage_config()
    storage_type = (config.get("type") or "local").lower()
    if storage_type == "local":
        return LocalKeyStorage(config.get("local_path") or "")
    if storage_type == "s3":
        return ObjectStoreKeyStorage(config.get("s3") or {})
    raise ValueError(f"不支持的 key_storage 类型: {storage_type}")
