"""Knowledge configuration."""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

from pydantic import BaseModel, Field


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


class KnowledgeChunkConfig(BaseModel):
    """Knowledge chunking configuration."""

    mode: str = Field(default="general", description="Chunking mode: general | parent_child | qa")
    preprocess: list[str] = Field(default_factory=list, description="Preprocessing rules")
    general: KnowledgeChunkGeneralConfig = Field(default_factory=KnowledgeChunkGeneralConfig)
    parent_child: KnowledgeChunkParentChildConfig = Field(default_factory=KnowledgeChunkParentChildConfig)
    qa: KnowledgeChunkQAConfig = Field(default_factory=KnowledgeChunkQAConfig)


class KnowledgeInjectConfig(BaseModel):
    """Knowledge injection configuration (defaults)."""

    mode: str = Field(default="tool", description="Injection mode: system | tool")
    max_tokens: int = Field(default=1200, ge=1, description="Max injected tokens (rough estimate)")
    max_chunks: int = Field(default=6, ge=1, description="Max injected chunks")
    format: str = Field(default="markdown", description="Injection format: markdown | json")


class RerankConfig(BaseModel):
    """Rerank configuration."""

    mode: str = Field(default="off", description="Rerank mode: off | model | weighted")
    provider: str | None = Field(default="sentence_transformers", description="Rerank provider")
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


class KnowledgeRetrieveConfig(BaseModel):
    """Knowledge retrieval configuration (defaults)."""

    method: str = Field(default="hybrid", description="Retrieval method: semantic | hybrid")
    top_k: int = Field(default=8, ge=1, description="Final result count")
    score_threshold: float | None = Field(default=None, description="Minimum score threshold")
    rerank: RerankConfig = Field(default_factory=RerankConfig)
    tuning: RetrieveTuningConfig = Field(default_factory=RetrieveTuningConfig)
    quality: RetrieveQualityConfig = Field(default_factory=RetrieveQualityConfig)
    inject: KnowledgeInjectConfig = Field(default_factory=KnowledgeInjectConfig)


class KnowledgeBaseConfig(BaseModel):
    """Knowledge base configuration (Embedding + VectorStore)."""

    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    vector_store: VectorStoreConfig = Field(default_factory=VectorStoreConfig)


class KnowledgeConfig(BaseModel):
    """Knowledge configuration."""

    base_config: KnowledgeBaseConfig = Field(default_factory=KnowledgeBaseConfig)
    chunk_config: KnowledgeChunkConfig = Field(default_factory=KnowledgeChunkConfig)
    retrieve_config: KnowledgeRetrieveConfig = Field(default_factory=KnowledgeRetrieveConfig)
