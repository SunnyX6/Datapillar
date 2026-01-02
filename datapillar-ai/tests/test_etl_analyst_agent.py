import json

import pytest

from src.modules.etl.agents.analyst_agent import AnalystAgent, ANALYST_AGENT_SYSTEM_INSTRUCTIONS
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, ETLPointer
from src.modules.etl.schemas.state import AgentState


class _FakeResponse:
    def __init__(self, *, content: str, tool_calls: list[dict] | None = None):
        self.content = content
        self.tool_calls = tool_calls or []


class _FakeLLM:
    def __init__(self, responses: list[_FakeResponse]):
        self._responses = list(responses)
        self.bound_tools = None
        self.messages_history: list[list] = []

    def bind_tools(self, tools):
        self.bound_tools = tools
        return self

    async def ainvoke(self, messages):
        self.messages_history.append(messages)
        if not self._responses:
            raise AssertionError("FakeLLM responses exhausted")
        return self._responses.pop(0)


@pytest.mark.asyncio
async def test_analyst_agent_uses_etl_pointers_context_and_no_search_assets(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content=json.dumps(
                    {
                        "summary": "把 ods.order 清洗到 dwd.order_clean",
                        "steps": [
                            {
                                "step_id": "step_1",
                                "step_name": "清洗订单",
                                "description": "对订单进行清洗后落表",
                                "input_tables": ["ods.order"],
                                "output_table": "dwd.order_clean",
                                "depends_on": [],
                            }
                        ],
                        "final_target": {"table_name": "dwd.order_clean", "write_mode": "overwrite", "partition_by": ["dt"]},
                        "ambiguities": [],
                        "confidence": 0.9,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            )
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.call_llm", fake_call_llm)

    agent = AnalystAgent()
    state = AgentState(
        user_input="帮我把订单表清洗到 dwd.order_clean",
        agent_contexts={
            AgentType.ANALYST: AgentScopedContext(
                agent_type=AgentType.ANALYST,
                tables=["ods.order", "dwd.order_clean"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        display_name="订单",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        path="datapillar.ods.order",
                        tools=["get_table_columns"],
                    )
                    ,
                    ETLPointer(
                        element_id="eid_tbl_2",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order_clean",
                        display_name="订单清洗表",
                        schema_name="dwd",
                        table_name="order_clean",
                        qualified_name="dwd.order_clean",
                        path="datapillar.dwd.order_clean",
                        tools=["get_table_columns"],
                    ),
                ],
                tools=["get_table_columns"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "analyst_agent"
    assert cmd.update["analysis_result"]["summary"]

    assert fake_llm.bound_tools is not None
    assert len(fake_llm.bound_tools) == 1

    messages = fake_llm.messages_history[0]
    assert messages[0].content == ANALYST_AGENT_SYSTEM_INSTRUCTIONS

    context_payload = json.loads(messages[1].content)
    assert context_payload["tables"] == ["ods.order", "dwd.order_clean"]
    assert context_payload["etl_pointers"][0]["element_id"] == "eid_tbl_1"


@pytest.mark.asyncio
async def test_analyst_agent_rejects_tool_call_not_in_pointers(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content="",
                tool_calls=[
                    {"name": "get_table_columns", "args": {"table_name": "ods.not_in_context"}, "id": "t1"}
                ],
            ),
            _FakeResponse(
                content=json.dumps(
                    {
                        "summary": "需求不明确",
                        "steps": [],
                        "final_target": {"table_name": "", "write_mode": "overwrite", "partition_by": ["dt"]},
                        "ambiguities": [{"question": "请确认目标表名", "context": "未提供目标表", "options": []}],
                        "confidence": 0.0,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            ),
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.call_llm", fake_call_llm)

    agent = AnalystAgent()
    state = AgentState(
        user_input="帮我处理订单表",
        agent_contexts={
            AgentType.ANALYST: AgentScopedContext(
                agent_type=AgentType.ANALYST,
                tables=["ods.order"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns"],
                    )
                ],
                tools=["get_table_columns"],
            )
        },
    )

    _ = await agent(state)

    second_round_messages = fake_llm.messages_history[1]
    tool_messages = [m for m in second_round_messages if getattr(m, "type", "") == "tool"]
    assert tool_messages, "expected tool message to be appended"
    tool_result = json.loads(tool_messages[-1].content)
    assert tool_result["status"] == "error"


@pytest.mark.asyncio
async def test_analyst_agent_enqueues_human_request_when_needs_clarification(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content=json.dumps(
                    {
                        "summary": "需求不明确",
                        "steps": [],
                        "final_target": {"table_name": "", "write_mode": "overwrite", "partition_by": ["dt"]},
                        "ambiguities": [{"question": "请确认目标表名", "context": "未提供目标表", "options": []}],
                        "confidence": 0.0,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            )
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.call_llm", fake_call_llm)

    agent = AnalystAgent()
    state = AgentState(
        user_input="帮我处理订单表",
        agent_contexts={
            AgentType.ANALYST: AgentScopedContext(
                agent_type=AgentType.ANALYST,
                tables=["ods.order"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns"],
                    )
                ],
                tools=["get_table_columns"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "analyst_agent"
    assert "pending_requests" in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    assert cmd.update["pending_requests"][0]["kind"] == "human"
    assert "analysis_result" not in cmd.update


@pytest.mark.asyncio
async def test_analyst_agent_requests_table_fix_when_unknown_tables(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content=json.dumps(
                    {
                        "summary": "把 ods.order 清洗到 dwd.order_clean",
                        "steps": [
                            {
                                "step_id": "step_1",
                                "step_name": "清洗订单",
                                "description": "对订单进行清洗后落表",
                                "input_tables": ["ods.order"],
                                "output_table": "dwd.order_clean",
                                "depends_on": [],
                            }
                        ],
                        "final_target": {"table_name": "dwd.order_clean", "write_mode": "overwrite", "partition_by": ["dt"]},
                        "ambiguities": [],
                        "confidence": 0.9,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            )
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.call_llm", fake_call_llm)

    agent = AnalystAgent()
    state = AgentState(
        user_input="帮我把订单表清洗到 dwd.order_clean",
        agent_contexts={
            AgentType.ANALYST: AgentScopedContext(
                agent_type=AgentType.ANALYST,
                tables=["ods.order"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns"],
                    )
                ],
                tools=["get_table_columns"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "analyst_agent"
    assert "analysis_result" not in cmd.update
    assert "pending_requests" in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "delegate"
    assert req0["target_agent"] == "knowledge_agent"
    assert req0["resume_to"] == "analyst_agent"


@pytest.mark.asyncio
async def test_analyst_agent_includes_guidance_in_clarification_when_unknown_tables_persist(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content=json.dumps(
                    {
                        "summary": "把 ods.order 清洗到 dwd.order_clean",
                        "steps": [
                            {
                                "step_id": "step_1",
                                "step_name": "清洗订单",
                                "description": "对订单进行清洗后落表",
                                "input_tables": ["ods.order"],
                                "output_table": "dwd.order_clean",
                                "depends_on": [],
                            }
                        ],
                        "final_target": {"table_name": "dwd.order_clean", "write_mode": "overwrite", "partition_by": ["dt"]},
                        "ambiguities": [],
                        "confidence": 0.9,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            )
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    class _FakeGuidanceTool:
        async def ainvoke(self, payload):
            assert payload["user_query"]
            return json.dumps(
                {
                    "status": "success",
                    "user_query": payload["user_query"],
                    "catalog_schema_nav": [{"name": "c1", "metalake": "m1", "schemas": [{"name": "ods", "table_count": 10}]}],
                    "tag_nav": [{"tag": "ods", "table_count": 1, "schemas": ["ods"], "sample_tables": [{"schema_name": "ods", "table_name": "order"}]}],
                },
                ensure_ascii=False,
            )

    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.analyst_agent.recommend_guidance", _FakeGuidanceTool())

    agent = AnalystAgent()
    state = AgentState(
        user_input="帮我把订单表清洗到 dwd.order_clean",
        delegation_counters={"analyst_agent:delegate:knowledge_agent:unknown_tables": 1},
        agent_contexts={
            AgentType.ANALYST: AgentScopedContext(
                agent_type=AgentType.ANALYST,
                tables=["ods.order"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns"],
                    )
                ],
                tools=["get_table_columns"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "analyst_agent"
    assert "analysis_result" not in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "human"
    assert req0["payload"]["type"] == "clarification"
    assert req0["payload"]["guidance"]["status"] == "success"
