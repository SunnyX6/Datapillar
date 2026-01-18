"""
知识入库构建器
"""

from __future__ import annotations

import hashlib

from datapillar_oneagentic.knowledge.chunker.models import ChunkDraft
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
) -> KnowledgeDocument:
    """构建文档元数据"""
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
        doc_id=parsed.document_id,
        source_id=source.source_id,
        title=parsed.metadata.get("title", doc_input.filename or parsed.document_id),
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
    """构建知识分片"""
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
