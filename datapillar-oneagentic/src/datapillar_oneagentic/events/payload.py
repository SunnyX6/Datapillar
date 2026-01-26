"""
Event output schema.

SSE transports event data only; this module provides a unified payload builder.
"""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events.constants import EventType
from datapillar_oneagentic.utils.time import now_ms


def build_event_payload(
    *,
    event: EventType,
    key: SessionKey | None = None,
    agent_id: str | None = None,
    agent_name: str | None = None,
    run_id: str | None = None,
    parent_run_id: str | None = None,
    duration_ms: float | None = None,
    data: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Build a unified event payload (SSE output)."""
    payload: dict[str, Any] = {
        "v": 1,
        "ts": now_ms(),
        "event": event.value,
    }

    if key is not None:
        payload["namespace"] = key.namespace
        payload["session_id"] = key.session_id

    if agent_id or agent_name:
        payload["agent"] = {
            "id": agent_id or "",
            "name": agent_name or "",
        }

    if run_id or parent_run_id:
        payload["span"] = {
            "run_id": run_id,
            "parent_run_id": parent_run_id,
        }

    if duration_ms is not None:
        payload["duration_ms"] = int(duration_ms)

    if data is not None:
        payload["data"] = data

    return payload
