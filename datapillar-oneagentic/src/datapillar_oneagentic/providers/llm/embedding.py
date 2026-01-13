"""
Embedding 统一调用层

支持 OpenAI、智谱GLM

特性：
- 统一接口，屏蔽模型差异
- 支持批量向量化
- 自动从配置获取参数
"""

import logging
import os
import threading
from dataclasses import dataclass
from typing import Any

from langchain_core.embeddings import Embeddings

from datapillar_oneagentic.providers.llm.config import EmbeddingConfig, EmbeddingProvider

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

        elif provider == "glm":
            from langchain_community.embeddings import ZhipuAIEmbeddings

            kwargs = {
                "api_key": config.api_key,
                "model": config.model_name,
            }
            if config.dimension:
                kwargs["dimensions"] = config.dimension

            return ZhipuAIEmbeddings(**kwargs)

        else:
            raise ValueError(f"不支持的 Embedding 提供商: {provider}")


# ==================== Embedding 实例缓存 ====================
_embedding_cache: dict[tuple, Embeddings] = {}
_embedding_cache_lock = threading.Lock()
_EMBEDDING_CACHE_MAX_SIZE = 20


def _cleanup_embedding_cache_if_needed() -> None:
    """如果缓存超限，清理最旧的一半（需持有锁）"""
    if len(_embedding_cache) >= _EMBEDDING_CACHE_MAX_SIZE:
        keys = list(_embedding_cache.keys())
        for key in keys[: len(keys) // 2]:
            _embedding_cache.pop(key, None)


def call_embedding() -> Embeddings:
    """
    统一的 Embedding 获取接口

    返回 LangChain Embeddings 实例，可用于：
    - embed_query(text): 向量化单个文本
    - embed_documents(texts): 批量向量化

    配置来源优先级：
    1. datapillar_configure() 配置
    2. 环境变量

    Returns:
        LangChain Embeddings 实例

    Raises:
        ValueError: 未找到可用配置
    """
    # 尝试从配置获取
    config = _get_embedding_config()

    if not config:
        raise ValueError(
            "Embedding 未配置！请通过以下方式配置：\n"
            "1. datapillar_configure(embedding={...})\n"
            "2. 环境变量 OPENAI_API_KEY + OPENAI_EMBEDDING_MODEL"
        )

    cache_key = (config.provider, config.model_name, config.api_key, config.base_url)

    # 双重检查锁定
    if cache_key in _embedding_cache:
        return _embedding_cache[cache_key]

    with _embedding_cache_lock:
        if cache_key in _embedding_cache:
            return _embedding_cache[cache_key]

        embeddings = EmbeddingFactory.create_embeddings(config)
        logger.info(f"创建 Embedding 实例: provider={config.provider}, model={config.model_name}")

        _cleanup_embedding_cache_if_needed()
        _embedding_cache[cache_key] = embeddings
        return embeddings


def _get_embedding_config() -> EmbeddingModelConfig | None:
    """获取 Embedding 配置"""
    # 1. 尝试从 datapillar 配置获取
    try:
        from datapillar_oneagentic.config import datapillar

        embedding_config: EmbeddingConfig = datapillar.embedding
        if embedding_config.is_configured():
            return EmbeddingModelConfig(
                provider=embedding_config.provider,
                model_name=embedding_config.model,
                api_key=embedding_config.api_key,
                base_url=embedding_config.base_url,
                dimension=embedding_config.dimension,
            )
    except Exception:
        pass

    # 2. 尝试从环境变量获取（OpenAI）
    openai_key = os.environ.get("OPENAI_API_KEY")
    if openai_key:
        return EmbeddingModelConfig(
            provider="openai",
            model_name=os.environ.get("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small"),
            api_key=openai_key,
            base_url=os.environ.get("OPENAI_BASE_URL"),
        )

    # 3. 尝试从环境变量获取（GLM）
    glm_key = os.environ.get("GLM_API_KEY") or os.environ.get("ZHIPU_API_KEY")
    if glm_key:
        return EmbeddingModelConfig(
            provider="glm",
            model_name=os.environ.get("GLM_EMBEDDING_MODEL", "embedding-3"),
            api_key=glm_key,
        )

    return None


async def embed_text(text: str) -> list[float]:
    """
    向量化单个文本（异步便捷接口）

    Args:
        text: 待向量化的文本

    Returns:
        向量列表
    """
    embeddings = call_embedding()
    return await embeddings.aembed_query(text)


async def embed_texts(texts: list[str]) -> list[list[float]]:
    """
    批量向量化文本（异步便捷接口）

    Args:
        texts: 待向量化的文本列表

    Returns:
        向量列表的列表
    """
    embeddings = call_embedding()
    return await embeddings.aembed_documents(texts)


def clear_embedding_cache() -> None:
    """清空 Embedding 实例缓存（测试用）"""
    global _embedding_cache
    with _embedding_cache_lock:
        _embedding_cache.clear()
