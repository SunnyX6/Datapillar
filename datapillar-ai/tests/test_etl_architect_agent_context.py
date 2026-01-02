import json

import pytest

from src.modules.etl.agents.architect_agent import ArchitectAgent
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


class _FakeListComponentTool:
    def __init__(self, *, payload: dict):
        self._payload = payload
        self.called_with: list[dict] = []

    def invoke(self, args):
        self.called_with.append(args)
        return json.dumps(self._payload, ensure_ascii=False)


@pytest.mark.asyncio
async def test_architect_agent_injects_etl_pointers_context(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content=json.dumps(
                    {
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
                            }
                        ],
                        "risks": [],
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

    fake_list_component = _FakeListComponentTool(
        payload={
            "status": "success",
            "components": [{"id": 1, "code": "SPARK_SQL", "name": "SparkSQL", "type": "SQL"}],
        }
    )

    monkeypatch.setattr("src.modules.etl.agents.architect_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.architect_agent.list_component", fake_list_component)

    agent = ArchitectAgent()
    state = AgentState(
        user_input="把订单清洗到 dwd.order_clean",
        analysis_result={
            "user_query": "把订单清洗到 dwd.order_clean",
            "summary": "把 ods.order 清洗到 dwd.order_clean",
            "steps": [
                {
                    "step_id": "step_1",
                    "step_name": "清洗订单",
                    "description": "清洗后落表",
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "depends_on": [],
                }
            ],
            "final_target": {"table_name": "dwd.order_clean", "write_mode": "overwrite", "partition_by": ["dt"]},
            "ambiguities": [],
            "confidence": 0.9,
        },
        selected_component="SPARK_SQL",
        agent_contexts={
            AgentType.ARCHITECT: AgentScopedContext(
                agent_type=AgentType.ARCHITECT,
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
                        tools=["get_table_lineage"],
                    ),
                    ETLPointer(
                        element_id="eid_tbl_2",
                        labels=["Knowledge", "Table"],
                        primary_label="Table",
                        name="order_clean",
                        schema_name="dwd",
                        table_name="order_clean",
                        qualified_name="dwd.order_clean",
                        tools=["get_table_lineage"],
                    ),
                ],
                tools=["get_table_lineage", "list_component"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "architect_agent"
    assert cmd.update["architecture_plan"]["jobs"]

    messages = fake_llm.messages_history[0]
    context_payload = json.loads(messages[2].content)
    assert context_payload["tables"] == ["ods.order", "dwd.order_clean"]
    assert context_payload["etl_pointers"][0]["element_id"] == "eid_tbl_1"
    assert fake_list_component.called_with == [{}]


@pytest.mark.asyncio
async def test_architect_agent_rejects_tool_call_not_in_pointers(monkeypatch):
    fake_llm = _FakeLLM(
        responses=[
            _FakeResponse(
                content="",
                tool_calls=[
                    {"name": "get_table_lineage", "args": {"table_name": "ods.not_in_context", "direction": "both"}, "id": "t1"}
                ],
            ),
            _FakeResponse(
                content=json.dumps(
                    {
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
                            }
                        ],
                        "risks": [],
                        "confidence": 0.9,
                    },
                    ensure_ascii=False,
                ),
                tool_calls=[],
            ),
        ]
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    fake_list_component = _FakeListComponentTool(
        payload={
            "status": "success",
            "components": [{"id": 1, "code": "SPARK_SQL", "name": "SparkSQL", "type": "SQL"}],
        }
    )

    monkeypatch.setattr("src.modules.etl.agents.architect_agent.call_llm", fake_call_llm)
    monkeypatch.setattr("src.modules.etl.agents.architect_agent.list_component", fake_list_component)

    agent = ArchitectAgent()
    state = AgentState(
        user_input="把订单清洗到 dwd.order_clean",
        analysis_result={
            "user_query": "把订单清洗到 dwd.order_clean",
            "summary": "把 ods.order 清洗到 dwd.order_clean",
            "steps": [
                {
                    "step_id": "step_1",
                    "step_name": "清洗订单",
                    "description": "清洗后落表",
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "depends_on": [],
                }
            ],
            "final_target": {"table_name": "dwd.order_clean", "write_mode": "overwrite", "partition_by": ["dt"]},
            "ambiguities": [],
            "confidence": 0.9,
        },
        selected_component="SPARK_SQL",
        agent_contexts={
            AgentType.ARCHITECT: AgentScopedContext(
                agent_type=AgentType.ARCHITECT,
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
                        tools=["get_table_lineage"],
                    )
                ],
                tools=["get_table_lineage", "list_component"],
            )
        },
    )

    _ = await agent(state)
    second_round_messages = fake_llm.messages_history[1]
    tool_messages = [m for m in second_round_messages if getattr(m, "type", "") == "tool"]
    assert tool_messages, "expected tool message to be appended"
    tool_result = json.loads(tool_messages[-1].content)
    assert tool_result["status"] == "error"
