# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
LLM 集成层

提供 Embedding 能力（用于图谱语义检索）。
"""

from src.infrastructure.llm.embeddings import UnifiedEmbedder

__all__ = [
    "UnifiedEmbedder",
]
