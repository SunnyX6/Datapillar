from __future__ import annotations

import asyncio
import json

import pytest

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.sse.event import SseEvent, SseEventType, SseLevel, SseState
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


def _normalize_enum(value):
    return value.value if hasattr(value, "value") else value


@pytest.mark.asyncio
async def test_stream_manager_replays_buffered_events() -> None:
    manager = StreamManager()
    key = SessionKey(namespace="ns", session_id="s1")
    orchestrator = _StubOrchestrator(
        [
            SseEvent.agent_start(agent_id="a1", agent_name="A1")
            .with_session(namespace=key.namespace, session_id=key.session_id)
            .to_dict(),
            SseEvent.result_event(deliverable={"ok": True}, deliverable_type=None)
            .with_session(namespace=key.namespace, session_id=key.session_id)
            .to_dict(),
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
        SseEventType.AGENT_START.value,
        SseEventType.RESULT.value,
    ]
    assert events[0]["state"] == SseState.THINKING.value
    assert events[0]["level"] == SseLevel.INFO.value
    assert events[1]["state"] == SseState.DONE.value
    assert events[1]["level"] == SseLevel.SUCCESS.value
    assert events[0]["session_id"] == "s1"
    assert events[0]["namespace"] == "ns"


@pytest.mark.asyncio
async def test_stream_manager_abort_emits_aborted_event() -> None:
    manager = StreamManager()
    key = SessionKey(namespace="ns", session_id="s2")
    orchestrator = _BlockingOrchestrator()

    await manager.chat(orchestrator=orchestrator, query="run", key=key)
    await asyncio.sleep(0)
    ok = await manager.abort(key=key)
    assert ok is True

    run = manager._runs[str(key)]
    aborted_payload = next(
        record.payload
        for record in run.buffer
        if _normalize_enum(record.payload.get("event")) == SseEventType.ABORTED.value
    )
    assert _normalize_enum(aborted_payload.get("state")) == SseState.ABORTED.value
    assert _normalize_enum(aborted_payload.get("level")) == SseLevel.WARNING.value
