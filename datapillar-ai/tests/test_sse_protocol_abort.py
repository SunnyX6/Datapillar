"""
SSE Agreement session.abort/interrupt Mapping test
"""

from src.modules.etl.schemas.sse import ActivityStatus, RunStatus
from src.modules.etl.sse_protocol import SseRunState, _map_payload


def test_map_payload_session_abort() -> None:
    state = SseRunState(run_id="run-1")
    payload = {
        "event": "session.abort",
        "agent": {"id": "analyst", "name": "Analyst"},
        "data": {"abort": {"message": "Aborted by user"}},
    }

    mapped = _map_payload(payload, state)

    assert mapped is not None
    assert mapped["status"] == RunStatus.ABORTED
    assert mapped["activity"]["status"] == ActivityStatus.ABORTED
    assert mapped["activity"]["event_name"] == "aborted"


def test_map_payload_interrupt_id_passthrough() -> None:
    state = SseRunState(run_id="run-2")
    payload = {
        "event": "agent.interrupt",
        "agent": {"id": "analyst", "name": "Analyst"},
        "data": {"interrupt": {"payload": "need input", "interrupt_id": "iid-1"}},
    }

    mapped = _map_payload(payload, state)

    assert mapped is not None
    assert mapped["activity"]["interrupt"]["interrupt_id"] == "iid-1"
