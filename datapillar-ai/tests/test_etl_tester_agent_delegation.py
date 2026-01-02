import json

import pytest

from src.modules.etl.agents.tester_agent import TesterAgent
from src.modules.etl.schemas.plan import Job, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult, DataTarget
from src.modules.etl.schemas.state import AgentState


class _FakeResponse:
    def __init__(self, *, content: str):
        self.content = content


class _FakeLLM:
    def __init__(self, *, content: str):
        self._content = content

    async def ainvoke(self, messages):
        return _FakeResponse(content=self._content)


@pytest.mark.asyncio
async def test_tester_agent_enqueues_delegate_on_failed_review(monkeypatch):
    fake_llm = _FakeLLM(
        content=json.dumps(
            {
                "passed": False,
                "score": 60,
                "summary": "SQL 有明显问题",
                "issues": ["JOIN 条件不正确"],
                "warnings": ["可能存在全表扫描"],
            },
            ensure_ascii=False,
        )
    )

    def fake_call_llm(*args, **kwargs):
        return fake_llm

    monkeypatch.setattr("src.modules.etl.agents.tester_agent.call_llm", fake_call_llm)

    analysis = AnalysisResult(
        user_query="把 ods.order 清洗到 dwd.order_clean",
        summary="把 ods.order 清洗到 dwd.order_clean",
        steps=[],
        final_target=DataTarget(table_name="dwd.order_clean"),
        ambiguities=[],
        confidence=0.9,
    )

    workflow = Workflow(
        name="wf",
        jobs=[
            Job(
                id="job_1",
                name="job_1",
                type="HIVE",
                stages=[],
                config={"content": "SELECT 1 AS x"},
                config_generated=True,
            )
        ],
    )

    agent = TesterAgent()
    state = AgentState(
        user_input="帮我生成 ETL 工作流",
        analysis_result=analysis.model_dump(),
        architecture_plan=workflow.model_dump(),
        pending_requests=[],
        iteration_count=0,
        max_iterations=3,
    )

    cmd = await agent(state)
    assert cmd.update["current_agent"] == "tester_agent"
    assert cmd.update["test_result"]["passed"] is False
    assert cmd.update["iteration_count"] == 1

    pending = cmd.update["pending_requests"]
    assert pending, "expected a delegate request to be enqueued"
    assert pending[0]["kind"] == "delegate"
    assert pending[0]["target_agent"] == "developer_agent"
    assert pending[0]["resume_to"] == "tester_agent"
