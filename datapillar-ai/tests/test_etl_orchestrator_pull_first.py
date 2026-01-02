import pytest

from src.modules.etl.orchestrator import EtlOrchestrator
from src.modules.etl.schemas.state import AgentState


@pytest.mark.asyncio
async def test_blackboard_router_defaults_to_analyst_agent_without_forcing_knowledge():
    orchestrator = EtlOrchestrator.__new__(EtlOrchestrator)
    state = AgentState(
        current_agent="blackboard_router",
        user_input="帮我清洗订单数据",
        pending_requests=[],
        metadata={},
        agent_contexts={},
        analysis_result=None,
        architecture_plan=None,
    )

    cmd = await orchestrator._blackboard_router(state)
    assert cmd.update["next_agent"] == "analyst_agent"

