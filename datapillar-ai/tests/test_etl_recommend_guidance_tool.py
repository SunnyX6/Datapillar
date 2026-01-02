import json

import pytest

from src.modules.etl.tools.agent_tools import recommend_guidance
from src.infrastructure.repository import KnowledgeRepository


@pytest.mark.asyncio
async def test_recommend_guidance_tool_returns_nav_only(monkeypatch):
    calls = {"catalog": 0, "tag": 0}

    async def fake_load_catalog_schema_nav():
        calls["catalog"] += 1
        return [{"name": "c1", "metalake": "m1", "schemas": [{"name": "ods", "table_count": 10}]}]

    async def fake_load_tag_nav(*, limit_tags: int, tables_per_tag: int):
        calls["tag"] += 1
        assert limit_tags == 12
        assert tables_per_tag == 8
        return [
            {"tag": "ods", "table_count": 1, "schemas": ["datapillar"], "sample_tables": [{"schema_name": "datapillar", "table_name": "t_order", "display_name": "t_order", "tags": ["ods"]}]},
            {"tag": "交易域", "table_count": 1, "schemas": ["datapillar"], "sample_tables": [{"schema_name": "datapillar", "table_name": "t_order", "display_name": "t_order", "tags": ["交易域"]}]},
        ]

    monkeypatch.setattr(KnowledgeRepository, "load_catalog_schema_nav", fake_load_catalog_schema_nav)
    monkeypatch.setattr(KnowledgeRepository, "load_tag_nav", fake_load_tag_nav)

    raw = await recommend_guidance.ainvoke({"user_query": "你好"})
    parsed = json.loads(raw)
    assert parsed["status"] == "success"
    assert isinstance(parsed["catalog_schema_nav"], list)
    assert isinstance(parsed["tag_nav"], list)
    assert calls["catalog"] == 1
    assert calls["tag"] == 1

