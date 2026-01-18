"""
知识评估数据结构
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, model_validator


class EvalDocument(BaseModel):
    """评估文档定义"""

    doc_id: str = Field(..., min_length=1, description="文档 ID")
    source_id: str = Field(..., min_length=1, description="数据源 ID")
    text: str = Field(..., min_length=1, description="原文内容")
    title: str | None = Field(default=None, description="文档标题")
    metadata: dict[str, Any] = Field(default_factory=dict, description="扩展元数据")


class EvalQuery(BaseModel):
    """评估查询定义"""

    query_id: str = Field(..., min_length=1, description="查询 ID")
    query: str = Field(..., min_length=1, description="查询文本")
    expected_doc_ids: list[str] = Field(default_factory=list, description="期望命中文档 ID")
    expected_chunk_ids: list[str] = Field(default_factory=list, description="期望命中分片 ID")
    relevance_doc: dict[str, int] = Field(default_factory=dict, description="文档级相关度标注")
    relevance_chunk: dict[str, int] = Field(default_factory=dict, description="分片级相关度标注")
    metadata: dict[str, Any] = Field(default_factory=dict, description="扩展元数据")


class EvalSet(BaseModel):
    """评估集定义"""

    evalset_id: str = Field(..., min_length=1, description="评估集 ID")
    documents: list[EvalDocument] = Field(default_factory=list, description="评估文档列表")
    queries: list[EvalQuery] = Field(default_factory=list, description="评估查询列表")
    k_values: list[int] = Field(default_factory=lambda: [1, 3, 5, 10], description="评估 K 值")

    @model_validator(mode="after")
    def _validate_evalset(self) -> "EvalSet":
        doc_ids = [doc.doc_id for doc in self.documents]
        if len(doc_ids) != len(set(doc_ids)):
            raise ValueError("评估集文档 doc_id 必须唯一")

        query_ids = [query.query_id for query in self.queries]
        if len(query_ids) != len(set(query_ids)):
            raise ValueError("评估集查询 query_id 必须唯一")

        for k in self.k_values:
            if k <= 0:
                raise ValueError("k_values 必须为正整数")

        doc_id_set = set(doc_ids)
        for query in self.queries:
            unknown = set(query.expected_doc_ids) - doc_id_set
            if unknown:
                raise ValueError(f"查询 {query.query_id} 引用了未知文档: {sorted(unknown)}")
            unknown_relevance = set(query.relevance_doc.keys()) - doc_id_set
            if unknown_relevance:
                raise ValueError(f"查询 {query.query_id} relevance_doc 引用了未知文档: {sorted(unknown_relevance)}")

        return self


class LengthStats(BaseModel):
    """长度统计"""

    count: int
    min: int
    max: int
    mean: float
    std: float


class ChunkingDocReport(BaseModel):
    """单文档切分报告"""

    doc_id: str
    source_id: str
    chunk_count: int
    length_stats: LengthStats
    duplicate_ratio: float
    coverage_ratio: float | None = None
    overlap_ratio: float | None = None


class ChunkingSummaryReport(BaseModel):
    """切分汇总报告"""

    documents: int
    avg_chunk_count: float
    avg_chunk_length: float
    avg_duplicate_ratio: float
    avg_coverage_ratio: float | None = None
    avg_overlap_ratio: float | None = None


class ChunkingReport(BaseModel):
    """切分评估报告"""

    summary: ChunkingSummaryReport
    documents: list[ChunkingDocReport]


class RetrievalQueryReport(BaseModel):
    """单查询检索报告"""

    query_id: str
    metrics: dict[str, float]


class RetrievalSummaryReport(BaseModel):
    """检索汇总报告"""

    total_queries: int
    metrics: dict[str, float]


class RetrievalReport(BaseModel):
    """检索评估报告"""

    target: str = Field(..., description="doc | chunk")
    summary: RetrievalSummaryReport
    queries: list[RetrievalQueryReport]


class EvaluationReport(BaseModel):
    """完整评估报告"""

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
