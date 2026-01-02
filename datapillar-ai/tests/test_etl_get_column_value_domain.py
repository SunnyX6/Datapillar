import json

import pytest

from src.infrastructure.repository import KnowledgeRepository
from src.modules.etl.tools.agent_tools import get_column_value_domain


@pytest.mark.asyncio
async def test_get_column_value_domain_parses_items(monkeypatch):
    async def fake_get_column_value_domains_by_element_id(column_element_id: str):
        assert column_element_id == "eid_col_1"
        return {
            "column_element_id": "eid_col_1",
            "column_id": "col_1",
            "column_name": "pay_status",
            "schema_name": "dwd",
            "table_name": "order",
            "value_domains": [
                {
                    "element_id": "eid_vd_1",
                    "domain_code": "PAY_STATUS",
                    "domain_name": "支付状态",
                    "domain_type": "ENUM",
                    "domain_level": "COLUMN",
                    "data_type": "STRING",
                    "description": "支付状态值域",
                    "items": "UNPAID:未支付,REFUND:退款,PAID:已支付",
                }
            ],
        }

    monkeypatch.setattr(
        KnowledgeRepository,
        "get_column_value_domains_by_element_id",
        fake_get_column_value_domains_by_element_id,
    )

    raw = await get_column_value_domain.ainvoke({"column_element_id": "eid_col_1"})
    payload = json.loads(raw)

    assert payload["status"] == "success"
    assert payload["column_name"] == "pay_status"
    assert payload["value_domain_count"] == 1
    vd = payload["value_domains"][0]
    assert vd["domain_code"] == "PAY_STATUS"
    assert vd["items"] == [
        {"value": "UNPAID", "label": "未支付"},
        {"value": "REFUND", "label": "退款"},
        {"value": "PAID", "label": "已支付"},
    ]
