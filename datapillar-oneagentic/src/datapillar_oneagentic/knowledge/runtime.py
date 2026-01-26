"""Knowledge runtime builder."""

from __future__ import annotations

from dataclasses import dataclass

from datapillar_oneagentic.knowledge.config import KnowledgeBaseConfig
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage import create_knowledge_store
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore


@dataclass
class KnowledgeRuntime:
    """Knowledge runtime dependencies."""

    store: KnowledgeStore
    embedding_provider: EmbeddingProvider

    async def initialize(self) -> None:
        await self.store.initialize()


def build_runtime(*, namespace: str, base_config: KnowledgeBaseConfig) -> KnowledgeRuntime:
    if not namespace:
        raise ValueError("namespace cannot be empty")
    if not base_config.embedding.is_configured():
        raise ValueError("KnowledgeConfig.base_config.embedding is not configured")
    if base_config.embedding.dimension is None:
        raise ValueError("KnowledgeConfig.base_config.embedding.dimension cannot be empty")

    store = create_knowledge_store(
        namespace,
        vector_store_config=base_config.vector_store,
        embedding_config=base_config.embedding,
    )
    embedding_provider = EmbeddingProvider(base_config.embedding)
    return KnowledgeRuntime(store=store, embedding_provider=embedding_provider)
