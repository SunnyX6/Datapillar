# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Embedding 集成层

统一走框架 EmbeddingProvider。
"""

import logging
from functools import lru_cache

from datapillar_oneagentic.providers.llm import EmbeddingProvider
from neo4j_graphrag.embeddings.base import Embedder

from src.infrastructure.llm.config import get_datapillar_config

logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def _get_embedding_provider() -> EmbeddingProvider:
    config = get_datapillar_config()
    return EmbeddingProvider(config.embedding)


@lru_cache(maxsize=1)
def _get_embeddings():
    return _get_embedding_provider().get_embeddings()


class UnifiedEmbedder(Embedder):
    """
    统一 Embedder（实现 neo4j-graphrag 的 Embedder 接口）

    单例模式：无论调用多少次 UnifiedEmbedder()，都返回同一个实例
    """

    _instance = None
    _initialized = False

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        """使用框架 EmbeddingProvider（只初始化一次）"""
        if self._initialized:
            return

        config = get_datapillar_config().embedding
        self.provider = config.provider
        self.model_name = config.model
        self.dimension = config.dimension
        self._embeddings = _get_embeddings()
        UnifiedEmbedder._initialized = True
        logger.info("UnifiedEmbedder 初始化完成: %s", type(self._embeddings).__name__)

    def embed_query(self, text: str) -> list[float]:
        """生成单个查询的向量嵌入"""
        return list(self._embeddings.embed_query(text))

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """批量生成向量嵌入"""
        if not texts:
            return []
        vectors = self._embeddings.embed_documents(texts)
        return [list(vector) for vector in vectors]
