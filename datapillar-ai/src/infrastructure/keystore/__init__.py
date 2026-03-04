# @author Sunny
# @date 2026-02-07

"""Private key storage entrance."""

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
    raise ValueError(f"Not supported key_storage Type:{storage_type}")
