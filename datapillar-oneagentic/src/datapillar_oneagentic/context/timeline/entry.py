"""
Context timeline submodule - timeline entry.

Records a single execution event.
Note: namespace and session_id are managed by Blackboard and not stored here.
"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, Field

from datapillar_oneagentic.context.checkpoint.types import CheckpointType
from datapillar_oneagentic.events.constants import EventType
from datapillar_oneagentic.utils.time import now_ms


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class TimelineEntry(BaseModel):
    """
    Timeline entry.

    Records a single event and links checkpoints for time travel.
    Note: namespace and session_id are managed by Blackboard.
    """

    # Identity
    id: str = Field(default_factory=_generate_id, description="Event ID")
    seq: int = Field(default=0, description="Sequence")

    # Event info
    event_type: EventType = Field(..., description="Event type")
    agent_id: str | None = Field(default=None, description="Related agent ID")
    content: str = Field(default="", description="Event description")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Extra metadata")

    # Timing
    timestamp_ms: int = Field(default_factory=now_ms, description="Event time")
    duration_ms: int | None = Field(default=None, description="Event duration")

    # Checkpoint support
    checkpoint_id: str | None = Field(
        default=None,
        description="Linked checkpoint ID",
    )
    checkpoint_type: CheckpointType | None = Field(
        default=None,
        description="Checkpoint type",
    )
    parent_checkpoint_id: str | None = Field(
        default=None,
        description="Parent checkpoint ID (for branches)",
    )
    is_checkpoint: bool = Field(
        default=False,
        description="Whether this is a checkpoint event",
    )

    def to_display(self) -> str:
        """Render a display string."""
        agent_part = f"[{self.agent_id}] " if self.agent_id else ""
        duration_part = f" ({self.duration_ms}ms)" if self.duration_ms else ""
        checkpoint_part = " [checkpoint]" if self.is_checkpoint else ""
        return f"{agent_part}{self.event_type.value}: {self.content}{duration_part}{checkpoint_part}"

    def to_dict(self) -> dict:
        """Convert to a dictionary."""
        return self.model_dump(mode="json")

    @classmethod
    def from_dict(cls, data: dict) -> TimelineEntry:
        """Create from a dictionary."""
        return cls.model_validate(data)
