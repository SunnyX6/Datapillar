import pytest

from src.modules.etl.orchestrator import EtlOrchestrator
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState


@pytest.mark.asyncio
async def test_blackboard_router_routes_delegate_to_target():
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)
    req = BlackboardRequest(
        request_id="req_1",
        kind="delegate",
        created_by="tester_agent",
        target_agent="developer_agent",
        resume_to="tester_agent",
        payload={},
    )
    state = AgentState(
        current_agent="tester_agent",
        pending_requests=[req],
        metadata={},
    )

    cmd = await orchestrator._blackboard_router(state)
    assert cmd.update["next_agent"] == "developer_agent"
    assert len(cmd.update["pending_requests"]) == 1


@pytest.mark.asyncio
async def test_blackboard_router_pops_completed_delegate_and_resumes():
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)
    req = BlackboardRequest(
        request_id="req_1",
        kind="delegate",
        created_by="tester_agent",
        target_agent="developer_agent",
        resume_to="tester_agent",
        payload={},
    )
    state = AgentState(
        current_agent="developer_agent",
        pending_requests=[req],
        metadata={},
    )

    cmd = await orchestrator._blackboard_router(state)
    assert cmd.update["pending_requests"] == []
    assert cmd.update["next_agent"] == "tester_agent"
    assert cmd.update["request_results"]["req_1"]["completed_by"] == "developer_agent"
