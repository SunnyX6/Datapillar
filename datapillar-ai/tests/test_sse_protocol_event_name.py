"""
SSE 协议 event_name 映射测试
"""

from src.modules.etl.schemas.sse import ActivityEvent, ActivityStatus
from src.modules.etl.sse_protocol import _build_activity


def test_tool_event_name_prefix() -> None:
    activity = _build_activity(
        agent_cn="需求分析师",
        agent_en="analyst",
        summary="",
        status=ActivityStatus.RUNNING,
        event=ActivityEvent.TOOL,
        event_name="search_tables",
    )
    assert activity.event_name == "invoke search_tables"


def test_llm_event_name_is_llm_when_running() -> None:
    activity = _build_activity(
        agent_cn="需求分析师",
        agent_en="analyst",
        summary="",
        status=ActivityStatus.RUNNING,
        event=ActivityEvent.LLM,
        event_name="llm",
    )
    assert activity.event_name == "llm"


def test_llm_event_name_is_final_when_done() -> None:
    activity = _build_activity(
        agent_cn="需求分析师",
        agent_en="analyst",
        summary="",
        status=ActivityStatus.DONE,
        event=ActivityEvent.LLM,
        event_name="llm",
    )
    assert activity.event_name == "final"
