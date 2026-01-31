# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge configuration."""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

from pydantic import BaseModel, Field, model_validator


class KnowledgeChunkGeneralConfig(BaseModel):
    """General chunking parameters."""

    delimiter: str | None = Field(default=None, description="Fixed delimiter (optional)")
    max_tokens: int = Field(default=800, gt=0, description="Chunk size (approx tokens/characters)")
    overlap: int = Field(default=120, ge=0, description="Chunk overlap (approx tokens/characters)")


class KnowledgeChunkParentChildConfig(BaseModel):
    """Parent-child chunking parameters."""

    parent: KnowledgeChunkGeneralConfig = Field(default_factory=KnowledgeChunkGeneralConfig)
    child: KnowledgeChunkGeneralConfig = Field(
        default_factory=lambda: KnowledgeChunkGeneralConfig(max_tokens=200, overlap=40)
    )


class KnowledgeChunkQAConfig(BaseModel):
    """QA chunking parameters."""

    pattern: str = Field(
        default=r"Q\d+:\s*(.*?)\s*A\d+:\s*([\s\S]*?)(?=Q\d+:|$)",
        description="QA regex chunking pattern",
    )


class KnowledgeWindowConfig(BaseModel):
    """Chunk window metadata configuration."""

    enabled: bool = Field(default=False, description="Enable window metadata on chunks")
    radius: int = Field(default=1, ge=0, description="Neighbor radius to store")
    scope: str = Field(default="auto", description="Window scope: auto | doc | parent")


class KnowledgeChunkConfig(BaseModel):
    """Knowledge chunking configuration."""

    mode: str = Field(default="general", description="Chunking mode: general | parent_child | qa")
    preprocess: list[str] = Field(default_factory=list, description="Preprocessing rules")
    general: KnowledgeChunkGeneralConfig = Field(default_factory=KnowledgeChunkGeneralConfig)
    parent_child: KnowledgeChunkParentChildConfig = Field(default_factory=KnowledgeChunkParentChildConfig)
    qa: KnowledgeChunkQAConfig = Field(default_factory=KnowledgeChunkQAConfig)
    window: KnowledgeWindowConfig = Field(default_factory=KnowledgeWindowConfig)


class RerankConfig(BaseModel):
    """Rerank configuration."""

    mode: str = Field(default="off", description="Rerank mode: off | model | weighted")
    provider: str | None = Field(
        default="sentence_transformers",
        description="Rerank provider: sentence_transformers | milvus",
    )
    model: str | None = Field(
        default="cross-encoder/ms-marco-MiniLM-L-6-v2", description="Rerank model"
    )
    top_n: int | None = Field(default=None, description="Rerank candidate count")
    score_threshold: float | None = Field(default=None, description="Rerank score threshold")
    score_mode: str = Field(default="rank", description="Score mode: rank | normalize | raw")
    normalize: str | None = Field(
        default=None, description="Normalization: min_max | sigmoid | softmax | zscore"
    )
    params: dict[str, Any] = Field(default_factory=dict, description="Extra parameters passthrough")


class RetrieveTuningConfig(BaseModel):
    """Retrieval tuning."""

    pool_k: int | None = Field(default=None, description="Recall candidate pool size")
    rerank_k: int | None = Field(default=None, description="Rerank candidate pool size")
    rrf_k: int = Field(default=60, ge=1, description="RRF fusion parameter")


class RetrieveQualityConfig(BaseModel):
    """Evidence quality settings."""

    dedupe: bool = Field(default=True, description="Enable deduplication")
    dedupe_threshold: float = Field(default=0.92, ge=0, le=1, description="Semantic dedupe threshold")
    max_per_document: int = Field(default=2, ge=1, description="Max chunks per document")


class MetadataFilterConfig(BaseModel):
    """Automatic metadata filtering."""

    mode: str = Field(default="auto", description="Filtering mode: off | auto")
    min_confidence: float = Field(default=0.6, ge=0, le=1, description="Minimum confidence to apply")
    fields: dict[str, list[str]] = Field(
        default_factory=dict,
        description="Field -> candidate values/aliases for rule-based matching",
    )
    use_llm: bool = Field(default=False, description="Use LLM fallback when rules are insufficient")


class QueryRouterConfig(BaseModel):
    """Query routing configuration."""

    mode: str = Field(default="off", description="Routing mode: off | auto")
    allow_no_rag: bool = Field(default=False, description="Allow skipping retrieval when routing decides")
    use_llm: bool = Field(default=False, description="Use LLM for routing decisions")
    min_confidence: float = Field(default=0.6, ge=0, le=1, description="Minimum confidence to apply")


class QueryExpansionConfig(BaseModel):
    """Query expansion configuration."""

    mode: str = Field(default="off", description="Expansion mode: off | multi | hyde")
    max_queries: int = Field(default=3, ge=1, description="Maximum expanded queries (excluding original)")
    include_original: bool = Field(default=True, description="Include original query in expansion")
    per_query_k: int | None = Field(default=None, ge=1, description="Override per-query pool size")
    use_llm: bool = Field(default=False, description="Use LLM for expansion")


class ContextResolveConfig(BaseModel):
    """Context resolution configuration."""

    mode: str = Field(
        default="parent",
        description="Context mode: off | parent | window | parent_window",
    )
    window_radius: int = Field(default=1, ge=0, description="Neighbor radius for window mode")


class KnowledgeRetrieveConfig(BaseModel):
    """Knowledge retrieval configuration (defaults)."""

    @model_validator(mode="before")
    @classmethod
    def _reject_removed_fields(cls, data):
        if isinstance(data, dict) and "hierarchical" in data:
            raise ValueError("Hierarchical summary retrieval has been removed from retrieval configuration.")
        return data

    method: str = Field(
        default="hybrid",
        description=(
            "Retrieval method: semantic | hybrid | full_text."
        ),
    )
    top_k: int = Field(default=8, ge=1, description="Final result count")
    score_threshold: float | None = Field(default=None, description="Minimum score threshold")
    params: dict[str, Any] = Field(default_factory=dict, description="Backend passthrough parameters")
    rerank: RerankConfig = Field(default_factory=RerankConfig)
    tuning: RetrieveTuningConfig = Field(default_factory=RetrieveTuningConfig)
    quality: RetrieveQualityConfig = Field(default_factory=RetrieveQualityConfig)
    filtering: MetadataFilterConfig = Field(default_factory=MetadataFilterConfig)
    routing: QueryRouterConfig = Field(default_factory=QueryRouterConfig)
    expansion: QueryExpansionConfig = Field(default_factory=QueryExpansionConfig)
    context: ContextResolveConfig = Field(default_factory=ContextResolveConfig)


class KnowledgeConfig(BaseModel):
    """Knowledge configuration (store + retrieve defaults)."""

    namespaces: list[str] | None = Field(
        default=None,
        description="Optional namespace whitelist for tool binding",
    )
    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    vector_store: VectorStoreConfig = Field(default_factory=VectorStoreConfig)
    retrieve: KnowledgeRetrieveConfig = Field(default_factory=KnowledgeRetrieveConfig)

    @model_validator(mode="after")
    def _validate_namespaces(self) -> "KnowledgeConfig":
        if self.namespaces is None:
            return self
        cleaned: list[str] = []
        seen: set[str] = set()
        for item in self.namespaces:
            value = (item or "").strip()
            if not value or value in seen:
                continue
            seen.add(value)
            cleaned.append(value)
        if not cleaned:
            raise ValueError("KnowledgeConfig.namespaces cannot be empty")
        self.namespaces = cleaned
        return self


class KnowledgeVectorDB(BaseModel):
    """Deprecated: use KnowledgeConfig and tool retrieve/filters instead."""

    store: VectorStoreConfig = Field(
        description="Vector store configuration (type/uri/path/params)",
    )
    retrieve: dict[str, Any] = Field(
        default_factory=dict,
        description="Dynamic retrieve options (method/top_k/params/expr/param/filters)",
    )
