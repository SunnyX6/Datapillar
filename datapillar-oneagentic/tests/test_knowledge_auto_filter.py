from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge.config import MetadataFilterConfig
from datapillar_oneagentic.knowledge.filtering import build_auto_filters


@pytest.mark.asyncio
async def test_auto_filter_disabled() -> None:
    config = MetadataFilterConfig(mode="off")
    filters = await build_auto_filters(query="source_id:abc", config=config)
    assert filters is None


@pytest.mark.asyncio
async def test_auto_filter_default_enabled() -> None:
    config = MetadataFilterConfig()
    filters = await build_auto_filters(query="source_id:abc", config=config)
    assert filters is not None
    assert filters.get("source_id") == "abc"


@pytest.mark.asyncio
async def test_auto_filter_rule_kv_and_version() -> None:
    config = MetadataFilterConfig(mode="auto", min_confidence=0.5)
    filters = await build_auto_filters(query="source_id:docs version 1.2.3", config=config)
    assert filters is not None
    assert filters.get("source_id") == "docs"
    version = filters.get("version")
    if isinstance(version, list):
        assert "1.2.3" in version
    else:
        assert version == "1.2.3"


@pytest.mark.asyncio
async def test_auto_filter_min_confidence() -> None:
    config = MetadataFilterConfig(mode="auto", min_confidence=0.9)
    filters = await build_auto_filters(query="version 2024", config=config)
    assert filters is None
