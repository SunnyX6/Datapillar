import json

import pytest

from src.infrastructure.repository import KnowledgeRepository
from src.modules.etl.tools.agent_tools import search_assets


@pytest.mark.asyncio
async def test_search_assets_maps_scores_and_parses_layer(monkeypatch):
    def fake_search_nodes_with_context(query: str, top_k: int = 10, min_score: float = 0.8):
        assert query == "订单明细"
        assert top_k == 12
        assert min_score == 0.8
        return [
            {
                "element_id": "eid_vd_1",
                "labels": ["Knowledge", "ValueDomain"],
                "primary_label": "ValueDomain",
                "node_id": "vd_1",
                "name": "支付状态",
                "code": "PAY_STATUS",
                "qualified_name": "valuedomain.PAY_STATUS",
                "path": "valuedomain.PAY_STATUS",
                "score": 0.95,
            },
            {
                "element_id": "eid_tbl_2",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_2",
                "name": "order_detail",
                "schema_name": "dwd",
                "table_name": "order_detail",
                "qualified_name": "dwd.order_detail",
                "path": "datapillar.dwd.order_detail",
                "score": 0.91,
            },
            {
                "element_id": "eid_tbl_1",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_1",
                "name": "order",
                "schema_name": "ods",
                "table_name": "order",
                "qualified_name": "ods.order",
                "path": "datapillar.ods.order",
                "score": 0.93,
            },
        ]

    async def fake_search_tables_with_context(table_ids: list[str]):
        assert set(table_ids) == {"tbl_1", "tbl_2"}
        # 故意打乱返回顺序，验证排序是按 relevance_score，而不是按 Neo4j 返回顺序
        return [
            {
                "table_id": "tbl_2",
                "table_name": "order_detail",
                "table_display_name": "订单明细",
                "table_description": "订单明细表",
                "column_count": 12,
                "schema_name": "dwd",
                "catalog_name": "datapillar",
                "table_tags": ["domain:trade"],
                "schema_layer_tag": "layer:DWD",
            },
            {
                "table_id": "tbl_1",
                "table_name": "order",
                "table_display_name": "订单",
                "table_description": "订单主表",
                "column_count": 20,
                "schema_name": "ods",
                "catalog_name": "datapillar",
                "table_tags": ["domain:trade", "layer:ODS"],
                "schema_layer_tag": "layer:ODS",
            },
        ]

    monkeypatch.setattr(KnowledgeRepository, "search_knowledge_nodes_with_context", fake_search_nodes_with_context)
    monkeypatch.setattr(KnowledgeRepository, "search_tables_with_context", fake_search_tables_with_context)

    raw = await search_assets.ainvoke({"query": "订单明细"})
    payload = json.loads(raw)

    assert payload["status"] == "success"
    assert payload["query"] == "订单明细"
    assert payload["total_nodes"] == 3
    assert payload["nodes"][0]["primary_label"] == "ValueDomain"
    assert payload["nodes"][0]["element_id"] == "eid_vd_1"
    assert payload["total_results"] == 2

    tables = payload["tables"]
    assert tables[0]["table_name"] == "order"
    assert tables[0]["relevance_score"] == pytest.approx(0.93, abs=1e-6)
    assert tables[0]["layer"] == "ODS"
    assert tables[0]["column_count"] == 20
    assert tables[0]["tags"] == ["domain:trade", "layer:ODS"]

    assert tables[1]["table_name"] == "order_detail"
    assert tables[1]["relevance_score"] == pytest.approx(0.91, abs=1e-6)
    assert tables[1]["layer"] == "DWD"
    assert tables[1]["column_count"] == 12
    assert tables[1]["tags"] == ["domain:trade"]
