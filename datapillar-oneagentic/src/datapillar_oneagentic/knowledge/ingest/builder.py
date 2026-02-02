# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge ingestion builder."""

from __future__ import annotations

import hashlib

from datapillar_oneagentic.knowledge.chunker.models import ChunkDraft
from datapillar_oneagentic.knowledge.config import KnowledgeWindowConfig
from datapillar_oneagentic.knowledge.models import (
    DocumentInput,
    KnowledgeChunk,
    KnowledgeDocument,
    KnowledgeSource,
    ParsedDocument,
)
from datapillar_oneagentic.utils.time import now_ms


def build_document(
    *,
    source: KnowledgeSource,
    parsed: ParsedDocument,
    doc_input: DocumentInput,
    doc_id: str,
) -> KnowledgeDocument:
    """Build document metadata."""
    if not doc_id or not str(doc_id).strip():
        raise ValueError("doc_id is required for knowledge document")
    doc_id = str(doc_id).strip()
    now = now_ms()
    content_hash = hash_content(parsed.text)
    source_uri = doc_input.filename
    if not source_uri and isinstance(doc_input.source, str):
        source_uri = doc_input.source
    attachments_meta = [
        {
            "attachment_id": att.attachment_id,
            "name": att.name,
            "mime_type": att.mime_type,
            "size": len(att.content or b""),
        }
        for att in parsed.attachments
    ]
    metadata = {
        "mime_type": parsed.mime_type,
        "parser": parsed.metadata.get("parser", ""),
        "attachments": attachments_meta,
        **parsed.metadata,
    }
    return KnowledgeDocument(
        doc_id=doc_id,
        source_id=source.source_id,
        title=parsed.metadata.get("title", doc_input.filename or doc_id),
        content=parsed.text,
        source_uri=source_uri or source.source_uri,
        content_hash=content_hash,
        status="published",
        created_at=now,
        updated_at=now,
        tags=source.tags,
        metadata=metadata,
    )


def build_chunks(
    *,
    source: KnowledgeSource,
    doc: KnowledgeDocument,
    drafts: list[ChunkDraft],
) -> list[KnowledgeChunk]:
    """Build knowledge chunks."""
    now = now_ms()
    chunks: list[KnowledgeChunk] = []
    for draft in drafts:
        chunks.append(
            KnowledgeChunk(
                chunk_id=draft.chunk_id,
                doc_id=doc.doc_id,
                source_id=source.source_id,
                doc_title=doc.title,
                parent_id=draft.parent_id,
                chunk_type=draft.chunk_type,
                content=draft.content,
                content_hash=hash_content(draft.content),
                vector=[],
                token_count=len(draft.content),
                chunk_index=draft.chunk_index,
                section_path="",
                version=doc.version,
                status="published",
                source_spans=draft.source_spans,
                metadata=draft.metadata,
                created_at=now,
                updated_at=now,
            )
        )
    return chunks


def apply_window_metadata(
    *,
    chunks: list[KnowledgeChunk],
    config: KnowledgeWindowConfig,
) -> None:
    if not config.enabled:
        return
    if config.radius <= 0:
        return

    groups: dict[str, list[KnowledgeChunk]] = {}
    for chunk in chunks:
        key = _window_group_key(chunk, config.scope)
        groups.setdefault(key, []).append(chunk)

    for group in groups.values():
        group.sort(key=lambda item: item.chunk_index)
        ordered_ids = [item.chunk_id for item in group]
        for idx, chunk in enumerate(group):
            prev_ids = ordered_ids[max(0, idx - config.radius) : idx]
            next_ids = ordered_ids[idx + 1 : idx + 1 + config.radius]
            metadata = dict(chunk.metadata or {})
            metadata["window_prev_ids"] = prev_ids
            metadata["window_next_ids"] = next_ids
            chunk.metadata = metadata


def _window_group_key(chunk: KnowledgeChunk, scope: str) -> str:
    mode = (scope or "auto").lower()
    if mode == "parent":
        return chunk.parent_id or chunk.doc_id
    if mode == "doc":
        return chunk.doc_id
    if chunk.chunk_type == "child" and chunk.parent_id:
        return f"parent:{chunk.parent_id}"
    return f"doc:{chunk.doc_id}:{chunk.chunk_type}"


def average_vectors(vectors: list[list[float]]) -> list[float]:
    if not vectors:
        return []
    length = len(vectors[0])
    sums = [0.0] * length
    for vec in vectors:
        for i, value in enumerate(vec):
            sums[i] += value
    return [v / len(vectors) for v in sums]


def hash_content(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()
