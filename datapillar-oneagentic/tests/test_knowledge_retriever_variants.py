from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import KnowledgeConfig, KnowledgeRetrieveConfig, RerankConfig
from datapillar_oneagentic.knowledge.models import (
    Knowledge,
    KnowledgeChunk,
    KnowledgeScope,
    KnowledgeSearchHit,
    KnowledgeSource,
)
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever


class _StubEmbeddingProvider:
    async def embed_text(self, text: str) -> list[float]:
        return [float(len(text)), 0.0]


class _StubSparseEmbedder:
    def __init__(self, value: float = 1.0) -> None:
        self._value = value

    async def embed_text(self, text: str) -> dict[int, float]:
        return {1: self._value}


class _StubKnowledgeStore:
    def __init__(self, *, search_results: list[KnowledgeSearchHit]) -> None:
        self.search_results = list(search_results)

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict | None = None,
    ) -> list[KnowledgeSearchHit]:
        return list(self.search_results[:k])

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        return []


@pytest.mark.asyncio
async def test_retriever_applies_score_threshold() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
            ),
            score=0.1,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[0.0, 1.0],
            ),
            score=10.0,
            score_kind="distance",
        ),
    ]
    store = _StubKnowledgeStore(search_results=hits)
    config = KnowledgeConfig(
        retrieve=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=5,
            score_threshold=0.5,
        )
    )
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c1"]


@pytest.mark.asyncio
async def test_retriever_hybrid_rrf_uses_sparse_rank() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
                sparse_vector={1: 0.1},
            ),
            score=0.1,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[0.8, 0.0],
                sparse_vector={1: 1.0},
            ),
            score=0.2,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c3",
                doc_id="d3",
                source_id="s1",
                content="gamma",
                vector=[0.7, 0.0],
                sparse_vector={1: 0.5},
            ),
            score=0.3,
            score_kind="distance",
        ),
    ]
    store = _StubKnowledgeStore(search_results=hits)
    config = KnowledgeConfig(
        retrieve=KnowledgeRetrieveConfig(
            method="hybrid",
            top_k=3,
            tuning={"rrf_k": 1},
        )
    )
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits][0] == "c2"


@pytest.mark.asyncio
async def test_retriever_filters_by_document_ids() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
            ),
            score=0.1,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[0.0, 1.0],
            ),
            score=0.2,
            score_kind="distance",
        ),
    ]
    store = _StubKnowledgeStore(search_results=hits)
    config = KnowledgeConfig(
        retrieve=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=2,
        )
    )
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(
        query="query",
        knowledge=knowledge,
        scope=KnowledgeScope(document_ids=["d1"]),
    )

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c1"]


@pytest.mark.asyncio
async def test_retriever_scope_tags_not_supported() -> None:
    store = _StubKnowledgeStore(search_results=[])
    config = KnowledgeConfig(retrieve=KnowledgeRetrieveConfig(method="semantic"))
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    with pytest.raises(ValueError):
        await retriever.retrieve(
            query="query",
            knowledge=knowledge,
            scope=KnowledgeScope(tags=["tag"]),
        )


@pytest.mark.asyncio
async def test_retriever_scope_multiple_namespaces_not_supported() -> None:
    store = _StubKnowledgeStore(search_results=[])
    config = KnowledgeConfig(retrieve=KnowledgeRetrieveConfig(method="semantic"))
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    with pytest.raises(ValueError):
        await retriever.retrieve(
            query="query",
            knowledge=knowledge,
            scope=KnowledgeScope(namespaces=["n1", "n2"]),
        )


@pytest.mark.asyncio
async def test_rerank_weighted_prefers_rerank_scores(monkeypatch) -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
            ),
            score=0.1,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[0.0, 1.0],
            ),
            score=0.2,
            score_kind="distance",
        ),
    ]

    async def _stub_rerank_scores(*args, **kwargs) -> list[float]:
        return [0.1, 0.9]

    def _stub_build_reranker(*args, **kwargs):
        return object()

    import datapillar_oneagentic.knowledge.retriever.retriever as retriever_module

    monkeypatch.setattr(retriever_module, "rerank_scores", _stub_rerank_scores)
    monkeypatch.setattr(retriever_module, "build_reranker", _stub_build_reranker)

    store = _StubKnowledgeStore(search_results=hits)
    config = KnowledgeConfig(
        retrieve=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=2,
            rerank=RerankConfig(
                mode="weighted",
                provider="sentence_transformers",
                params={"alpha": 0.0},
            ),
        )
    )
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c2", "c1"]


@pytest.mark.asyncio
async def test_rerank_normalize_minmax_outputs_normalized_scores(monkeypatch) -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
            ),
            score=0.3,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[0.0, 1.0],
            ),
            score=0.4,
            score_kind="distance",
        ),
    ]

    async def _stub_rerank_scores(*args, **kwargs) -> list[float]:
        return [1.0, 3.0]

    def _stub_build_reranker(*args, **kwargs):
        return object()

    import datapillar_oneagentic.knowledge.retriever.retriever as retriever_module

    monkeypatch.setattr(retriever_module, "rerank_scores", _stub_rerank_scores)
    monkeypatch.setattr(retriever_module, "build_reranker", _stub_build_reranker)

    store = _StubKnowledgeStore(search_results=hits)
    config = KnowledgeConfig(
        retrieve=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=2,
            rerank=RerankConfig(
                mode="model",
                provider="sentence_transformers",
                score_mode="normalize",
                normalize="min_max",
            ),
        )
    )
    retriever = KnowledgeRetriever(
        store=store, embedding_provider=_StubEmbeddingProvider(), config=config
    )
    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="demo", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c2", "c1"]
    assert result.hits[0][1] == pytest.approx(1.0)
