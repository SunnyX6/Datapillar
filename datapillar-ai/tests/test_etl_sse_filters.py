import pytest

from src.modules.etl.orchestrator import EtlOrchestrator


@pytest.mark.parametrize(
    ("agent_id", "expected"),
    [
        ("knowledge_agent", False),
        ("analyst_agent", True),
        ("developer_agent", True),
        ("tester_agent", True),
        ("human_in_the_loop", True),
        ("blackboard_router", True),
        ("finalize", True),
    ],
)
def test_should_emit_tool_start_sse_filters_only_knowledge_agent(agent_id: str, expected: bool):
    assert EtlOrchestrator._should_emit_tool_start_sse(agent_id) is expected

