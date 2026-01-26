"""
Timeline recorder.

Records EventBus events into Timeline.

Uses SessionKey as the storage key and flushes into Timeline after agent_node completes.
"""

from __future__ import annotations

import logging
import threading
from collections import defaultdict
from typing import Any

from datapillar_oneagentic.events.constants import EventType
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.events.types import (
    AgentCompletedEvent,
    AgentFailedEvent,
    AgentStartedEvent,
    DelegationCompletedEvent,
    DelegationStartedEvent,
    SessionCompletedEvent,
    SessionStartedEvent,
)

logger = logging.getLogger(__name__)


class TimelineRecorder:
    """
    Timeline recorder.

    Records EventBus events into an in-memory buffer grouped by SessionKey.
    Call flush() after agent_node finishes to push events into Timeline.

    Example:
    ```python
    recorder = TimelineRecorder(event_bus)
    recorder.register()  # Register EventBus handlers

    # Events are recorded into the buffer automatically...

    # Fetch and clear events for a session
    key = SessionKey(namespace="etl", session_id="abc123")
    entries = recorder.flush(key)
    for entry in entries:
        timeline.add_entry_dict(entry)
    ```
    """

    def __init__(self, event_bus: EventBus) -> None:
        """Initialize."""
        self._event_bus = event_bus
        self._buffer: dict[str, list[dict]] = defaultdict(list)
        self._buffer_lock = threading.Lock()
        self._registered = False

    def register(self) -> None:
        """Register EventBus handlers."""
        if self._registered:
            return

        # Agent events
        self._event_bus.register(AgentStartedEvent, self._on_agent_started)
        self._event_bus.register(AgentCompletedEvent, self._on_agent_completed)
        self._event_bus.register(AgentFailedEvent, self._on_agent_failed)

        # Session events
        self._event_bus.register(SessionStartedEvent, self._on_session_started)
        self._event_bus.register(SessionCompletedEvent, self._on_session_completed)

        # Delegation events
        self._event_bus.register(DelegationStartedEvent, self._on_delegation_started)
        self._event_bus.register(DelegationCompletedEvent, self._on_delegation_completed)

        self._registered = True

    def _record(self, key: SessionKey | None, entry_data: dict) -> None:
        """Record an event into the buffer."""
        if not key:
            return
        with self._buffer_lock:
            self._buffer[str(key)].append(entry_data)

    def flush(self, key: SessionKey) -> list[dict]:
        """Fetch and clear events for the session."""
        with self._buffer_lock:
            entries = self._buffer.pop(str(key), [])
        return entries

    def peek(self, key: SessionKey) -> list[dict]:
        """Peek events for the session (without clearing)."""
        with self._buffer_lock:
            return list(self._buffer.get(str(key), []))

    def clear(self, key: SessionKey | None = None) -> None:
        """Clear the buffer."""
        with self._buffer_lock:
            if key:
                self._buffer.pop(str(key), None)
            else:
                self._buffer.clear()

    # === EventBus handlers ===

    def _on_agent_started(self, source: Any, event: AgentStartedEvent) -> None:
        """Agent started."""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_START.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] started",
                "metadata": {"query": event.query[:200] if event.query else ""},
            },
        )

    def _on_agent_completed(self, source: Any, event: AgentCompletedEvent) -> None:
        """Agent completed."""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_END.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] completed",
                "duration_ms": int(event.duration_ms),
                "metadata": {"result": str(event.result)[:200] if event.result else ""},
            },
        )

    def _on_agent_failed(self, source: Any, event: AgentFailedEvent) -> None:
        """Agent failed."""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_FAILED.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] failed: {event.error}",
                "metadata": {"error_type": event.error_type, "error": event.error},
            },
        )

    def _on_session_started(self, source: Any, event: SessionStartedEvent) -> None:
        """Session started."""
        self._record(
            event.key,
            {
                "event_type": EventType.SESSION_START.value,
                "content": f"Session started: {event.query[:100] if event.query else ''}",
                "metadata": {"query": event.query},
            },
        )

    def _on_session_completed(self, source: Any, event: SessionCompletedEvent) -> None:
        """Session completed."""
        self._record(
            event.key,
            {
                "event_type": EventType.SESSION_END.value,
                "content": "Session completed",
                "duration_ms": int(event.duration_ms),
                "metadata": {
                    "agent_count": event.agent_count,
                    "tool_count": event.tool_count,
                },
            },
        )

    def _on_delegation_started(self, source: Any, event: DelegationStartedEvent) -> None:
        """Delegation started."""
        self._record(
            event.key,
            {
                "event_type": EventType.DELEGATION_START.value,
                "agent_id": event.from_agent_id,
                "content": f"Delegated to [{event.to_agent_id}]: {event.task[:100] if event.task else ''}",
                "metadata": {
                    "from_agent_id": event.from_agent_id,
                    "to_agent_id": event.to_agent_id,
                    "task": event.task,
                    "is_a2a": event.is_a2a,
                },
            },
        )

    def _on_delegation_completed(self, source: Any, event: DelegationCompletedEvent) -> None:
        """Delegation completed."""
        self._record(
            event.key,
            {
                "event_type": EventType.DELEGATION_END.value,
                "agent_id": event.from_agent_id,
                "content": f"Delegation completed: [{event.to_agent_id}]",
                "duration_ms": int(event.duration_ms),
                "metadata": {
                    "from_agent_id": event.from_agent_id,
                    "to_agent_id": event.to_agent_id,
                },
            },
        )
