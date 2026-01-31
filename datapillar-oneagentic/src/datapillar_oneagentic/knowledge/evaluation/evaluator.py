# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge evaluation executor."""

from __future__ import annotations

from typing import Iterable

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig, KnowledgeConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.evaluation.metrics import (
    compute_coverage_overlap,
    compute_duplicate_ratio,
    compute_length_stats,
    compute_ranking_metrics,
)
from datapillar_oneagentic.knowledge.evaluation.schema import (
    ChunkingDocReport,
    ChunkingReport,
    ChunkingSummaryReport,
    EvalDocument,
    EvalSet,
    EvaluationReport,
    RetrievalQueryReport,
    RetrievalReport,
    RetrievalSummaryReport,
)
from datapillar_oneagentic.knowledge.ingest.builder import average_vectors, build_chunks, build_document
from datapillar_oneagentic.knowledge.models import (
    DocumentInput,
    Knowledge,
    KnowledgeChunk,
    KnowledgeSource,
    ParsedDocument,
    SparseEmbeddingProvider,
)
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore


class KnowledgeEvaluator:
    """Knowledge evaluation executor."""

    def __init__(
        self,
        *,
        store: KnowledgeStore,
        embedding_provider: EmbeddingProvider,
        chunk_config: KnowledgeChunkConfig,
        retrieve_config: KnowledgeRetrieveConfig,
        sparse_embedder: SparseEmbeddingProvider | None = None,
    ) -> None:
        self._store = store
        self._embedding_provider = embedding_provider
        self._chunk_config = chunk_config
        self._retrieve_config = retrieve_config
        self._sparse_embedder = sparse_embedder
        self._chunker = KnowledgeChunker(config=chunk_config)
        self._retriever = KnowledgeRetriever(
            store=store,
            embedding_provider=embedding_provider,
            retrieve_defaults=retrieve_config,
        )

    @classmethod
    async def from_config(
        cls,
        *,
        namespace: str | None = None,
        config: KnowledgeConfig,
        chunk_config: KnowledgeChunkConfig | None = None,
        retrieve_config: KnowledgeRetrieveConfig | None = None,
        sparse_embedder: SparseEmbeddingProvider | None = None,
    ) -> "KnowledgeEvaluator":
        if not namespace:
            raise ValueError("namespace is required for KnowledgeEvaluator.from_config")
        from datapillar_oneagentic.knowledge.runtime import build_runtime

        runtime = build_runtime(namespace=namespace, config=config)
        await runtime.initialize()
        return cls(
            store=runtime.store,
            embedding_provider=runtime.embedding_provider,
            chunk_config=chunk_config or KnowledgeChunkConfig(),
            retrieve_config=retrieve_config or KnowledgeRetrieveConfig(),
            sparse_embedder=sparse_embedder,
        )

    async def close(self) -> None:
        await self._store.close()

    async def evaluate(self, evalset: EvalSet) -> EvaluationReport:
        if evalset.k_values and max(evalset.k_values) > self._retrieve_config.top_k:
            raise ValueError("retrieve_config.top_k must be >= max k in evalset")
        chunking_report = self.evaluate_chunking(evalset)
        await self._ingest_eval_documents(evalset.documents)
        retrieval_reports = [
            await self._evaluate_retrieval(evalset, target="doc"),
            await self._evaluate_retrieval(evalset, target="chunk"),
        ]
        return EvaluationReport(
            evalset_id=evalset.evalset_id,
            chunking=chunking_report,
            retrieval=retrieval_reports,
            k_values=list(evalset.k_values),
        )

    def evaluate_chunking(self, evalset: EvalSet) -> ChunkingReport:
        doc_reports: list[ChunkingDocReport] = []
        for doc in evalset.documents:
            source_id = _eval_source_id(doc)
            parsed = _build_parsed_document(doc)
            preview = self._chunker.preview(parsed)
            contents = [chunk.content for chunk in preview.chunks]
            lengths = [len(content) for content in contents]
            coverage_ratio, overlap_ratio = compute_coverage_overlap(
                doc_length=len(doc.text),
                spans=[span for chunk in preview.chunks for span in chunk.source_spans],
            )
            doc_reports.append(
                ChunkingDocReport(
                    doc_id=doc.doc_id,
                    source_id=source_id,
                    chunk_count=len(preview.chunks),
                    length_stats=compute_length_stats(lengths),
                    duplicate_ratio=compute_duplicate_ratio(contents),
                    coverage_ratio=coverage_ratio,
                    overlap_ratio=overlap_ratio,
                )
            )

        summary = _build_chunking_summary(doc_reports)
        return ChunkingReport(summary=summary, documents=doc_reports)

    async def _evaluate_retrieval(self, evalset: EvalSet, *, target: str) -> RetrievalReport:
        knowledge = Knowledge(
            sources=_build_sources(evalset.documents, chunk_config=self._chunk_config),
            sparse_embedder=None if self._store.supports_hybrid else self._sparse_embedder,
        )
        query_reports: list[RetrievalQueryReport] = []

        for query in evalset.queries:
            result = await self._retriever.retrieve(query=query.query, knowledge=knowledge)
            retrieved_chunks = [chunk.chunk_id for chunk, _ in result.hits]
            retrieved_docs = _unique_in_order([chunk.doc_id for chunk, _ in result.hits])

            if target == "doc":
                relevant = set(query.expected_doc_ids or query.relevance_doc.keys())
                relevance = query.relevance_doc or None
                metrics = compute_ranking_metrics(
                    retrieved_ids=retrieved_docs,
                    relevant_ids=relevant,
                    relevance=relevance,
                    k_values=evalset.k_values,
                )
            elif target == "chunk":
                relevant = set(query.expected_chunk_ids or query.relevance_chunk.keys())
                relevance = query.relevance_chunk or None
                metrics = compute_ranking_metrics(
                    retrieved_ids=retrieved_chunks,
                    relevant_ids=relevant,
                    relevance=relevance,
                    k_values=evalset.k_values,
                )
            else:
                raise ValueError(f"Unsupported evaluation target: {target}")

            query_reports.append(RetrievalQueryReport(query_id=query.query_id, metrics=metrics))

        summary = _build_retrieval_summary(query_reports)
        return RetrievalReport(target=target, summary=summary, queries=query_reports)

    async def _ingest_eval_documents(self, documents: Iterable[EvalDocument]) -> None:
        sources = _build_sources(documents, chunk_config=self._chunk_config)
        sources_map = {source.source_id: source for source in sources}
        if sources:
            await self._store.upsert_sources(sources)

        all_docs = []
        all_chunks: list[KnowledgeChunk] = []
        for doc in documents:
            source_id = _eval_source_id(doc)
            source = sources_map[source_id]
            parsed = _build_parsed_document(doc)
            preview = self._chunker.preview(parsed)
            if not preview.chunks:
                continue
            doc_input = DocumentInput(
                source=doc.text,
                filename=doc.title or doc.doc_id,
                metadata=doc.metadata,
            )
            knowledge_doc = build_document(source=source, parsed=parsed, doc_input=doc_input)
            knowledge_chunks = build_chunks(source=source, doc=knowledge_doc, drafts=preview.chunks)

            vectors = await self._embedding_provider.embed_texts([chunk.content for chunk in knowledge_chunks])
            sparse_vectors = None
            use_sparse = self._sparse_embedder is not None and not self._store.supports_hybrid
            if use_sparse:
                sparse_vectors = await self._sparse_embedder.embed_texts(
                    [chunk.content for chunk in knowledge_chunks]
                )

            for idx, chunk in enumerate(knowledge_chunks):
                chunk.vector = vectors[idx]
                if sparse_vectors:
                    chunk.sparse_vector = sparse_vectors[idx]

            knowledge_doc.vector = average_vectors(vectors)
            all_docs.append(knowledge_doc)
            all_chunks.extend(knowledge_chunks)

        if all_docs:
            await self._store.upsert_docs(all_docs)
        if all_chunks:
            await self._store.upsert_chunks(all_chunks)


def _build_parsed_document(doc: EvalDocument) -> ParsedDocument:
    metadata = {"title": doc.title or doc.doc_id, **doc.metadata}
    return ParsedDocument(
        document_id=doc.doc_id,
        source_type="doc",
        mime_type="text/plain",
        text=doc.text,
        metadata=metadata,
    )


def _eval_source_id(doc: EvalDocument) -> str:
    return doc.doc_id


def _build_sources(
    documents: Iterable[EvalDocument],
    *,
    chunk_config: KnowledgeChunkConfig,
) -> list[KnowledgeSource]:
    sources: dict[str, KnowledgeSource] = {}
    for doc in documents:
        source_id = _eval_source_id(doc)
        if source_id not in sources:
            sources[source_id] = KnowledgeSource(
                source=doc.text,
                chunk=chunk_config,
                source_id=source_id,
                name=source_id,
                source_type="doc",
            )
    return list(sources.values())


def _unique_in_order(values: Iterable[str]) -> list[str]:
    seen = set()
    ordered: list[str] = []
    for item in values:
        if item in seen:
            continue
        seen.add(item)
        ordered.append(item)
    return ordered


def _build_chunking_summary(reports: list[ChunkingDocReport]) -> ChunkingSummaryReport:
    if not reports:
        return ChunkingSummaryReport(
            documents=0,
            avg_chunk_count=0.0,
            avg_chunk_length=0.0,
            avg_duplicate_ratio=0.0,
            avg_coverage_ratio=None,
            avg_overlap_ratio=None,
        )

    avg_chunk_count = sum(r.chunk_count for r in reports) / len(reports)
    avg_chunk_length = (
        sum(r.length_stats.mean for r in reports) / len(reports) if reports else 0.0
    )
    avg_duplicate_ratio = sum(r.duplicate_ratio for r in reports) / len(reports)

    coverage_values = [r.coverage_ratio for r in reports if r.coverage_ratio is not None]
    overlap_values = [r.overlap_ratio for r in reports if r.overlap_ratio is not None]
    avg_coverage = sum(coverage_values) / len(coverage_values) if coverage_values else None
    avg_overlap = sum(overlap_values) / len(overlap_values) if overlap_values else None

    return ChunkingSummaryReport(
        documents=len(reports),
        avg_chunk_count=avg_chunk_count,
        avg_chunk_length=avg_chunk_length,
        avg_duplicate_ratio=avg_duplicate_ratio,
        avg_coverage_ratio=avg_coverage,
        avg_overlap_ratio=avg_overlap,
    )


def _build_retrieval_summary(reports: list[RetrievalQueryReport]) -> RetrievalSummaryReport:
    metrics: dict[str, list[float]] = {}
    for report in reports:
        for key, value in report.metrics.items():
            metrics.setdefault(key, []).append(value)

    summary = {key: sum(values) / len(values) for key, values in metrics.items()} if metrics else {}
    return RetrievalSummaryReport(total_queries=len(reports), metrics=summary)
