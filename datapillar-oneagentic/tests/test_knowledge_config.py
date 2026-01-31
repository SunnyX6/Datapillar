# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-30
"""KnowledgeConfig validation tests."""

from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import KnowledgeConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig


def _base_config() -> dict:
    return {
        "embedding": EmbeddingConfig(provider="openai", api_key="stub", model="stub", dimension=2),
        "vector_store": VectorStoreConfig(type="lance"),
    }


def test_namespaces_dedup_and_trim() -> None:
    config = KnowledgeConfig(
        **_base_config(),
        namespaces=["kb_main", "kb_a", "kb_a", "  kb_b  ", ""],
    )
    assert config.namespaces == ["kb_main", "kb_a", "kb_b"]


def test_namespaces_empty_after_clean() -> None:
    with pytest.raises(ValueError, match="cannot be empty"):
        KnowledgeConfig(
            **_base_config(),
            namespaces=["", "   "],
        )
