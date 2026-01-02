import pytest

from src.modules.etl.agents.architect_agent import ArchitectAgent
from src.modules.etl.agents.developer_agent import DeveloperAgent
import src.modules.etl.agents.tester_agent as tester_agent
from src.modules.etl.schemas.state import AgentState


@pytest.mark.asyncio
async def test_architect_agent_delegates_when_missing_analysis_result():
    agent = ArchitectAgent()
    state = AgentState(user_input="随便一个需求", analysis_result=None, pending_requests=[])
    cmd = await agent(state)

    assert cmd.update["current_agent"] == "architect_agent"
    assert "error" not in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "delegate"
    assert req0["target_agent"] == "analyst_agent"
    assert req0["resume_to"] == "architect_agent"


@pytest.mark.asyncio
async def test_developer_agent_delegates_when_missing_architecture_plan():
    agent = DeveloperAgent()
    state = AgentState(user_input="随便一个需求", architecture_plan=None, pending_requests=[])
    cmd = await agent(state)

    assert cmd.update["current_agent"] == "developer_agent"
    assert "error" not in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "delegate"
    assert req0["target_agent"] == "architect_agent"
    assert req0["resume_to"] == "developer_agent"


@pytest.mark.asyncio
async def test_tester_agent_delegates_to_developer_when_sql_missing():
    agent = tester_agent.TesterAgent()
    state = AgentState(
        user_input="随便一个需求",
        analysis_result={"user_query": "q", "summary": "s", "steps": [], "confidence": 1.0},
        architecture_plan={
            "name": "wf",
            "description": "",
            "env": "dev",
            "jobs": [
                {
                    "id": "job_1",
                    "name": "j1",
                    "type": "HIVE",
                    "depends": [],
                    "step_ids": [],
                    "stages": [
                        {
                            "stage_id": 1,
                            "name": "s1",
                            "description": "d",
                            "input_tables": ["ods.order"],
                            "output_table": "temp.tmp1",
                            "is_temp_table": True,
                        }
                    ],
                    "input_tables": ["ods.order"],
                    "output_table": "dwd.order_clean",
                    "config": {},
                    "config_generated": False,
                    "config_validated": False,
                }
            ],
            "risks": [],
            "decision_points": [],
            "confidence": 1.0,
        },
        pending_requests=[],
        metadata={},
    )
    cmd = await agent(state)

    assert cmd.update["current_agent"] == "tester_agent"
    assert "error" not in cmd.update
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "delegate"
    assert req0["target_agent"] == "developer_agent"
    assert req0["resume_to"] == "tester_agent"
