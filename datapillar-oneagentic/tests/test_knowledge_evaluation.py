from __future__ import annotations

import json

import pytest

from datapillar_oneagentic.knowledge.chunker.cleaner import apply_preprocess
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.evaluation import EvalDocument, EvalQuery, EvalSet, KnowledgeEvaluator, load_eval_set
from datapillar_oneagentic.knowledge.identity import build_doc_id
from datapillar_oneagentic.knowledge.models import KnowledgeChunk, KnowledgeDocument, KnowledgeSearchHit, KnowledgeSource
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore


class _StubEmbeddingProvider:
    async def embed_text(self, text: str) -> list[float]:
        return self._embed(text)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    @staticmethod
    def _embed(text: str) -> list[float]:
        return [
            1.0 if "alpha" in text else 0.0,
            1.0 if "beta" in text else 0.0,
        ]


class _InMemoryKnowledgeStore(KnowledgeStore):
    def __init__(self) -> None:
        self._namespace = "ns_eval"
        self.sources: dict[str, KnowledgeSource] = {}
        self.docs: dict[str, KnowledgeDocument] = {}
        self.chunks: dict[str, KnowledgeChunk] = {}

    @property
    def namespace(self) -> str:
        return self._namespace

    async def initialize(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        for source in sources:
            self.sources[source.source_id] = source

    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        for doc in docs:
            self.docs[doc.doc_id] = doc

    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        for chunk in chunks:
            self.chunks[chunk.chunk_id] = chunk

    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict | None = None,
    ) -> list[KnowledgeSearchHit]:
        hits: list[KnowledgeSearchHit] = []
        for chunk in self.chunks.values():
            if filters and filters.get("source_id") and chunk.source_id != filters["source_id"]:
                continue
            score = sum(a * b for a, b in zip(query_vector, chunk.vector))
            hits.append(KnowledgeSearchHit(chunk=chunk, score=score, score_kind="similarity"))
        hits.sort(key=lambda item: item.score, reverse=True)
        return hits[:k]

    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        return self.docs.get(doc_id)

    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        return [self.chunks[chunk_id] for chunk_id in chunk_ids if chunk_id in self.chunks]

    async def delete_doc(self, doc_id: str) -> int:
        if doc_id in self.docs:
            del self.docs[doc_id]
            return 1
        return 0

    async def delete_chunks_by_doc_id(self, doc_id: str) -> int:
        to_delete = [key for key, chunk in self.chunks.items() if chunk.doc_id == doc_id]
        for key in to_delete:
            del self.chunks[key]
        return len(to_delete)


def _build_evalset() -> EvalSet:
    doc1_text = "alpha alpha"
    doc2_text = "beta beta"
    doc1_id = build_doc_id(apply_preprocess(doc1_text, []))
    doc2_id = build_doc_id(apply_preprocess(doc2_text, []))
    return EvalSet(
        evalset_id="demo_eval",
        documents=[
            EvalDocument(doc_id=doc1_id, text=doc1_text),
            EvalDocument(doc_id=doc2_id, text=doc2_text),
        ],
        queries=[
            EvalQuery(
                query_id="q1",
                query="alpha",
                expected_doc_ids=[doc1_id],
                expected_chunk_ids=[f"{doc1_id}:0"],
            ),
            EvalQuery(
                query_id="q2",
                query="beta",
                expected_doc_ids=[doc2_id],
                expected_chunk_ids=[f"{doc2_id}:0"],
            ),
        ],
        k_values=[1, 3],
    )


@pytest.mark.asyncio
async def test_knowledge_evaluator_reports_metrics() -> None:
    store = _InMemoryKnowledgeStore()
    embedder = _StubEmbeddingProvider()
    chunk_config = KnowledgeChunkConfig(mode="general", general={"max_tokens": 200, "overlap": 0})
    retrieve_config = KnowledgeRetrieveConfig(method="semantic", top_k=5)
    evaluator = KnowledgeEvaluator(
        store=store,
        embedding_provider=embedder,
        chunk_config=chunk_config,
        retrieve_config=retrieve_config,
    )

    report = await evaluator.evaluate(_build_evalset())

    assert report.chunking.summary.documents == 2
    assert report.chunking.summary.avg_duplicate_ratio == 0.0
    assert report.chunking.documents[0].coverage_ratio == pytest.approx(1.0)

    doc_report = next(item for item in report.retrieval if item.target == "doc")
    chunk_report = next(item for item in report.retrieval if item.target == "chunk")
    assert doc_report.summary.metrics["hit@1"] == 1.0
    assert doc_report.summary.metrics["recall@1"] == 1.0
    assert chunk_report.summary.metrics["hit@1"] == 1.0
    assert chunk_report.summary.metrics["recall@1"] == 1.0


def test_load_eval_set_roundtrip(tmp_path) -> None:
    evalset = _build_evalset()
    path = tmp_path / "eval.json"
    path.write_text(json.dumps(evalset.model_dump(), ensure_ascii=False), encoding="utf-8")
    loaded = load_eval_set(path)
    assert loaded.evalset_id == evalset.evalset_id
