"""
知识入库管道
"""

from __future__ import annotations

import logging
from typing import Iterable, TYPE_CHECKING

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.ingest.builder import (
    average_vectors,
    build_chunks,
    build_document,
)
from datapillar_oneagentic.knowledge.models import DocumentInput, KnowledgeSource, SparseEmbeddingProvider
from datapillar_oneagentic.knowledge.parser import ParserRegistry, default_registry
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProviderClient
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore

if TYPE_CHECKING:
    from datapillar_oneagentic.knowledge.models import KnowledgeChunk, KnowledgeDocument
logger = logging.getLogger(__name__)


class KnowledgeIngestor:
    """知识入库器"""

    def __init__(
        self,
        *,
        store: KnowledgeStore,
        embedding_provider: EmbeddingProviderClient,
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

        await self._store.upsert_sources([source])

        all_docs: list[KnowledgeDocument] = []
        all_chunks: list[KnowledgeChunk] = []

        for doc_input in inputs:
            parsed = self._parser_registry.parse(doc_input)
            preview = self._chunker.preview(parsed)
            if not preview.chunks:
                continue
            doc = build_document(source=source, parsed=parsed, doc_input=doc_input)
            chunks = build_chunks(source=source, doc=doc, drafts=preview.chunks)

            vectors = await self._embedding_provider.embed_texts([c.content for c in chunks])
            sparse_vectors = None
            if sparse_embedder:
                sparse_vectors = await sparse_embedder.embed_texts([c.content for c in chunks])

            for idx, chunk in enumerate(chunks):
                chunk.vector = vectors[idx]
                if sparse_vectors:
                    chunk.sparse_vector = sparse_vectors[idx]

            doc.vector = average_vectors(vectors)
            all_docs.append(doc)
            all_chunks.extend(chunks)

        await self._store.upsert_docs(all_docs)
        await self._store.upsert_chunks(all_chunks)
        logger.info(f"知识入库完成: source_id={source.source_id}, docs={len(all_docs)}, chunks={len(all_chunks)}")
