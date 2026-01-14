"""Orchestrator 边界测试

测试核心编排器的边界条件：
1. 无效输入处理
2. 状态恢复逻辑
3. 中断恢复逻辑
"""

from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.runtime.orchestrator import Orchestrator


class _MockStateGraph:
    """Mock StateGraph"""

    def __init__(self, events: list[dict] | None = None):
        self._events = events or []

    def compile(self, checkpointer=None, store=None):
        return _MockCompiledGraph(self._events)


class _MockCompiledGraph:
    """Mock CompiledGraph"""

    def __init__(self, events: list[dict]):
        self._events = events
        self._state = None

    async def aget_state(self, config):
        return self._state

    async def astream(self, input_data, config):
        for event in self._events:
            yield event


@pytest.mark.asyncio
async def test_orchestrator_stream_should_error_when_no_query_and_no_resume_value() -> None:
    """无 query 且无 resume_value 时应返回错误事件"""
    graph = _MockStateGraph()

    orchestrator = Orchestrator(
        namespace="test",
        name="test_team",
        graph=graph,
        entry_agent_id="agent1",
        agent_ids=["agent1"],
        checkpointer=None,
        store=None,
    )

    key = SessionKey(namespace="test", session_id="s1")

    events = []
    async for event in orchestrator.stream(query=None, key=key, resume_value=None):
        events.append(event)

    assert len(events) == 1
    assert events[0]["event"] == "error"
    assert "无效调用" in events[0]["data"]["detail"]


@pytest.mark.asyncio
async def test_orchestrator_stream_should_emit_start_event_with_query() -> None:
    """有 query 时应发送 start 事件"""
    graph = _MockStateGraph(events=[])

    orchestrator = Orchestrator(
        namespace="test",
        name="test_team",
        graph=graph,
        entry_agent_id="agent1",
        agent_ids=["agent1"],
        checkpointer=None,
        store=None,
    )

    key = SessionKey(namespace="test", session_id="s1")

    events = []
    async for event in orchestrator.stream(query="hello", key=key):
        events.append(event)

    # 应该有 start 和 result 事件
    event_types = [e["event"] for e in events]
    assert "start" in event_types
    assert "result" in event_types


@pytest.mark.asyncio
async def test_orchestrator_stream_start_event_should_contain_session_info() -> None:
    """start 事件应包含会话信息"""
    graph = _MockStateGraph(events=[])

    orchestrator = Orchestrator(
        namespace="ns1",
        name="my_team",
        graph=graph,
        entry_agent_id="entry",
        agent_ids=["entry"],
        checkpointer=None,
        store=None,
    )

    key = SessionKey(namespace="ns1", session_id="session123")

    events = []
    async for event in orchestrator.stream(query="test", key=key):
        events.append(event)

    start_event = next(e for e in events if e["event"] == "start")
    assert start_event["data"]["session_id"] == "session123"
    assert start_event["data"]["namespace"] == "ns1"
    assert start_event["data"]["team"] == "my_team"
    assert start_event["data"]["entry_agent"] == "entry"
