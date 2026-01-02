import pytest

import src.modules.etl.orchestrator as orchestrator_mod
from src.modules.etl.orchestrator import EtlOrchestrator
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState


@pytest.mark.asyncio
async def test_blackboard_router_prioritizes_human_over_delegate():
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)
    delegate_req = BlackboardRequest(
        request_id="req_delegate",
        kind="delegate",
        created_by="tester_agent",
        target_agent="developer_agent",
        resume_to="tester_agent",
        payload={},
    )
    human_req = BlackboardRequest(
        request_id="req_human",
        kind="human",
        created_by="analyst_agent",
        resume_to="blackboard_router",
        payload={"type": "clarification", "message": "请补充信息"},
    )

    state = AgentState(
        current_agent="blackboard_router",
        pending_requests=[delegate_req, human_req],
        metadata={},
    )

    cmd = await orchestrator._blackboard_router(state)
    assert cmd.update["next_agent"] == "human_in_the_loop"
    assert len(cmd.update["pending_requests"]) == 2


@pytest.mark.asyncio
async def test_human_in_the_loop_handles_non_head_human_request(monkeypatch):
    monkeypatch.setattr(orchestrator_mod, "interrupt", lambda payload: "resp")
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)

    delegate_req = BlackboardRequest(
        request_id="req_delegate",
        kind="delegate",
        created_by="tester_agent",
        target_agent="developer_agent",
        resume_to="tester_agent",
        payload={},
    )
    human_req = BlackboardRequest(
        request_id="req_human",
        kind="human",
        created_by="analyst_agent",
        resume_to="blackboard_router",
        payload={"type": "clarification", "message": "请补充信息"},
    )

    state = AgentState(
        user_input="原始需求",
        pending_requests=[delegate_req, human_req],
        metadata={},
    )

    cmd = await orchestrator._handle_human_in_the_loop(state)
    assert cmd.update["current_agent"] == "human_in_the_loop"
    assert cmd.update["human_responses"]["req_human"] == "resp"
    assert cmd.update["request_results"]["req_human"]["kind"] == "human"
    assert cmd.update["request_results"]["req_human"]["completed_by"] == "human_in_the_loop"
    assert cmd.update["user_input"].endswith("用户补充: resp")
    assert len(cmd.update["pending_requests"]) == 1
    assert cmd.update["pending_requests"][0]["request_id"] == "req_delegate"
    assert cmd.update["pending_requests"][0]["kind"] == "delegate"


@pytest.mark.asyncio
async def test_blackboard_router_creates_error_recovery_request():
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)
    state = AgentState(
        current_agent="analyst_agent",
        user_input="帮我做一个工作流",
        pending_requests=[],
        metadata={},
        error="LLM 输出不是合法 JSON（必须输出纯 JSON）",
        human_request_count=0,
        max_human_requests=6,
        delegation_counters={},
    )

    cmd = await orchestrator._blackboard_router(state)
    assert cmd.update["next_agent"] == "human_in_the_loop"
    assert cmd.update["error"] is None
    assert cmd.update["delegation_counters"]["orchestrator:error_recovery"] == 1
    assert len(cmd.update["pending_requests"]) == 1
    req0 = cmd.update["pending_requests"][0]
    assert req0["kind"] == "human"
    assert req0["payload"]["type"] == "error_recovery"
