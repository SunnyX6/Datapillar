# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Chunking result models."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from datapillar_oneagentic.knowledge.models import Attachment, SourceSpan


@dataclass
class ChunkDraft:
    chunk_id: str
    content: str
    chunk_index: int
    chunk_type: str
    parent_id: str | None = None
    source_spans: list[SourceSpan] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ChunkPreview:
    document_id: str
    chunks: list[ChunkDraft]
    attachments: list[Attachment]
