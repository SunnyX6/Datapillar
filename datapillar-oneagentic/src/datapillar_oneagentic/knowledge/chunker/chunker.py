"""
切分执行器
"""

from __future__ import annotations

import re
from typing import Iterable

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from datapillar_oneagentic.knowledge.chunker.cleaner import apply_preprocess
from datapillar_oneagentic.knowledge.chunker.models import ChunkDraft, ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.models import ParsedDocument, SourceSpan


class KnowledgeChunker:
    """知识切分器"""

    def __init__(self, *, config: KnowledgeChunkConfig) -> None:
        self._config = config

    def preview(self, parsed: ParsedDocument) -> ChunkPreview:
        text = apply_preprocess(parsed.text, self._config.preprocess)
        mode = (self._config.mode or "general").lower()
        if mode == "general":
            chunks = _split_general(parsed.document_id, text, self._config.general)
        elif mode == "parent_child":
            chunks = _split_parent_child(parsed.document_id, text, self._config.parent_child)
        elif mode == "qa":
            chunks = _split_qa(parsed.document_id, text, self._config.qa.pattern)
        else:
            raise ValueError(f"不支持的切分模式: {mode}")

        return ChunkPreview(
            document_id=parsed.document_id,
            chunks=chunks,
            attachments=parsed.attachments,
        )


def _split_general(doc_id: str, text: str, config) -> list[ChunkDraft]:
    splitter = _build_splitter(config.max_tokens, config.overlap)
    parts = _split_with_splitter(splitter, text, config.delimiter)
    return _build_chunks(doc_id, parts, chunk_type="parent")


def _split_parent_child(doc_id: str, text: str, config) -> list[ChunkDraft]:
    parent_splitter = _build_splitter(config.parent.max_tokens, config.parent.overlap)
    parent_parts = _split_with_splitter(parent_splitter, text, config.parent.delimiter)
    chunks: list[ChunkDraft] = []
    for parent_index, (parent_text, parent_start) in enumerate(parent_parts):
        parent_id = f"{doc_id}:p{parent_index}"
        parent_spans = _build_spans(parent_start, parent_text)
        chunks.append(
            ChunkDraft(
                chunk_id=parent_id,
                content=parent_text,
                chunk_index=parent_index,
                chunk_type="parent",
                parent_id=None,
                source_spans=parent_spans,
            )
        )

        child_splitter = _build_splitter(config.child.max_tokens, config.child.overlap)
        child_parts = _split_with_splitter(child_splitter, parent_text, config.child.delimiter)
        for child_index, (child_text, child_start) in enumerate(child_parts):
            child_id = f"{doc_id}:c{parent_index}:{child_index}"
            absolute_start = parent_start + child_start
            chunks.append(
                ChunkDraft(
                    chunk_id=child_id,
                    content=child_text,
                    chunk_index=len(chunks),
                    chunk_type="child",
                    parent_id=parent_id,
                    source_spans=_build_spans(absolute_start, child_text),
                )
            )
    return chunks


def _split_qa(doc_id: str, text: str, pattern: str) -> list[ChunkDraft]:
    matches = re.findall(pattern, text, re.UNICODE)
    chunks: list[ChunkDraft] = []
    for idx, (q, a) in enumerate(matches):
        q = q.strip()
        a = re.sub(r"\n\s*", "\n", a.strip())
        if not q or not a:
            continue
        content = f"Q: {q}\nA: {a}"
        chunks.append(
            ChunkDraft(
                chunk_id=f"{doc_id}:qa{idx}",
                content=content,
                chunk_index=idx,
                chunk_type="parent",
                source_spans=_build_spans(None, content),
            )
        )
    if chunks:
        return chunks
    return _split_general(doc_id, text, _fallback_general_config())


def _build_splitter(max_tokens: int, overlap: int) -> RecursiveCharacterTextSplitter:
    return RecursiveCharacterTextSplitter(
        chunk_size=max_tokens,
        chunk_overlap=overlap,
        add_start_index=True,
    )


def _split_with_splitter(
    splitter: RecursiveCharacterTextSplitter, text: str, delimiter: str | None
) -> list[tuple[str, int]]:
    parts: list[tuple[str, int]] = []
    if delimiter:
        segments = [seg for seg in text.split(delimiter) if seg.strip()]
        offset = 0
        for seg in segments:
            seg_docs = splitter.split_documents([Document(page_content=seg)])
            for doc in seg_docs:
                start = doc.metadata.get("start_index", 0) + offset
                parts.append((doc.page_content, start))
            offset += len(seg) + len(delimiter)
        return parts

    docs = splitter.split_documents([Document(page_content=text)])
    for doc in docs:
        parts.append((doc.page_content, doc.metadata.get("start_index", 0)))
    return parts


def _build_chunks(
    doc_id: str,
    parts: Iterable[tuple[str, int]],
    *,
    chunk_type: str,
) -> list[ChunkDraft]:
    chunks: list[ChunkDraft] = []
    for idx, (content, start) in enumerate(parts):
        if not content.strip():
            continue
        chunks.append(
            ChunkDraft(
                chunk_id=f"{doc_id}:{idx}",
                content=content,
                chunk_index=idx,
                chunk_type=chunk_type,
                source_spans=_build_spans(start, content),
            )
        )
    return chunks


def _build_spans(start: int | None, content: str) -> list[SourceSpan]:
    if start is None:
        return []
    return [SourceSpan(start_offset=start, end_offset=start + len(content))]


def _fallback_general_config():
    class _Config:
        delimiter = None
        max_tokens = 800
        overlap = 120

    return _Config()
