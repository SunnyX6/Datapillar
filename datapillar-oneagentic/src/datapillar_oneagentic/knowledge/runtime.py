# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge runtime builder."""

from __future__ import annotations

from dataclasses import dataclass

from datapillar_oneagentic.knowledge.config import KnowledgeConfig
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


def build_runtime(*, namespace: str | None = None, config: KnowledgeConfig) -> KnowledgeRuntime:
    if config is None:
        raise ValueError("config cannot be empty")
    if not namespace:
        raise ValueError("namespace cannot be empty")
    resolved_namespace = namespace
    if not config.embedding.is_configured():
        raise ValueError("KnowledgeConfig.embedding is not configured")
    if config.embedding.dimension is None:
        raise ValueError("KnowledgeConfig.embedding.dimension cannot be empty")

    store = create_knowledge_store(
        resolved_namespace,
        vector_store_config=config.vector_store,
        embedding_config=config.embedding,
    )
    embedding_provider = EmbeddingProvider(config.embedding)
    return KnowledgeRuntime(store=store, embedding_provider=embedding_provider)
