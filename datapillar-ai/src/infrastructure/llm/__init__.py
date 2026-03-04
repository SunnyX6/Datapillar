# @author Sunny
# @date 2026-01-27

"""
LLM Integration layer

provide Embedding Ability(Used for graph semantic retrieval)."""

from src.infrastructure.llm.embeddings import UnifiedEmbedder

__all__ = [
    "UnifiedEmbedder",
]
