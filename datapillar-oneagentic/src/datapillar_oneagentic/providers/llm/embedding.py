"""
Embedding unified access layer.

Supports OpenAI and GLM.

Features:
- Unified interface to hide model differences
- Batch embedding support
- Instances created from team config
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import Any

from langchain_core.embeddings import Embeddings

from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

logger = logging.getLogger(__name__)


@dataclass
class EmbeddingModelConfig:
    """Embedding model configuration."""

    provider: str
    model_name: str
    api_key: str
    base_url: str | None = None
    dimension: int | None = None


class EmbeddingFactory:
    """Embedding factory - create Embeddings from config."""

    @staticmethod
    def create_embeddings(config: EmbeddingModelConfig) -> Embeddings:
        """
        Create a LangChain Embeddings instance.

        Args:
            config: Embedding model config

        Returns:
            LangChain Embeddings instance
        """
        provider = config.provider.lower()

        if provider == "openai":
            from langchain_openai import OpenAIEmbeddings

            kwargs: dict[str, Any] = {
                "api_key": config.api_key,
                "model": config.model_name,
            }
            if config.base_url:
                kwargs["base_url"] = config.base_url
            if config.dimension:
                kwargs["dimensions"] = config.dimension

            return OpenAIEmbeddings(**kwargs)

        if provider == "glm":
            from langchain_community.embeddings import ZhipuAIEmbeddings

            kwargs = {
                "api_key": config.api_key,
                "model": config.model_name,
            }
            if config.dimension:
                kwargs["dimensions"] = config.dimension

            return ZhipuAIEmbeddings(**kwargs)

        raise ValueError(f"Unsupported embedding provider: {provider}")


class EmbeddingProvider:
    """
    Embedding provider (team scope).

    Creates Embeddings instances from config and caches locally.
    """

    def __init__(self, config: EmbeddingConfig) -> None:
        if not config.is_configured():
            raise ValueError("Embedding is not configured; cannot create EmbeddingProvider")
        self._config = config
        self._cache: dict[tuple, Embeddings] = {}
        self._lock = threading.Lock()

    def _build_model_config(self) -> EmbeddingModelConfig:
        return EmbeddingModelConfig(
            provider=self._config.provider,
            model_name=self._config.model,
            api_key=self._config.api_key,
            base_url=self._config.base_url,
            dimension=self._config.dimension,
        )

    def get_embeddings(self) -> Embeddings:
        """Get Embeddings instance with caching."""
        config = self._build_model_config()
        cache_key = (
            config.provider,
            config.model_name,
            config.api_key,
            config.base_url,
            config.dimension,
        )

        if cache_key in self._cache:
            return self._cache[cache_key]

        with self._lock:
            if cache_key in self._cache:
                return self._cache[cache_key]

            embeddings = EmbeddingFactory.create_embeddings(config)
            logger.info(
                f"Embedding instance created: provider={config.provider}, model={config.model_name}"
            )
            self._cache[cache_key] = embeddings
            return embeddings

    async def embed_text(self, text: str) -> list[float]:
        """Embed a single text."""
        embeddings = self.get_embeddings()
        return await embeddings.aembed_query(text)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """Embed a batch of texts."""
        embeddings = self.get_embeddings()
        return await embeddings.aembed_documents(texts)

    def clear_cache(self) -> None:
        """Clear Embeddings cache."""
        with self._lock:
            self._cache.clear()
