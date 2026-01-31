# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge ingestion pipeline."""

from __future__ import annotations

import logging
from typing import Iterable, TYPE_CHECKING

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.identity import build_source_id
from datapillar_oneagentic.knowledge.ingest.builder import (
    apply_window_metadata,
    average_vectors,
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
            chunk_config = _resolve_chunk_config(source)
            _attach_chunk_config(source, chunk_config)
            doc_input = _build_document_input(source)
            parsed = self._parser_registry.parse(doc_input)
            chunker = KnowledgeChunker(config=chunk_config)
            previews.append(chunker.preview(parsed))
        return previews

    async def ingest(
        self,
        *,
        sources: Iterable[KnowledgeSource],
        sparse_embedder: SparseEmbeddingProvider | None = None,
    ) -> None:
        source_list = list(sources)
        if not source_list:
            return

        resolved_sources = []
        source_items: list[tuple[KnowledgeSource, DocumentInput, KnowledgeChunkConfig]] = []
        for source in source_list:
            chunk_config = _resolve_chunk_config(source)
            _attach_chunk_config(source, chunk_config)
            doc_input = _build_document_input(source)
            resolved_sources.append(self._resolve_source(source))
            source_items.append((source, doc_input, chunk_config))
        await self._store.upsert_sources(resolved_sources)

        all_docs: dict[str, KnowledgeDocument] = {}
        all_chunks: dict[str, list[KnowledgeChunk]] = {}
        seen_doc_ids: set[str] = set()

        for source, doc_input, chunk_config in source_items:
            parsed = self._parser_registry.parse(doc_input)
            chunker = KnowledgeChunker(config=chunk_config)
            preview = chunker.preview(parsed)
            if not preview.chunks:
                continue
            if parsed.document_id not in seen_doc_ids:
                await self._rebuild_doc(parsed.document_id)
                seen_doc_ids.add(parsed.document_id)
            doc = build_document(source=source, parsed=parsed, doc_input=doc_input)
            chunks = build_chunks(source=source, doc=doc, drafts=preview.chunks)
            apply_window_metadata(chunks=chunks, config=chunk_config.window)

            vectors: list[list[float]] = []
            sparse_vectors = None
            if self._store.supports_external_embeddings:
                vectors = await self._embedding_provider.embed_texts([c.content for c in chunks])
                use_sparse = sparse_embedder is not None and not self._store.supports_hybrid
                if use_sparse:
                    sparse_vectors = await sparse_embedder.embed_texts([c.content for c in chunks])

                for idx, chunk in enumerate(chunks):
                    chunk.vector = vectors[idx]
                    if sparse_vectors:
                        chunk.sparse_vector = sparse_vectors[idx]

                doc.vector = average_vectors(vectors)
            all_docs[doc.doc_id] = doc
            all_chunks[doc.doc_id] = chunks

        await self._store.upsert_docs(list(all_docs.values()))
        flat_chunks: list[KnowledgeChunk] = []
        for chunks in all_chunks.values():
            flat_chunks.extend(chunks)
        await self._store.upsert_chunks(flat_chunks)
        logger.info(
            "Knowledge ingestion completed: sources=%s, docs=%s, chunks=%s",
            len(resolved_sources),
            len(all_docs),
            len(flat_chunks),
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
