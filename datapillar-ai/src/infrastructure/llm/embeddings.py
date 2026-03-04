# @author Sunny
# @date 2026-01-27

"""
Embedding Integration layer

Unify the framework EmbeddingProvider."""

import logging
from functools import lru_cache

from datapillar_oneagentic.providers.llm import EmbeddingProvider
from neo4j_graphrag.embeddings.base import Embedder

from src.infrastructure.llm.config import get_datapillar_config
from src.shared.config.runtime import get_default_tenant_id

logger = logging.getLogger(__name__)


def _resolve_tenant_id(tenant_id: int | None) -> int:
    return tenant_id or get_default_tenant_id()


@lru_cache(maxsize=32)
def _get_embedding_provider(tenant_id: int) -> EmbeddingProvider:
    config = get_datapillar_config(tenant_id)
    return EmbeddingProvider(config.embedding)


@lru_cache(maxsize=32)
def _get_embeddings(tenant_id: int):
    return _get_embedding_provider(tenant_id).get_embeddings()


class UnifiedEmbedder(Embedder):
    """
    unify Embedder(realize neo4j-graphrag of Embedder interface)

    Cache instances by tenant:The same tenant reuses the same Embedder."""

    _instances: dict[int, "UnifiedEmbedder"] = {}

    def __new__(cls, tenant_id: int | None = None):
        resolved_tenant_id = _resolve_tenant_id(tenant_id)
        instance = cls._instances.get(resolved_tenant_id)
        if instance is None:
            instance = super().__new__(cls)
            instance._initialized = False
            instance._tenant_id = resolved_tenant_id
            cls._instances[resolved_tenant_id] = instance
        return instance

    def __init__(self, tenant_id: int | None = None):
        """Use frames EmbeddingProvider(Each tenant is only initialized once)"""
        if getattr(self, "_initialized", False):
            return

        resolved_tenant_id = _resolve_tenant_id(tenant_id)
        config = get_datapillar_config(resolved_tenant_id).embedding
        self._tenant_id = resolved_tenant_id
        self.provider = config.provider
        self.model_name = config.model
        self.dimension = config.dimension
        self._embeddings = _get_embeddings(resolved_tenant_id)
        self._initialized = True
        logger.info(
            "UnifiedEmbedder Initialization completed:%s,tenant_id=%s",
            type(self._embeddings).__name__,
            resolved_tenant_id,
        )

    def embed_query(self, text: str) -> list[float]:
        """Generate vector embeddings for a single query"""
        return list(self._embeddings.embed_query(text))

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """Generate vector embeddings in batches"""
        if not texts:
            return []
        vectors = self._embeddings.embed_documents(texts)
        return [list(vector) for vector in vectors]
