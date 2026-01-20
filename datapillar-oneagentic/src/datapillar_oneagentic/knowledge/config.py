"""
Knowledge 配置
"""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

from pydantic import BaseModel, Field


class KnowledgeChunkGeneralConfig(BaseModel):
    """通用切分参数"""

    delimiter: str | None = Field(default=None, description="固定分隔符（可选）")
    max_tokens: int = Field(default=800, gt=0, description="切分大小（近似 token/字符）")
    overlap: int = Field(default=120, ge=0, description="切分重叠（近似 token/字符）")


class KnowledgeChunkParentChildConfig(BaseModel):
    """父子切分参数"""

    parent: KnowledgeChunkGeneralConfig = Field(default_factory=KnowledgeChunkGeneralConfig)
    child: KnowledgeChunkGeneralConfig = Field(
        default_factory=lambda: KnowledgeChunkGeneralConfig(max_tokens=200, overlap=40)
    )


class KnowledgeChunkQAConfig(BaseModel):
    """QA 切分参数"""

    pattern: str = Field(
        default=r"Q\d+:\s*(.*?)\s*A\d+:\s*([\s\S]*?)(?=Q\d+:|$)",
        description="QA 正则切分规则",
    )


class KnowledgeChunkConfig(BaseModel):
    """知识切分配置"""

    mode: str = Field(default="general", description="切分模式: general | parent_child | qa")
    preprocess: list[str] = Field(default_factory=list, description="预处理规则")
    general: KnowledgeChunkGeneralConfig = Field(default_factory=KnowledgeChunkGeneralConfig)
    parent_child: KnowledgeChunkParentChildConfig = Field(default_factory=KnowledgeChunkParentChildConfig)
    qa: KnowledgeChunkQAConfig = Field(default_factory=KnowledgeChunkQAConfig)


class KnowledgeInjectConfig(BaseModel):
    """知识注入配置（默认值）"""

    mode: str = Field(default="tool", description="注入方式: system | tool")
    max_tokens: int = Field(default=1200, ge=1, description="最大注入 token 数（粗略估算）")
    max_chunks: int = Field(default=6, ge=1, description="最大注入片段数")
    format: str = Field(default="markdown", description="注入格式: markdown | json")


class RerankConfig(BaseModel):
    """重排配置"""

    mode: str = Field(default="off", description="重排模式: off | model | weighted")
    provider: str | None = Field(default="sentence_transformers", description="重排提供商")
    model: str | None = Field(
        default="cross-encoder/ms-marco-MiniLM-L-6-v2", description="重排模型"
    )
    top_n: int | None = Field(default=None, description="重排候选数")
    score_threshold: float | None = Field(default=None, description="重排分数阈值")
    score_mode: str = Field(default="rank", description="分数策略: rank | normalize | raw")
    normalize: str | None = Field(default=None, description="归一化方式: min_max | sigmoid | softmax | zscore")
    params: dict[str, Any] = Field(default_factory=dict, description="额外参数透传")


class RetrieveTuningConfig(BaseModel):
    """检索调参"""

    pool_k: int | None = Field(default=None, description="召回候选池大小")
    rerank_k: int | None = Field(default=None, description="重排候选池大小")
    rrf_k: int = Field(default=60, ge=1, description="RRF 融合参数")


class RetrieveQualityConfig(BaseModel):
    """证据治理"""

    dedupe: bool = Field(default=True, description="是否去重")
    dedupe_threshold: float = Field(default=0.92, ge=0, le=1, description="语义去重阈值")
    max_per_document: int = Field(default=2, ge=1, description="单文档最大保留块数")


class KnowledgeRetrieveConfig(BaseModel):
    """知识检索配置（默认值）"""

    method: str = Field(default="hybrid", description="检索方式: semantic | hybrid")
    top_k: int = Field(default=8, ge=1, description="最终返回数量")
    score_threshold: float | None = Field(default=None, description="最低分数阈值")
    rerank: RerankConfig = Field(default_factory=RerankConfig)
    tuning: RetrieveTuningConfig = Field(default_factory=RetrieveTuningConfig)
    quality: RetrieveQualityConfig = Field(default_factory=RetrieveQualityConfig)
    inject: KnowledgeInjectConfig = Field(default_factory=KnowledgeInjectConfig)


class KnowledgeBaseConfig(BaseModel):
    """知识基础配置（Embedding + VectorStore）"""

    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    vector_store: VectorStoreConfig = Field(default_factory=VectorStoreConfig)


class KnowledgeConfig(BaseModel):
    """知识配置"""

    base_config: KnowledgeBaseConfig = Field(default_factory=KnowledgeBaseConfig)
    chunk_config: KnowledgeChunkConfig = Field(default_factory=KnowledgeChunkConfig)
    retrieve_config: KnowledgeRetrieveConfig = Field(default_factory=KnowledgeRetrieveConfig)
