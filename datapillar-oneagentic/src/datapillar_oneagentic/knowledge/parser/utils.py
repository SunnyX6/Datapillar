# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Parser utilities."""

from __future__ import annotations

import mimetypes
import os
import uuid
from typing import Any

import httpx

from datapillar_oneagentic.knowledge.models import DocumentInput


def build_document_id() -> str:
    return uuid.uuid4().hex


def guess_mime_type(doc_input: DocumentInput) -> str:
    if doc_input.mime_type:
        return doc_input.mime_type
    filename = doc_input.filename or _infer_filename(doc_input.source)
    if filename:
        mime_type, _ = mimetypes.guess_type(filename)
        if mime_type:
            return mime_type
    return "text/plain"


def load_bytes(doc_input: DocumentInput) -> bytes:
    source = doc_input.source
    if isinstance(source, bytes):
        return source
    if _is_url(source):
        response = httpx.get(source, timeout=30.0)
        response.raise_for_status()
        return response.content
    if os.path.exists(source):
        with open(source, "rb") as f:
            return f.read()
    return source.encode("utf-8", errors="ignore")


def load_text(doc_input: DocumentInput, *, encoding: str = "utf-8") -> str:
    source = doc_input.source
    if isinstance(source, str) and not _is_url(source) and not os.path.exists(source):
        return source
    data = load_bytes(doc_input)
    return data.decode(encoding, errors="ignore")


def extract_extension(doc_input: DocumentInput) -> str | None:
    filename = doc_input.filename or _infer_filename(doc_input.source)
    if not filename:
        return None
    _, ext = os.path.splitext(filename)
    return ext.lower() if ext else None


def normalize_metadata(metadata: dict[str, Any] | None) -> dict[str, Any]:
    return dict(metadata or {})


def _infer_filename(source: str | bytes) -> str | None:
    if isinstance(source, bytes):
        return None
    if _is_url(source):
        return source.split("?")[0].split("/")[-1] or None
    if os.path.exists(source):
        return os.path.basename(source)
    return None


def _is_url(value: str) -> bool:
    return value.startswith("http://") or value.startswith("https://")
