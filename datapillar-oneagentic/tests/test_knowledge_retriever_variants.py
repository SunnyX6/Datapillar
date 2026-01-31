from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import KnowledgeRetrieveConfig, RerankConfig
from datapillar_oneagentic.knowledge.models import (
    Knowledge,
    KnowledgeChunk,
    KnowledgeSearchHit,
    KnowledgeSource,
)
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever

DEFAULT_CHUNK = {"mode": "general"}


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
        self._namespace = "ns_stub"
        self.search_results = list(search_results)
        self.last_filters: dict | None = None

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    def supports_hybrid(self) -> bool:
        return True

    @property
    def supports_full_text(self) -> bool:
        return False

    async def initialize(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict | None = None,
    ) -> list[KnowledgeSearchHit]:
        self.last_filters = filters
        return list(self.search_results[:k])

    async def hybrid_search_chunks(
        self,
        *,
        query_vector: list[float],
        query_text: str,
        k: int,
        filters: dict | None = None,
        rrf_k: int = 60,
    ) -> list[KnowledgeSearchHit]:
        self.last_filters = filters
        return list(self.search_results[:k])

    async def full_text_search_chunks(self, *, query_text: str, k: int, filters: dict | None = None):
        return []

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        return []

    async def get_doc(self, doc_id: str):
        return None

    async def delete_doc(self, doc_id: str) -> int:
        return 0

    async def delete_doc_chunks(self, doc_id: str) -> int:
        return 0


@pytest.mark.asyncio
async def test_score_threshold() -> None:
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
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="semantic",
        top_k=5,
        score_threshold=0.5,
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c1"]


class _NoEmbedProvider:
    async def embed_text(self, _text: str) -> list[float]:
        raise AssertionError("full_text should not call embed_text")


class _FullTextStore:
    def __init__(self, hits: list[KnowledgeSearchHit]) -> None:
        self._namespace = "ns_full_text"
        self._hits = list(hits)
        self.query_texts: list[str] = []

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    def supports_hybrid(self) -> bool:
        return False

    @property
    def supports_full_text(self) -> bool:
        return True

    async def initialize(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def search_chunks(self, *, query_vector, k, filters=None):
        raise AssertionError("full_text should not call search_chunks")

    async def hybrid_search_chunks(self, *, query_vector, query_text, k, filters=None, rrf_k=60):
        raise AssertionError("full_text should not call hybrid_search_chunks")

    async def full_text_search_chunks(self, *, query_text: str, k: int, filters=None):
        self.query_texts.append(query_text)
        return list(self._hits[:k])

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        return []

    async def get_doc(self, doc_id: str):
        return None

    async def delete_doc(self, doc_id: str) -> int:
        return 0

    async def delete_doc_chunks(self, doc_id: str) -> int:
        return 0


class _NoFullTextStore(_FullTextStore):
    @property
    def supports_full_text(self) -> bool:
        return False


@pytest.mark.asyncio
async def test_full_text_retrieval_uses_store() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="ft1",
                doc_id="d1",
                source_id="s1",
                content="full text",
                vector=[1.0, 0.0],
            ),
            score=0.9,
            score_kind="similarity",
        )
    ]
    store = _FullTextStore(hits)
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_NoEmbedProvider(),
        retrieve_defaults=KnowledgeRetrieveConfig(method="full_text", top_k=5),
    )

    result = await retriever.retrieve(query="test query")

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["ft1"]
    assert store.query_texts == ["test query"]


@pytest.mark.asyncio
async def test_full_text_requires_support() -> None:
    hits = []
    store = _NoFullTextStore(hits)
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=KnowledgeRetrieveConfig(method="full_text"),
    )

    with pytest.raises(ValueError, match="Full-text retrieval is not supported"):
        await retriever.retrieve(query="test query")


@pytest.mark.asyncio
async def test_rank_sparse() -> None:
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
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="hybrid",
        top_k=3,
        tuning={"rrf_k": 1},
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits][0] == "c2"


@pytest.mark.asyncio
async def test_retriever_filters() -> None:
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
    retrieve_defaults = KnowledgeRetrieveConfig(method="semantic", top_k=2)
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(
        query="query",
        knowledge=knowledge,
        filters={"doc_id": "d1"},
    )

    assert store.last_filters == {"doc_id": "d1"}


@pytest.mark.asyncio
async def test_retriever_scope() -> None:
    store = _StubKnowledgeStore(search_results=[])
    retrieve_defaults = KnowledgeRetrieveConfig(method="semantic")
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    with pytest.raises(ValueError):
        await retriever.retrieve(
            query="query",
            knowledge=knowledge,
            search_params={"ef": 10},
        )


@pytest.mark.asyncio
async def test_retriever_scope2() -> None:
    store = _StubKnowledgeStore(search_results=[])
    retrieve_defaults = KnowledgeRetrieveConfig(method="semantic")
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    with pytest.raises(ValueError):
        await retriever.retrieve(
            query="query",
            knowledge=knowledge,
            search_params={"nprobe": 32},
        )


@pytest.mark.asyncio
async def test_rerank_weighted(monkeypatch) -> None:
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
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="semantic",
        top_k=2,
        rerank=RerankConfig(
            mode="weighted",
            provider="sentence_transformers",
            params={"alpha": 0.0},
        ),
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c2", "c1"]


@pytest.mark.asyncio
async def test_normalize_minmax(monkeypatch) -> None:
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
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="semantic",
        top_k=2,
        rerank=RerankConfig(
            mode="model",
            provider="sentence_transformers",
            score_mode="normalize",
            normalize="min_max",
        ),
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )
    knowledge = Knowledge(
        sources=[
            KnowledgeSource(source="stub", chunk=DEFAULT_CHUNK, source_id="s1", name="demo", source_type="doc")
        ],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c2", "c1"]
    assert result.hits[0][1] == pytest.approx(1.0)
