import json
import pytest

from src.modules.etl.agents.knowledge_agent import KnowledgeAgent
from src.modules.etl.schemas.kg_context import AgentType
from src.modules.etl.schemas.state import AgentState
from src.infrastructure.repository import KnowledgeRepository


class _FakeTool:
    def __init__(self, *, result: str):
        self._result = result
        self.called_with: list[dict] = []

    async def ainvoke(self, payload):
        self.called_with.append(payload)
        return self._result


class _FakeResponse:
    def __init__(self, *, content: str):
        self.content = content


class _FakeLLM:
    def __init__(self, *, content: str):
        self._content = content
        self.messages_history: list[list] = []

    async def ainvoke(self, messages):
        self.messages_history.append(messages)
        return _FakeResponse(content=self._content)


@pytest.mark.asyncio
async def test_knowledge_agent_emits_table_pointers_and_qualified_names(monkeypatch):
    def fake_search_nodes_with_context(query: str, top_k: int = 10, min_score: float = 0.8):
        assert query == "订单"
        assert top_k == 12
        assert min_score == 0.8
        return [
            {
                "element_id": "eid_tbl_1",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_1",
                "code": None,
                "name": "order",
                "display_name": "订单",
                "description": "订单主表",
                "tags": ["domain:trade", "layer:ODS"],
                "catalog_name": "datapillar",
                "schema_name": "ods",
                "table_name": "order",
                "path": "datapillar.ods.order",
                "qualified_name": "ods.order",
                "score": 0.93,
            },
            {
                "element_id": "eid_tbl_2",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_2",
                "code": None,
                "name": "order_detail",
                "display_name": "订单明细",
                "description": "订单明细表",
                "tags": ["domain:trade", "layer:DWD"],
                "catalog_name": "datapillar",
                "schema_name": "dwd",
                "table_name": "order_detail",
                "path": "datapillar.dwd.order_detail",
                "qualified_name": "dwd.order_detail",
                "score": 0.91,
            },
        ]

    monkeypatch.setattr(KnowledgeRepository, "search_knowledge_nodes_with_context", fake_search_nodes_with_context)

    agent = KnowledgeAgent(max_pointers=12, min_score=0.8)
    state = AgentState(user_input="订单")
    cmd = await agent(state)

    update = cmd.update
    assert update["current_agent"] == "knowledge_agent"
    assert "agent_contexts" in update

    analyst_ctx = update["agent_contexts"][AgentType.ANALYST]
    assert analyst_ctx["tables"] == ["ods.order", "dwd.order_detail"]
    assert analyst_ctx["etl_pointers"][0]["element_id"] == "eid_tbl_1"
    assert analyst_ctx["etl_pointers"][0]["qualified_name"] == "ods.order"
    assert analyst_ctx["etl_pointers"][0]["node_id"] == "tbl_1"
    assert analyst_ctx["etl_pointers"][0]["schema_name"] == "ods"
    assert analyst_ctx["etl_pointers"][0]["tools"] == ["get_table_columns"]

    meta = update["metadata"]["knowledge_agent"]
    assert meta["summary"]
    assert meta["etl_pointers"][0]["element_id"] == "eid_tbl_1"
    assert len(meta["etl_pointers"]) == 2


@pytest.mark.asyncio
async def test_knowledge_agent_fills_qualified_name_for_table_and_column(monkeypatch):
    def fake_search_nodes_with_context(query: str, top_k: int = 10, min_score: float = 0.8):
        assert query == "order"
        return [
            {
                "element_id": "eid_tbl_1",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_1",
                "name": "order",
                "display_name": "订单",
                "catalog_name": "datapillar",
                "schema_name": "ods",
                "table_name": None,
                "qualified_name": None,
                "path": "datapillar.ods.order",
                "score": 0.9,
            },
            {
                "element_id": "eid_col_1",
                "labels": ["Knowledge", "Column"],
                "primary_label": "Column",
                "node_id": "col_1",
                "name": "order_id",
                "display_name": "订单ID",
                "catalog_name": "datapillar",
                "schema_name": "ods",
                "table_name": "order",
                "qualified_name": None,
                "path": "datapillar.ods.order.order_id",
                "score": 0.85,
            },
        ]

    monkeypatch.setattr(KnowledgeRepository, "search_knowledge_nodes_with_context", fake_search_nodes_with_context)

    agent = KnowledgeAgent(max_pointers=12, min_score=0.8)
    state = AgentState(user_input="order")
    cmd = await agent(state)

    analyst_ctx = cmd.update["agent_contexts"][AgentType.ANALYST]
    assert analyst_ctx["tables"] == ["ods.order"]

    pointers = analyst_ctx["etl_pointers"]
    assert pointers[0]["element_id"] == "eid_tbl_1"
    assert pointers[0]["table_name"] == "order"
    assert pointers[0]["qualified_name"] == "ods.order"
    assert pointers[0]["tools"] == ["get_table_columns"]

    assert pointers[1]["element_id"] == "eid_col_1"
    assert pointers[1]["qualified_name"] == "ods.order.order_id"
    assert pointers[1]["tools"] == []


@pytest.mark.asyncio
async def test_knowledge_agent_does_not_short_circuit_free_text_table_token(monkeypatch):
    def fake_search_nodes_with_context(query: str, top_k: int = 10, min_score: float = 0.8):
        assert query == "帮我把 ods.order 清洗到 xxx"
        return [
            {
                "element_id": "eid_tbl_1",
                "labels": ["Knowledge", "Table"],
                "primary_label": "Table",
                "node_id": "tbl_1",
                "name": "order",
                "display_name": "订单",
                "description": "订单主表",
                "tags": [],
                "catalog_name": "datapillar",
                "schema_name": "ods",
                "table_name": "order",
                "path": "datapillar.ods.order",
                "qualified_name": "ods.order",
                "score": 0.93,
            }
        ]

    monkeypatch.setattr(KnowledgeRepository, "search_knowledge_nodes_with_context", fake_search_nodes_with_context)

    agent = KnowledgeAgent(max_pointers=12, min_score=0.8)
    state = AgentState(user_input="帮我把 ods.order 清洗到 xxx")
    cmd = await agent(state)

    analyst_ctx = cmd.update["agent_contexts"][AgentType.ANALYST]
    assert analyst_ctx["tables"] == ["ods.order"]
    assert analyst_ctx["etl_pointers"][0]["element_id"] == "eid_tbl_1"
    assert analyst_ctx["etl_pointers"][0]["tools"] == ["get_table_columns"]


@pytest.mark.asyncio
async def test_knowledge_agent_enqueues_human_request_when_no_pointers(monkeypatch):
    def fake_search_nodes_with_context(query: str, top_k: int = 10, min_score: float = 0.8):
        assert query == "完全不相关的描述"
        return []

    monkeypatch.setattr(KnowledgeRepository, "search_knowledge_nodes_with_context", fake_search_nodes_with_context)

    agent = KnowledgeAgent(max_pointers=12, min_score=0.8)
    state = AgentState(user_input="完全不相关的描述")
    cmd = await agent(state)

    update = cmd.update
    assert update["current_agent"] == "knowledge_agent"
    assert "agent_contexts" not in update
    assert update["metadata"]["knowledge_user_input"] == "完全不相关的描述"
    assert update["metadata"]["knowledge_no_hit"]["user_query"] == "完全不相关的描述"
