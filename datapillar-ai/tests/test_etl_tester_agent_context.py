import json

import pytest

from src.modules.etl.agents.tester_agent import TesterAgent
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, ETLPointer
from src.modules.etl.schemas.state import AgentState


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
async def test_tester_agent_injects_etl_pointers_context(monkeypatch):
    fake_llm = _FakeLLM(
        content=json.dumps(
            {"passed": True, "score": 90, "summary": "ok", "issues": [], "warnings": []},
            ensure_ascii=False,
        )
    )

    def fake_call_llm(*args, **kwargs):
        assert kwargs.get("enable_json_mode") is True
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.tester_agent.call_llm", fake_call_llm)

    agent = TesterAgent()
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
                    "config": {"content": "SELECT 1;"},
                    "config_generated": True,
                }
            ],
        },
        agent_contexts={
            AgentType.TESTER: AgentScopedContext(
                agent_type=AgentType.TESTER,
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
                        tools=["get_table_columns"],
                    )
                ],
                tools=["get_table_columns", "get_column_value_domain"],
            )
        },
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "tester_agent"
    assert cmd.update["test_result"]["passed"] is True

    messages = fake_llm.messages_history[0]
    context_payload = json.loads(messages[2].content)
    assert context_payload["tables"] == ["ods.order", "dwd.order_clean"]
    assert context_payload["etl_pointers"][0]["element_id"] == "eid_tbl_1"
