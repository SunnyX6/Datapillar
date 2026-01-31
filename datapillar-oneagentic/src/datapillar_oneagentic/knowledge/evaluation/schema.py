# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge evaluation data structures."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, model_validator


class EvalDocument(BaseModel):
    """Evaluation document definition."""

    doc_id: str = Field(..., min_length=1, description="Document ID")
    text: str = Field(..., min_length=1, description="Original text content")
    title: str | None = Field(default=None, description="Document title")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Extra metadata")


class EvalQuery(BaseModel):
    """Evaluation query definition."""

    query_id: str = Field(..., min_length=1, description="Query ID")
    query: str = Field(..., min_length=1, description="Query text")
    expected_doc_ids: list[str] = Field(default_factory=list, description="Expected document IDs")
    expected_chunk_ids: list[str] = Field(default_factory=list, description="Expected chunk IDs")
    relevance_doc: dict[str, int] = Field(default_factory=dict, description="Document-level relevance")
    relevance_chunk: dict[str, int] = Field(default_factory=dict, description="Chunk-level relevance")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Extra metadata")


class EvalSet(BaseModel):
    """Evaluation set definition."""

    evalset_id: str = Field(..., min_length=1, description="Eval set ID")
    documents: list[EvalDocument] = Field(default_factory=list, description="Documents")
    queries: list[EvalQuery] = Field(default_factory=list, description="Queries")
    k_values: list[int] = Field(default_factory=lambda: [1, 3, 5, 10], description="K values")

    @model_validator(mode="after")
    def _validate_evalset(self) -> "EvalSet":
        doc_ids = [doc.doc_id for doc in self.documents]
        if len(doc_ids) != len(set(doc_ids)):
            raise ValueError("Eval set doc_id must be unique")

        query_ids = [query.query_id for query in self.queries]
        if len(query_ids) != len(set(query_ids)):
            raise ValueError("Eval set query_id must be unique")

        for k in self.k_values:
            if k <= 0:
                raise ValueError("k_values must be positive integers")

        doc_id_set = set(doc_ids)
        for query in self.queries:
            unknown = set(query.expected_doc_ids) - doc_id_set
            if unknown:
                raise ValueError(f"Query {query.query_id} references unknown docs: {sorted(unknown)}")
            unknown_relevance = set(query.relevance_doc.keys()) - doc_id_set
            if unknown_relevance:
                raise ValueError(
                    f"Query {query.query_id} relevance_doc references unknown docs: {sorted(unknown_relevance)}"
                )

        return self


class LengthStats(BaseModel):
    """Length statistics."""

    count: int
    min: int
    max: int
    mean: float
    std: float


class ChunkingDocReport(BaseModel):
    """Per-document chunking report."""

    doc_id: str
    source_id: str
    chunk_count: int
    length_stats: LengthStats
    duplicate_ratio: float
    coverage_ratio: float | None = None
    overlap_ratio: float | None = None


class ChunkingSummaryReport(BaseModel):
    """Chunking summary report."""

    documents: int
    avg_chunk_count: float
    avg_chunk_length: float
    avg_duplicate_ratio: float
    avg_coverage_ratio: float | None = None
    avg_overlap_ratio: float | None = None


class ChunkingReport(BaseModel):
    """Chunking evaluation report."""

    summary: ChunkingSummaryReport
    documents: list[ChunkingDocReport]


class RetrievalQueryReport(BaseModel):
    """Per-query retrieval report."""

    query_id: str
    metrics: dict[str, float]


class RetrievalSummaryReport(BaseModel):
    """Retrieval summary report."""

    total_queries: int
    metrics: dict[str, float]


class RetrievalReport(BaseModel):
    """Retrieval evaluation report."""

    target: str = Field(..., description="doc | chunk")
    summary: RetrievalSummaryReport
    queries: list[RetrievalQueryReport]


class EvaluationReport(BaseModel):
    """Full evaluation report."""

    evalset_id: str
    chunking: ChunkingReport
    retrieval: list[RetrievalReport]
    k_values: list[int]

    def summary(self) -> dict[str, Any]:
        return {
            "evalset_id": self.evalset_id,
            "chunking": self.chunking.summary.model_dump(),
            "retrieval": {item.target: item.summary.metrics for item in self.retrieval},
            "k_values": list(self.k_values),
        }
