"""Knowledge identity hashing."""

from __future__ import annotations

import hashlib
import json
from typing import Any


def build_source_id(
    *,
    namespace: str,
    source_type: str,
    source_uri: str | None,
    metadata: dict[str, Any] | None,
) -> str:
    canonical = canonicalize_metadata(metadata)
    raw = f"{namespace}|{source_type}|{source_uri or ''}|{canonical}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def build_doc_id(normalized_text: str) -> str:
    return hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()


def canonicalize_metadata(metadata: dict[str, Any] | None) -> str:
    normalized = _normalize_value(metadata or {})
    return json.dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _normalize_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _normalize_value(value[key]) for key in sorted(value.keys())}
    if isinstance(value, list):
        normalized_list = [_normalize_value(item) for item in value]
        normalized_list.sort(key=_normalize_sort_key)
        return normalized_list
    if isinstance(value, tuple):
        normalized_list = [_normalize_value(item) for item in value]
        normalized_list.sort(key=_normalize_sort_key)
        return normalized_list
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return str(value)


def _normalize_sort_key(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
