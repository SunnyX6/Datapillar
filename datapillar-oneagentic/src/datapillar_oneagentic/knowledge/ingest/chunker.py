# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge chunker."""

from __future__ import annotations

from dataclasses import dataclass

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter


@dataclass
class TextChunk:
    index: int
    content: str


def split_text(
    text: str,
    *,
    chunk_size: int,
    chunk_overlap: int,
    min_chunk_size: int,
) -> list[TextChunk]:
    """Split text using RecursiveCharacterTextSplitter."""
    if not text:
        return []
    if chunk_size <= 0:
        return [TextChunk(index=0, content=text)]
    overlap = max(0, min(chunk_overlap, chunk_size - 1))
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=overlap,
        add_start_index=False,
    )
    docs = splitter.split_documents([Document(page_content=text)])
    chunks: list[TextChunk] = []
    for idx, doc in enumerate(docs):
        content = (doc.page_content or "").strip()
        if content and len(content) >= min_chunk_size:
            chunks.append(TextChunk(index=idx, content=content))
    return chunks
