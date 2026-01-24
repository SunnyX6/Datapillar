from __future__ import annotations

import json

import pytest

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.knowledge.config import (
    KnowledgeConfig,
    KnowledgeChunkConfig,
    KnowledgeInjectConfig,
    KnowledgeRetrieveConfig,
    RerankConfig,
)
from datapillar_oneagentic.knowledge.ingest.pipeline import KnowledgeIngestor
from datapillar_oneagentic.knowledge.models import (
    DocumentInput,
    Knowledge,
    KnowledgeChunk,
    KnowledgeDocument,
    KnowledgeInject,
    KnowledgeRetrieve,
    KnowledgeSearchHit,
    KnowledgeSource,
    merge_knowledge,
)
from datapillar_oneagentic.knowledge.retriever.evidence import dedupe_hits, group_hits
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever


class _StubEmbeddingProvider:
    def __init__(self) -> None:
        self.texts: list[str] = []

    async def embed_text(self, text: str) -> list[float]:
        self.texts.append(text)
        return [float(len(text)), 0.0]

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        self.texts.extend(texts)
        return [[float(len(text)), 0.0] for text in texts]


class _StubSparseEmbedder:
    async def embed_text(self, text: str) -> dict[int, float]:
        return {len(text): 1.0}

    async def embed_texts(self, texts: list[str]) -> list[dict[int, float]]:
        return [{len(text): 1.0} for text in texts]


class _StubKnowledgeStore:
    def __init__(self, *, search_results: list[KnowledgeSearchHit] | None = None) -> None:
        self._namespace = "ns_stub"
        self.sources: list[KnowledgeSource] = []
        self.docs: list[KnowledgeDocument] = []
        self.chunks: list[KnowledgeChunk] = []
        self.search_results = list(search_results or [])
        self.last_search: tuple[list[float], int, dict | None] | None = None

    @property
    def namespace(self) -> str:
        return self._namespace

    async def initialize(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        self.sources.extend(sources)

    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        self.docs.extend(docs)

    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        self.chunks.extend(chunks)

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict | None = None,
    ) -> list[KnowledgeSearchHit]:
        self.last_search = (list(query_vector), k, filters)
        return list(self.search_results[:k])

    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        return next((doc for doc in self.docs if doc.doc_id == doc_id), None)

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        return [chunk for chunk in self.chunks if chunk.chunk_id in set(chunk_ids)]

    async def delete_doc(self, doc_id: str) -> int:
        before = len(self.docs)
        self.docs = [doc for doc in self.docs if doc.doc_id != doc_id]
        return before - len(self.docs)

    async def delete_chunks_by_doc_id(self, doc_id: str) -> int:
        before = len(self.chunks)
        self.chunks = [chunk for chunk in self.chunks if chunk.doc_id != doc_id]
        return before - len(self.chunks)


@pytest.mark.asyncio
async def test_knowledge_ingestor_writes_chunks_and_sparse_vectors() -> None:
    store = _StubKnowledgeStore()
    embedder = _StubEmbeddingProvider()
    sparse = _StubSparseEmbedder()
    config = KnowledgeChunkConfig(
        mode="general",
        general={"max_tokens": 4, "overlap": 1},
    )

    ingestor = KnowledgeIngestor(store=store, embedding_provider=embedder, config=config)
    source = KnowledgeSource(source_id="src1", name="示例", source_type="doc")
    doc = DocumentInput(source="abcdef", filename="doc1.txt")

    await ingestor.ingest(source=source, documents=[doc], sparse_embedder=sparse)

    assert len(store.sources) == 1
    assert len(store.docs) == 1
    assert len(store.chunks) == 2
    assert store.chunks[0].sparse_vector is not None


@pytest.mark.asyncio
async def test_knowledge_retriever_requires_sparse_embedder_for_hybrid() -> None:
    store = _StubKnowledgeStore()
    embedder = _StubEmbeddingProvider()
    retriever = KnowledgeRetriever(store=store, embedding_provider=embedder, config=KnowledgeConfig())

    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="src1", name="示例", source_type="doc")],
        sparse_embedder=None,
    )

    with pytest.raises(ValueError):
        await retriever.retrieve(query="query", knowledge=knowledge)


@pytest.mark.asyncio
async def test_knowledge_retriever_builds_json_context_and_refs() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="src1",
                doc_title="标题2",
                content="内容2",
                vector=[1.0, 0.0],
                sparse_vector={1: 1.0},
                token_count=3,
                chunk_index=1,
            ),
            score=0.2,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="src1",
                doc_title="标题1",
                content="内容1",
                vector=[1.0, 0.0],
                sparse_vector={1: 1.0},
                token_count=3,
                chunk_index=0,
            ),
            score=0.1,
            score_kind="distance",
        ),
    ]

    store = _StubKnowledgeStore(search_results=hits)
    embedder = _StubEmbeddingProvider()
    config = KnowledgeConfig(
        retrieve_config=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=2,
            inject=KnowledgeInjectConfig(mode="system", max_tokens=200, max_chunks=1, format="json"),
        ),
    )
    retriever = KnowledgeRetriever(store=store, embedding_provider=embedder, config=config)

    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="src1", name="示例", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)
    context = ContextBuilder.build_knowledge_context(
        chunks=[chunk for chunk, _ in result.hits],
        inject=config.retrieve_config.inject,
    )
    payload = json.loads(context)

    assert len(result.refs) == 1
    assert payload["chunks"][0]["chunk_id"] == "c1"


@pytest.mark.asyncio
async def test_knowledge_retriever_allows_tool_mode() -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="src1",
                doc_title="标题1",
                content="内容1",
                vector=[1.0, 0.0],
                sparse_vector={1: 1.0},
                token_count=3,
                chunk_index=0,
            ),
            score=0.1,
            score_kind="distance",
        )
    ]

    store = _StubKnowledgeStore(search_results=hits)
    embedder = _StubEmbeddingProvider()
    config = KnowledgeConfig(
        retrieve_config=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=1,
            inject=KnowledgeInjectConfig(mode="tool", max_tokens=200, max_chunks=1, format="markdown"),
        ),
    )
    retriever = KnowledgeRetriever(store=store, embedding_provider=embedder, config=config)

    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="src1", name="示例", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)
    context = ContextBuilder.build_knowledge_context(
        chunks=[chunk for chunk, _ in result.hits],
        inject=config.retrieve_config.inject,
    )
    assert "Knowledge Context" in context


def test_merge_knowledge_prefers_agent_over_team() -> None:
    team = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="团队库", source_type="doc")],
        retrieve=KnowledgeRetrieve(
            top_k=5,
            rerank={"mode": "model", "model": "m1"},
            inject=KnowledgeInject(mode="system"),
        ),
        inject=KnowledgeInject(max_tokens=100),
        sparse_embedder=_StubSparseEmbedder(),
    )
    agent = Knowledge(
        sources=[
            KnowledgeSource(source_id="s1", name="Agent库", source_type="doc"),
            KnowledgeSource(source_id="s2", name="新增库", source_type="doc"),
        ],
        retrieve=KnowledgeRetrieve(
            top_k=3,
            rerank={"model": "m2"},
            inject=KnowledgeInject(max_chunks=2),
        ),
        inject=KnowledgeInject(format="json"),
    )

    merged = merge_knowledge(team, agent)

    assert merged is not None
    assert [source.source_id for source in merged.sources] == ["s1", "s2"]
    assert merged.sources[0].name == "Agent库"
    assert merged.retrieve is not None
    assert merged.retrieve.top_k == 3
    assert merged.retrieve.rerank is not None
    assert merged.retrieve.rerank["mode"] == "model"
    assert merged.retrieve.rerank["model"] == "m2"
    assert merged.retrieve.inject is not None
    assert merged.retrieve.inject.mode == "system"
    assert merged.retrieve.inject.max_chunks == 2
    assert merged.inject is not None
    assert merged.inject.max_tokens == 100
    assert merged.inject.format == "json"
    assert merged.sparse_embedder is team.sparse_embedder


def test_merge_knowledge_allows_sparse_embedder_override() -> None:
    team = Knowledge(
        sources=[KnowledgeSource(source_id="s1", name="团队库", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )
    override_embedder = _StubSparseEmbedder()
    agent = Knowledge(
        sources=[],
        sparse_embedder=override_embedder,
    )

    merged = merge_knowledge(team, agent)

    assert merged is not None
    assert merged.sparse_embedder is override_embedder


def test_group_hits_limits_per_document() -> None:
    hits = [
        (
            KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="a",
            ),
            0.9,
        ),
        (
            KnowledgeChunk(
                chunk_id="c2",
                doc_id="d1",
                source_id="s1",
                content="b",
            ),
            0.7,
        ),
        (
            KnowledgeChunk(
                chunk_id="c3",
                doc_id="d1",
                source_id="s1",
                parent_id="p1",
                content="c",
            ),
            0.8,
        ),
        (
            KnowledgeChunk(
                chunk_id="c4",
                doc_id="d1",
                source_id="s1",
                parent_id="p1",
                content="d",
            ),
            0.6,
        ),
        (
            KnowledgeChunk(
                chunk_id="c5",
                doc_id="d2",
                source_id="s1",
                content="e",
            ),
            0.95,
        ),
    ]

    grouped = group_hits(hits, max_per_document=1)

    assert [chunk.chunk_id for chunk, _ in grouped] == ["c5", "c1", "c3"]


def test_dedupe_hits_removes_exact_duplicates() -> None:
    hits = [
        (
            KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="same",
            ),
            0.9,
        ),
        (
            KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="same",
            ),
            0.8,
        ),
    ]

    deduped = dedupe_hits(hits, threshold=None)

    assert [chunk.chunk_id for chunk, _ in deduped] == ["c1"]


def test_dedupe_hits_removes_semantic_duplicates() -> None:
    hits = [
        (
            KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="s1",
                content="alpha",
                vector=[1.0, 0.0],
            ),
            0.9,
        ),
        (
            KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="s1",
                content="beta",
                vector=[1.0, 0.0],
            ),
            0.8,
        ),
    ]

    deduped = dedupe_hits(hits, threshold=0.95)

    assert [chunk.chunk_id for chunk, _ in deduped] == ["c1"]


@pytest.mark.asyncio
async def test_knowledge_retriever_applies_rerank(monkeypatch) -> None:
    hits = [
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c1",
                doc_id="d1",
                source_id="src1",
                content="内容1",
                vector=[1.0, 0.0],
            ),
            score=0.1,
            score_kind="distance",
        ),
        KnowledgeSearchHit(
            chunk=KnowledgeChunk(
                chunk_id="c2",
                doc_id="d2",
                source_id="src1",
                content="内容2",
                vector=[0.0, 1.0],
            ),
            score=0.5,
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
    embedder = _StubEmbeddingProvider()
    config = KnowledgeConfig(
        retrieve_config=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=2,
            rerank=RerankConfig(mode="model", provider="sentence_transformers"),
        )
    )
    retriever = KnowledgeRetriever(store=store, embedding_provider=embedder, config=config)

    knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="src1", name="示例", source_type="doc")],
        sparse_embedder=_StubSparseEmbedder(),
    )

    result = await retriever.retrieve(query="query", knowledge=knowledge)

    assert [chunk.chunk_id for chunk, _ in result.hits] == ["c2", "c1"]
