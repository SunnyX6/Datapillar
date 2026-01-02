import json

import pytest

from src.modules.etl.agents.developer_agent import DeveloperAgent
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


class _FakeAsyncTool:
    def __init__(self, *, result: str):
        self._result = result
        self.called_with: list[dict] = []

    async def ainvoke(self, payload):
        self.called_with.append(payload)
        return self._result


@pytest.mark.asyncio
async def test_developer_agent_injects_etl_pointers_context(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content="""
-- Stage 1: clean
CREATE TABLE temp.tmp_clean AS
SELECT 1 AS id;
""".strip(),
                tool_calls=[],
            )
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    fake_get_table_columns = _FakeAsyncTool(
        result=json.dumps(
            {"status": "success", "table_name": "ods.order", "columns": [{"name": "order_id", "data_type": "string"}]},
            ensure_ascii=False,
        )
    )
    fake_get_column_lineage = _FakeAsyncTool(
        result=json.dumps(
            {"status": "success", "source_table": "ods.order", "target_table": "dwd.order_clean", "lineage": []},
            ensure_ascii=False,
        )
    )
    fake_get_sql_by_lineage = _FakeAsyncTool(
        result=json.dumps(
            {
                "status": "success",
                "source_tables": ["ods.order"],
                "target_table": "dwd.order_clean",
                "sql_id": "sql_1",
                "sql_name": "ref",
                "sql_content": "SELECT 1;",
                "engine": "SPARK_SQL",
            },
            ensure_ascii=False,
        )
    )
    fake_get_column_value_domain = _FakeAsyncTool(
        result=json.dumps({"status": "not_found", "column_element_id": "eid_col_1", "value_domains": []}, ensure_ascii=False)
    )

    monkeypatch.setattr("src.modules.etl.agents.developer_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_table_columns", fake_get_table_columns)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_column_lineage", fake_get_column_lineage)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_sql_by_lineage", fake_get_sql_by_lineage)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_column_value_domain", fake_get_column_value_domain)

    agent = DeveloperAgent()
    state = AgentState(
        user_input="把订单清洗到 dwd.order_clean",
        architecture_plan={
            "name": "订单清洗工作流",
            "description": "从 ods.order 清洗到 dwd.order_clean",
            "jobs": [
                {
                    "id": "job_1",
                    "name": "订单清洗",
                    "description": "清洗并落表",
                    "type": "SPARK_SQL",
                    "depends": [],
                    "step_ids": ["step_1"],
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "stages": [
                        {
                            "stage_id": 1,
                            "name": "清洗",
                            "description": "清洗订单",
                            "input_tables": ["ods.order"],
                            "output_table": "temp.tmp_clean_order",
                            "is_temp_table": True,
                        }
                    ],
                    "config": {},
                }
            ],
        },
        agent_contexts={
            AgentType.DEVELOPER: AgentScopedContext(
                agent_type=AgentType.DEVELOPER,
                tables=["ods.order", "dwd.order_clean"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns", "get_column_lineage", "get_sql_by_lineage"],
                    ),
                    ETLPointer(
                        element_id="eid_tbl_2",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order_clean",
                        schema_name="dwd",
                        table_name="order_clean",
                        qualified_name="dwd.order_clean",
                        tools=["get_table_columns", "get_column_lineage", "get_sql_by_lineage"],
                    ),
                    ETLPointer(
                        element_id="eid_col_1",
                        labels=["Knowledge", "Column"],
                        primary_label="Column",
                        name="status",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order.status",
                        tools=["get_column_value_domain"],
                    ),
                ],
                tools=["get_table_columns", "get_column_value_domain", "get_column_lineage", "get_sql_by_lineage"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "developer_agent"
    assert cmd.update["architecture_plan"]["jobs"][0]["config_generated"] is True

    messages = fake_llm.messages_history[0]
    context_payload = json.loads(messages[2].content)
    assert context_payload["tables"] == ["ods.order", "dwd.order_clean"]
    assert context_payload["etl_pointers"][0]["element_id"] == "eid_tbl_1"


@pytest.mark.asyncio
async def test_developer_agent_rejects_tool_call_not_in_pointers(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content="",
                tool_calls=[{"name": "get_table_columns", "args": {"table_name": "ods.not_in_context"}, "id": "t1"}],
            ),
            _FakeResponse(
                content="SELECT 1 AS id;",
                tool_calls=[],
            ),
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    fake_get_table_columns = _FakeAsyncTool(
        result=json.dumps({"status": "success", "table_name": "ods.order", "columns": []}, ensure_ascii=False)
    )
    fake_get_column_lineage = _FakeAsyncTool(
        result=json.dumps({"status": "success", "source_table": "ods.order", "target_table": "dwd.order_clean", "lineage": []}, ensure_ascii=False)
    )
    fake_get_sql_by_lineage = _FakeAsyncTool(
        result=json.dumps(
            {"status": "success", "source_tables": ["ods.order"], "target_table": "dwd.order_clean", "sql_id": None, "sql_content": "SELECT 1;", "engine": "SPARK_SQL"},
            ensure_ascii=False,
        )
    )
    fake_get_column_value_domain = _FakeAsyncTool(
        result=json.dumps({"status": "not_found", "column_element_id": "eid_col_1", "value_domains": []}, ensure_ascii=False)
    )

    monkeypatch.setattr("src.modules.etl.agents.developer_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_table_columns", fake_get_table_columns)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_column_lineage", fake_get_column_lineage)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_sql_by_lineage", fake_get_sql_by_lineage)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.get_column_value_domain", fake_get_column_value_domain)

    agent = DeveloperAgent()
    state = AgentState(
        user_input="把订单清洗到 dwd.order_clean",
        architecture_plan={
            "name": "订单清洗工作流",
            "description": "从 ods.order 清洗到 dwd.order_clean",
            "jobs": [
                {
                    "id": "job_1",
                    "name": "订单清洗",
                    "description": "清洗并落表",
                    "type": "SPARK_SQL",
                    "depends": [],
                    "step_ids": ["step_1"],
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "stages": [
                        {
                            "stage_id": 1,
                            "name": "清洗",
                            "description": "清洗订单",
                            "input_tables": ["ods.order"],
                            "output_table": "temp.tmp_clean_order",
                            "is_temp_table": True,
                        }
                    ],
                    "config": {},
                }
            ],
        },
        agent_contexts={
            AgentType.DEVELOPER: AgentScopedContext(
                agent_type=AgentType.DEVELOPER,
                tables=["ods.order", "dwd.order_clean"],
                etl_pointers=[
                    ETLPointer(
                        element_id="eid_tbl_1",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order",
                        schema_name="ods",
                        table_name="order",
                        qualified_name="ods.order",
                        tools=["get_table_columns", "get_column_lineage", "get_sql_by_lineage"],
                    ),
                    ETLPointer(
                        element_id="eid_tbl_2",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order_clean",
                        schema_name="dwd",
                        table_name="order_clean",
                        qualified_name="dwd.order_clean",
                        tools=["get_table_columns", "get_column_lineage", "get_sql_by_lineage"],
                    ),
                ],
                tools=["get_table_columns", "get_column_value_domain", "get_column_lineage", "get_sql_by_lineage"],
            )
        },
    )

    _ = await agent(state)


@pytest.mark.asyncio
async def test_developer_agent_includes_guidance_in_clarification_when_unknown_tables_persist(monkeypatch):
    fake_llm = _FakeLLM(responses=[_FakeResponse(content="SELECT 1 AS id;", tool_calls=[])])

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

    monkeypatch.setattr("src.modules.etl.agents.developer_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.developer_agent.recommend_guidance", _FakeGuidanceTool())

    agent = DeveloperAgent()
    state = AgentState(
        user_input="把订单清洗到 dwd.order_clean",
        delegation_counters={"developer_agent:delegate:knowledge_agent:unknown_tables": 1},
        architecture_plan={
            "name": "订单清洗工作流",
            "description": "从 ods.order 清洗到 dwd.order_clean",
            "jobs": [
                {
                    "id": "job_1",
                    "name": "订单清洗",
                    "description": "清洗并落表",
                    "type": "SPARK_SQL",
                    "depends": [],
                    "step_ids": ["step_1"],
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "stages": [],
                    "config": {},
                }
            ],
        },
        agent_contexts={
            AgentType.DEVELOPER: AgentScopedContext(
                agent_type=AgentType.DEVELOPER,
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
                        tools=["get_table_columns", "get_column_lineage", "get_sql_by_lineage"],
                    )
                ],
                tools=["get_table_columns", "get_column_value_domain", "get_column_lineage", "get_sql_by_lineage"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "developer_agent"
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "human"
    assert req0["payload"]["type"] == "clarification"
    assert req0["payload"]["guidance"]["status"] == "success"
    second_round_messages = fake_llm.messages_history[1]
    tool_messages = [m for m in second_round_messages if getattr(m, "type", "") == "tool"]
    assert tool_messages, "expected tool message to be appended"
    tool_result = json.loads(tool_messages[-1].content)
    assert tool_result["status"] == "error"
