from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import (
    KnowledgeRetrieveConfig,
    QueryExpansionConfig,
    QueryRouterConfig,
)
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever
from datapillar_oneagentic.knowledge.retriever.query import QueryExpansionOutput, QueryRouteOutput


class _StubEmbeddingProvider:
    async def embed_text(self, text: str) -> list[float]:
        return [float(len(text)), 0.0]


class _StubKnowledgeStore:
    def __init__(self) -> None:
        self._namespace = "ns_stub"
        self.search_calls = 0
        self.hybrid_calls = 0
        self.query_texts: list[str] = []

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

    async def upsert_sources(self, sources):
        return None

    async def upsert_docs(self, docs):
        return None

    async def upsert_chunks(self, chunks):
        return None

    async def search_chunks(self, *, query_vector, k, filters=None):
        self.search_calls += 1
        return []

    async def hybrid_search_chunks(self, *, query_vector, query_text, k, filters=None, rrf_k=60):
        self.hybrid_calls += 1
        self.query_texts.append(query_text)
        return []

    async def full_text_search_chunks(self, *, query_text: str, k: int, filters=None):
        return []

    async def get_doc(self, doc_id):
        return None

    async def get_chunks(self, chunk_ids):
        return []

    async def delete_chunks(self, chunk_ids):
        return 0

    async def query_chunks(self, *, filters=None, limit=None):
        return []

    async def delete_doc(self, doc_id):
        return 0

    async def delete_doc_chunks(self, doc_id):
        return 0


class _StubStructuredLLM:
    def __init__(self, output) -> None:
        self._output = output

    async def ainvoke(self, _messages):
        return self._output


class _StubLLM:
    def __init__(self, output) -> None:
        self._output = output

    def with_structured_output(self, _schema, **_kwargs):
        return _StubStructuredLLM(self._output)


def _llm_provider(output):
    return lambda: _StubLLM(output)


@pytest.mark.asyncio
async def test_query_router_can_skip_retrieval() -> None:
    store = _StubKnowledgeStore()
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="semantic",
        routing=QueryRouterConfig(
            mode="auto",
            use_llm=True,
            allow_no_rag=True,
            min_confidence=0.0,
        ),
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )

    output = QueryRouteOutput(use_rag=False, method="semantic", rerank=False, confidence=0.9)
    result = await retriever.retrieve(
        query="hello",
        llm_provider=_llm_provider(output),
    )

    assert result.hits == []
    assert store.search_calls == 0
    assert store.hybrid_calls == 0


@pytest.mark.asyncio
async def test_query_expansion_multi_query() -> None:
    store = _StubKnowledgeStore()
    retrieve_defaults = KnowledgeRetrieveConfig(
        method="hybrid",
        expansion=QueryExpansionConfig(
            mode="multi",
            use_llm=True,
            max_queries=2,
            include_original=True,
        ),
    )
    retriever = KnowledgeRetriever(
        store=store,
        embedding_provider=_StubEmbeddingProvider(),
        retrieve_defaults=retrieve_defaults,
    )

    output = QueryExpansionOutput(queries=["alpha", "beta", "alpha"])
    await retriever.retrieve(
        query="original",
        llm_provider=_llm_provider(output),
    )

    assert store.query_texts == ["original", "alpha", "beta"]
