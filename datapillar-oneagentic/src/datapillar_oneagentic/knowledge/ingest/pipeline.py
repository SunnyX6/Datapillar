# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge ingestion pipeline."""

from __future__ import annotations

import inspect
import logging
from collections.abc import Awaitable, Callable, Iterable
from typing import TYPE_CHECKING

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.identity import build_source_id
from datapillar_oneagentic.knowledge.ingest.builder import (
    apply_window_metadata,
    build_chunks,
    build_document,
)
from datapillar_oneagentic.knowledge.models import (
    DocumentInput,
    KnowledgeSource,
    SparseEmbeddingProvider,
    _build_document_input,
)
from datapillar_oneagentic.knowledge.parser import ParserRegistry, default_registry
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore

if TYPE_CHECKING:
    from datapillar_oneagentic.knowledge.models import KnowledgeChunk, KnowledgeDocument
logger = logging.getLogger(__name__)


ProgressCallback = Callable[[int, int], Awaitable[None] | None]


class KnowledgeIngestor:
    """Knowledge ingestor."""

    def __init__(
        self,
        *,
        store: KnowledgeStore,
        embedding_provider: EmbeddingProvider,
        parser_registry: ParserRegistry | None = None,
    ) -> None:
        self._store = store
        self._embedding_provider = embedding_provider
        self._parser_registry = parser_registry or default_registry()

    def preview(self, *, sources: Iterable[KnowledgeSource]) -> list[ChunkPreview]:
        previews: list[ChunkPreview] = []
        for source in sources:
            doc_uid = _resolve_doc_uid(source)
            chunk_config = _resolve_chunk_config(source)
            _attach_chunk_config(source, chunk_config)
            doc_input = _build_document_input(source)
            parsed = self._parser_registry.parse(doc_input)
            chunker = KnowledgeChunker(config=chunk_config)
            previews.append(chunker.preview(parsed, doc_id=doc_uid))
        return previews

    async def ingest(
        self,
        *,
        sources: Iterable[KnowledgeSource],
        sparse_embedder: SparseEmbeddingProvider | None = None,
        batch_size: int | None = None,
        progress_cb: ProgressCallback | None = None,
        progress_step: int | None = None,
    ) -> None:
        source_list = list(sources)
        if not source_list:
            return

        resolved_sources = []
        source_items: list[tuple[KnowledgeSource, DocumentInput, KnowledgeChunkConfig, str]] = []
        for source in source_list:
            doc_uid = _resolve_doc_uid(source)
            chunk_config = _resolve_chunk_config(source)
            _attach_chunk_config(source, chunk_config)
            doc_input = _build_document_input(source)
            resolved_sources.append(self._resolve_source(source))
            source_items.append((source, doc_input, chunk_config, doc_uid))
        await self._store.upsert_sources(resolved_sources)

        all_docs: dict[str, KnowledgeDocument] = {}
        all_chunks: dict[str, list[KnowledgeChunk]] = {}
        seen_doc_ids: set[str] = set()
        total_chunks = 0

        for source, doc_input, chunk_config, doc_uid in source_items:
            parsed = self._parser_registry.parse(doc_input)
            chunker = KnowledgeChunker(config=chunk_config)
            preview = chunker.preview(parsed, doc_id=doc_uid)
            if not preview.chunks:
                continue
            if doc_uid not in seen_doc_ids:
                await self._rebuild_doc(doc_uid)
                seen_doc_ids.add(doc_uid)
            doc = build_document(source=source, parsed=parsed, doc_input=doc_input, doc_id=doc_uid)
            chunks = build_chunks(source=source, doc=doc, drafts=preview.chunks)
            apply_window_metadata(chunks=chunks, config=chunk_config.window)
            all_docs[doc.doc_id] = doc
            all_chunks[doc.doc_id] = chunks
            total_chunks += len(chunks)

        if not all_chunks:
            return

        processed = 0

        for doc_id, chunks in all_chunks.items():
            doc = all_docs[doc_id]
            vector_sum: list[float] | None = None
            vector_count = 0
            doc_batch = _normalize_batch_size(batch_size, fallback=len(chunks))
            for idx in range(0, len(chunks), doc_batch):
                batch = chunks[idx : idx + doc_batch]
                if self._store.supports_external_embeddings:
                    vectors = await self._embedding_provider.embed_texts([c.content for c in batch])
                    use_sparse = sparse_embedder is not None and not self._store.supports_hybrid
                    sparse_vectors = None
                    if use_sparse:
                        sparse_vectors = await sparse_embedder.embed_texts([c.content for c in batch])

                    for row_idx, chunk in enumerate(batch):
                        vector = vectors[row_idx]
                        chunk.vector = vector
                        if sparse_vectors:
                            chunk.sparse_vector = sparse_vectors[row_idx]
                        if vector_sum is None:
                            vector_sum = [0.0 for _ in vector]
                        for dim_idx, value in enumerate(vector):
                            vector_sum[dim_idx] += value
                        vector_count += 1
                await self._store.upsert_chunks(batch)
                processed += len(batch)
                await _notify_progress(
                    progress_cb=progress_cb,
                    processed=processed,
                    total=total_chunks,
                    progress_step=progress_step,
                )

            if self._store.supports_external_embeddings:
                doc.vector = (
                    [value / vector_count for value in vector_sum] if vector_sum and vector_count else []
                )
            await self._store.upsert_docs([doc])
        logger.info(
            "Knowledge ingestion completed: sources=%s, docs=%s, chunks=%s",
            len(resolved_sources),
            len(all_docs),
            total_chunks,
        )

    def _resolve_source(self, source: KnowledgeSource) -> KnowledgeSource:
        source_id = build_source_id(
            namespace=self._store.namespace,
            source_type=source.source_type,
            source_uri=source.source_uri,
            metadata=source.metadata,
        )
        source.source_id = source_id
        return source

    async def _rebuild_doc(self, doc_id: str) -> None:
        existing = await self._store.get_doc(doc_id)
        if not existing:
            return
        await self._store.delete_doc_chunks(doc_id)
        await self._store.delete_doc(doc_id)


def _resolve_chunk_config(source: KnowledgeSource) -> KnowledgeChunkConfig:
    payload = source.chunk
    if payload is None:
        raise ValueError("Chunk config is required for each knowledge source.")
    if isinstance(payload, KnowledgeChunkConfig):
        return payload
    if isinstance(payload, dict):
        return KnowledgeChunkConfig.model_validate(payload)
    raise TypeError(f"Unsupported chunk config type: {type(payload).__name__}")


def _attach_chunk_config(source: KnowledgeSource, chunk_config: KnowledgeChunkConfig) -> None:
    metadata = dict(source.metadata or {})
    metadata["chunk_config"] = chunk_config.model_dump(mode="json")
    source.metadata = metadata


def _resolve_doc_uid(source: KnowledgeSource) -> str:
    doc_uid = getattr(source, "doc_uid", None)
    if not doc_uid or not str(doc_uid).strip():
        raise ValueError("doc_uid is required for knowledge ingestion")
    doc_uid = str(doc_uid).strip()
    metadata = dict(source.metadata or {})
    metadata.setdefault("doc_uid", doc_uid)
    source.metadata = metadata
    return doc_uid


def _normalize_batch_size(value: int | None, *, fallback: int) -> int:
    if value is None:
        return max(1, fallback)
    if isinstance(value, int) and value > 0:
        return value
    raise ValueError("batch_size must be a positive integer")


async def _notify_progress(
    *,
    progress_cb: ProgressCallback | None,
    processed: int,
    total: int,
    progress_step: int | None,
) -> None:
    if progress_cb is None:
        return
    if total <= 0:
        return
    step = progress_step or 0
    if step > 0 and processed % step != 0 and processed < total:
        return
    payload = progress_cb(processed, total)
    if inspect.isawaitable(payload):
        await payload
