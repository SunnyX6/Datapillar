"""Knowledge ingestion pipeline."""

from __future__ import annotations

import logging
from typing import Iterable, TYPE_CHECKING

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.identity import build_source_id
from datapillar_oneagentic.knowledge.ingest.builder import average_vectors, build_chunks, build_document
from datapillar_oneagentic.knowledge.models import DocumentInput, KnowledgeSource, SparseEmbeddingProvider
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
        config: KnowledgeChunkConfig,
        parser_registry: ParserRegistry | None = None,
    ) -> None:
        self._store = store
        self._embedding_provider = embedding_provider
        self._config = config
        self._parser_registry = parser_registry or default_registry()
        self._chunker = KnowledgeChunker(config=config)

    def preview(self, *, documents: Iterable[DocumentInput]) -> list[ChunkPreview]:
        previews: list[ChunkPreview] = []
        for doc_input in documents:
            parsed = self._parser_registry.parse(doc_input)
            previews.append(self._chunker.preview(parsed))
        return previews

    async def ingest(
        self,
        *,
        source: KnowledgeSource,
        documents: Iterable[DocumentInput],
        sparse_embedder: SparseEmbeddingProvider | None = None,
    ) -> None:
        inputs = list(documents)
        if not inputs:
            return

        resolved_source = self._resolve_source(source)
        await self._store.upsert_sources([resolved_source])

        all_docs: dict[str, KnowledgeDocument] = {}
        all_chunks: dict[str, list[KnowledgeChunk]] = {}
        seen_doc_ids: set[str] = set()

        for doc_input in inputs:
            parsed = self._parser_registry.parse(doc_input)
            preview = self._chunker.preview(parsed)
            if not preview.chunks:
                continue
            if parsed.document_id not in seen_doc_ids:
                await self._rebuild_doc(parsed.document_id)
                seen_doc_ids.add(parsed.document_id)
            doc = build_document(source=resolved_source, parsed=parsed, doc_input=doc_input)
            chunks = build_chunks(source=resolved_source, doc=doc, drafts=preview.chunks)

            vectors = await self._embedding_provider.embed_texts([c.content for c in chunks])
            sparse_vectors = None
            if sparse_embedder:
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
            "Knowledge ingestion completed: source_id=%s, docs=%s, chunks=%s",
            resolved_source.source_id,
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
