# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Knowledge EmbeddingProcessor（封装共享处理器）
"""

from src.infrastructure.repository.knowledge import Metadata
from src.shared.embedding.processor import EmbeddingProcessor

_embedding_processor: EmbeddingProcessor | None = None


def get_embedding_processor() -> EmbeddingProcessor:
    """获取 EmbeddingProcessor 单例（延迟初始化）"""
    global _embedding_processor
    if _embedding_processor is None:
        _embedding_processor = EmbeddingProcessor(metadata_repo=Metadata)
    return _embedding_processor


__all__ = ["EmbeddingProcessor", "get_embedding_processor"]
