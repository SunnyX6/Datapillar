"""
Embedding 统一调用层

支持 OpenAI、智谱GLM

特性：
- 统一接口，屏蔽模型差异
- 支持批量向量化
- 基于团队配置创建实例
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
    """Embedding 模型配置"""

    provider: str
    model_name: str
    api_key: str
    base_url: str | None = None
    dimension: int | None = None


class EmbeddingFactory:
    """Embedding 工厂类 - 根据配置创建 Embedding 实例"""

    @staticmethod
    def create_embeddings(config: EmbeddingModelConfig) -> Embeddings:
        """
        创建 LangChain Embeddings 实例

        Args:
            config: Embedding 模型配置

        Returns:
            LangChain Embeddings 实例
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

        raise ValueError(f"不支持的 Embedding 提供商: {provider}")


class EmbeddingProviderClient:
    """
    Embedding 提供者（团队内使用）

    负责按配置创建 Embeddings 实例并做本地缓存。
    """

    def __init__(self, config: EmbeddingConfig) -> None:
        if not config.is_configured():
            raise ValueError("Embedding 未配置，无法创建 EmbeddingProviderClient")
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
        """获取 Embeddings 实例（带缓存）"""
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
                f"创建 Embedding 实例: provider={config.provider}, model={config.model_name}"
            )
            self._cache[cache_key] = embeddings
            return embeddings

    async def embed_text(self, text: str) -> list[float]:
        """向量化单个文本"""
        embeddings = self.get_embeddings()
        return await embeddings.aembed_query(text)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """批量向量化文本"""
        embeddings = self.get_embeddings()
        return await embeddings.aembed_documents(texts)

    def clear_cache(self) -> None:
        """清空 Embedding 实例缓存"""
        with self._lock:
            self._cache.clear()
