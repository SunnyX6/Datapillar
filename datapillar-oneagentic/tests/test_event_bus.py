from __future__ import annotations

import pytest

from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.events.types import AgentStartedEvent


@pytest.mark.asyncio
async def test_event_bus_emits_sync_and_async_handlers() -> None:
    bus = EventBus()
    calls: list[str] = []

    def sync_handler(_source, event: AgentStartedEvent) -> None:
        calls.append(f"sync:{event.agent_id}")

    async def async_handler(_source, event: AgentStartedEvent) -> None:
        calls.append(f"async:{event.agent_id}")

    bus.register(AgentStartedEvent, sync_handler)
    bus.register(AgentStartedEvent, async_handler)

    await bus.emit("src", AgentStartedEvent(agent_id="a1", agent_name="A1"))

    assert "sync:a1" in calls
    assert "async:a1" in calls


def test_event_bus_scoped_handlers_isolated() -> None:
    bus = EventBus()
    calls: list[str] = []

    def outer_handler(_source, _event: AgentStartedEvent) -> None:
        calls.append("outer")

    def inner_handler(_source, _event: AgentStartedEvent) -> None:
        calls.append("inner")

    bus.register(AgentStartedEvent, outer_handler)
    assert bus.handler_count(AgentStartedEvent) == 1

    with bus.scoped_handlers():
        assert bus.handler_count(AgentStartedEvent) == 0
        bus.register(AgentStartedEvent, inner_handler)
        assert bus.handler_count(AgentStartedEvent) == 1

    assert bus.handler_count(AgentStartedEvent) == 1
