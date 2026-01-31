from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import ContextResolveConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.models import KnowledgeChunk, KnowledgeSearchHit
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever


class _StubEmbeddingProvider:
    async def embed_text(self, text: str) -> list[float]:
        return [float(len(text)), 0.0]


class _StubKnowledgeStore:
    def __init__(self, *, search_results, neighbors) -> None:
        self._namespace = "ns_stub"
        self.search_results = list(search_results)
        self._neighbors = dict(neighbors)
        self.filters_used = []

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    def supports_hybrid(self) -> bool:
        return False

    @property
    def supports_full_text(self) -> bool:
        return False

    async def initialize(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def upsert_sources(self, sources):
        return None

    async def upsert_docs(self, docs):
        return None

    async def upsert_chunks(self, chunks):
        return None

    async def search_chunks(self, *, query_vector, k, filters=None):
        self.filters_used.append(filters)
        return list(self.search_results[:k])

    async def hybrid_search_chunks(self, *, query_vector, query_text, k, filters=None, rrf_k=60):
        return []

    async def full_text_search_chunks(self, *, query_text: str, k: int, filters=None):
        return []

    async def get_doc(self, doc_id):
        return None

    async def get_chunks(self, chunk_ids):
        return [self._neighbors[cid] for cid in chunk_ids if cid in self._neighbors]

    async def delete_chunks(self, chunk_ids):
        return 0

    async def query_chunks(self, *, filters=None, limit=None):
        return []

    async def delete_doc(self, doc_id):
        return 0

    async def delete_doc_chunks(self, doc_id):
        return 0


@pytest.mark.asyncio
async def test_context_window_expansion() -> None:
    anchor = KnowledgeChunk(
        chunk_id="c1",
        doc_id="d1",
        source_id="s1",
        content="anchor",
        vector=[1.0, 0.0],
        metadata={
            "window_prev_ids": ["c0"],
            "window_next_ids": ["c2"],
        },
    )
    prev_chunk = KnowledgeChunk(
        chunk_id="c0",
        doc_id="d1",
        source_id="s1",
        content="prev",
    )
    next_chunk = KnowledgeChunk(
        chunk_id="c2",
        doc_id="d1",
        source_id="s1",
        content="next",
    )

    hits = [KnowledgeSearchHit(chunk=anchor, score=0.9, score_kind="similarity")]
    store = _StubKnowledgeStore(search_results=hits, neighbors={"c0": prev_chunk, "c2": next_chunk})
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="semantic",
        context=ContextResolveConfig(mode="window", window_radius=1),
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )

    result = await retriever.retrieve(query="query")

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c0", "c1", "c2"]
