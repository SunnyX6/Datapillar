from __future__ import annotations

import asyncio
import json

import pytest

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import EventType, build_event_payload
from datapillar_oneagentic.sse.manager import StreamManager


class _StubOrchestrator:
    def __init__(self, events: list[dict]) -> None:
        self._events = events

    async def stream(self, *, query=None, key=None, resume_value=None):
        for event in self._events:
            yield event


class _BlockingOrchestrator:
    async def stream(self, *, query=None, key=None, resume_value=None):
        while True:
            await asyncio.sleep(1)
            if False:
                yield {}


class _StubRequest:
    async def is_disconnected(self) -> bool:
        return False


@pytest.mark.asyncio
async def test_stream_manager() -> None:
    manager = StreamManager()
    key = SessionKey(namespace="ns", session_id="s1")
    orchestrator = _StubOrchestrator(
        [
            build_event_payload(
                event=EventType.AGENT_START,
                key=key,
                agent_id="a1",
                agent_name="A1",
            ),
            build_event_payload(
                event=EventType.AGENT_END,
                key=key,
                agent_id="a1",
                agent_name="A1",
                data={
                    "deliverable": {"ok": True},
                },
            ),
        ]
    )

    await manager.chat(orchestrator=orchestrator, query="hi", key=key)
    run = manager._runs[str(key)]
    await run.running_task

    request = _StubRequest()
    events = []
    async for item in manager.subscribe(request=request, key=key, last_event_id=None):
        events.append(json.loads(item["data"]))

    assert [event["event"] for event in events] == [
        EventType.AGENT_START.value,
        EventType.AGENT_END.value,
    ]
    assert events[0]["session_id"] == "s1"
    assert events[0]["namespace"] == "ns"


@pytest.mark.asyncio
async def test_run_cancels() -> None:
    manager = StreamManager()
    key = SessionKey(namespace="ns", session_id="s2")
    orchestrator = _BlockingOrchestrator()

    await manager.chat(orchestrator=orchestrator, query="run", key=key)
    await asyncio.sleep(0)
    ok = await manager.abort(key=key)
    assert ok is True

    run = manager._runs[str(key)]
    assert run.buffer == []
