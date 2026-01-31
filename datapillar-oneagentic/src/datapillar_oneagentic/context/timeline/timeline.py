# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Context timeline submodule - timeline management.

Records execution events and supports time travel.
Note: namespace and session_id are managed by Blackboard and not stored here.
"""

from __future__ import annotations

import uuid

from pydantic import BaseModel, Field

from datapillar_oneagentic.context.timeline.entry import TimelineEntry
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.context.checkpoint.types import CheckpointType
from datapillar_oneagentic.events.constants import EventType


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class Timeline(BaseModel):
    """
    Timeline.

    Records the full event sequence for a session and supports time travel.
    Note: namespace and session_id are managed by Blackboard.
    """

    # Identity
    id: str = Field(default_factory=_generate_id, description="Timeline ID")

    # Event sequence
    entries: list[TimelineEntry] = Field(default_factory=list, description="Event list")
    next_seq: int = Field(default=1, description="Next sequence")

    # Checkpoint index
    checkpoint_ids: list[str] = Field(
        default_factory=list,
        description="Checkpoint IDs in chronological order",
    )

    # Current state
    current_checkpoint_id: str | None = Field(
        default=None,
        description="Current checkpoint ID",
    )

    # Statistics
    total_duration_ms: int = Field(default=0, description="Total duration")

    def add_entry(
        self,
        event_type: EventType,
        content: str,
        *,
        agent_id: str | None = None,
        metadata: dict | None = None,
        duration_ms: int | None = None,
        checkpoint_id: str | None = None,
        checkpoint_type: CheckpointType | None = None,
        is_checkpoint: bool = False,
    ) -> TimelineEntry:
        """Add an event."""
        entry = TimelineEntry(
            seq=self.next_seq,
            event_type=event_type,
            agent_id=agent_id,
            content=content,
            metadata=metadata or {},
            duration_ms=duration_ms,
            checkpoint_id=checkpoint_id,
            checkpoint_type=checkpoint_type,
            parent_checkpoint_id=self.current_checkpoint_id,
            is_checkpoint=is_checkpoint,
        )

        self.entries.append(entry)
        self.next_seq += 1

        if duration_ms:
            self.total_duration_ms += duration_ms

        if is_checkpoint and checkpoint_id:
            self.checkpoint_ids.append(checkpoint_id)
            self.current_checkpoint_id = checkpoint_id

        return entry

    def add_checkpoint(
        self,
        checkpoint_id: str,
        content: str = "Checkpoint",
        *,
        checkpoint_type: CheckpointType = CheckpointType.AUTO,
        agent_id: str | None = None,
        metadata: dict | None = None,
    ) -> TimelineEntry:
        """Add a checkpoint event."""
        return self.add_entry(
            event_type=EventType.CHECKPOINT_CREATE,
            content=content,
            agent_id=agent_id,
            metadata=metadata,
            checkpoint_id=checkpoint_id,
            checkpoint_type=checkpoint_type,
            is_checkpoint=True,
        )

    def get_entry(self, entry_id: str) -> TimelineEntry | None:
        """Get a specific entry."""
        for entry in self.entries:
            if entry.id == entry_id:
                return entry
        return None

    def get_checkpoint_entry(self, checkpoint_id: str) -> TimelineEntry | None:
        """Get entry for a checkpoint."""
        for entry in self.entries:
            if entry.checkpoint_id == checkpoint_id:
                return entry
        return None

    def get_entries_since(self, timestamp_ms: int) -> list[TimelineEntry]:
        """Get entries after a timestamp."""
        return [e for e in self.entries if e.timestamp_ms >= timestamp_ms]

    def get_agent_entries(self, agent_id: str) -> list[TimelineEntry]:
        """Get entries for a specific agent."""
        return [e for e in self.entries if e.agent_id == agent_id]

    def get_type_entries(self, event_type: EventType) -> list[TimelineEntry]:
        """Get entries of a specific type."""
        return [e for e in self.entries if e.event_type == event_type]

    def get_checkpoint_entries(self) -> list[TimelineEntry]:
        """Get all checkpoint entries."""
        return [e for e in self.entries if e.is_checkpoint]

    def get_latest_checkpoint(self) -> TimelineEntry | None:
        """Get the latest checkpoint."""
        checkpoint_entries = self.get_checkpoint_entries()
        return checkpoint_entries[-1] if checkpoint_entries else None

    def find_checkpoint_before(self, timestamp_ms: int) -> TimelineEntry | None:
        """Find the closest checkpoint before a timestamp."""
        checkpoints = [
            e for e in self.entries
            if e.is_checkpoint and e.timestamp_ms < timestamp_ms
        ]
        return checkpoints[-1] if checkpoints else None

    def truncate_to_checkpoint(self, checkpoint_id: str) -> int:
        """Truncate to a checkpoint (delete later entries)."""
        checkpoint_idx = None
        for i, entry in enumerate(self.entries):
            if entry.checkpoint_id == checkpoint_id:
                checkpoint_idx = i
                break

        if checkpoint_idx is None:
            return 0

        removed_count = len(self.entries) - checkpoint_idx - 1
        self.entries = self.entries[: checkpoint_idx + 1]

        # Update checkpoint list.
        self.checkpoint_ids = [
            cid for cid in self.checkpoint_ids
            if any(e.checkpoint_id == cid for e in self.entries)
        ]

        self.next_seq = self.entries[-1].seq + 1 if self.entries else 1
        self.current_checkpoint_id = checkpoint_id

        return removed_count

    def to_prompt(self, max_entries: int = 20) -> str:
        """Convert to a prompt string."""
        if not self.entries:
            return ""

        recent_entries = self.entries[-max_entries:]
        lines: list[str] = []
        if len(self.entries) > max_entries:
            lines.append(f"(showing last {max_entries} of {len(self.entries)})")
        for entry in recent_entries:
            lines.append(f"- {entry.to_display()}")

        body = "\n".join(lines).strip()
        return format_markdown(
            title="Execution Timeline",
            sections=[("Timeline", body)],
        )

    def get_stats(self) -> dict:
        """Return statistics."""
        type_counts: dict[str, int] = {}
        agents: set[str] = set()

        for entry in self.entries:
            event_type = entry.event_type.value
            type_counts[event_type] = type_counts.get(event_type, 0) + 1
            if entry.agent_id:
                agents.add(entry.agent_id)

        return {
            "total_entries": len(self.entries),
            "checkpoint_count": len(self.checkpoint_ids),
            "agent_count": len(agents),
            "agents": list(agents),
            "total_duration_ms": self.total_duration_ms,
            "type_counts": type_counts,
        }

    def add_entry_dict(self, data: dict) -> TimelineEntry:
        """Add an entry from a dict (used by TimelineRecorder flush)."""
        event_type = data.get("event_type")
        if isinstance(event_type, str):
            event_type = EventType.from_string(event_type)

        return self.add_entry(
            event_type=event_type,
            content=data.get("content", ""),
            agent_id=data.get("agent_id"),
            metadata=data.get("metadata"),
            duration_ms=data.get("duration_ms"),
            checkpoint_id=data.get("checkpoint_id"),
            checkpoint_type=data.get("checkpoint_type"),
            is_checkpoint=data.get("is_checkpoint", False),
        )

    def to_dict(self) -> dict:
        """Serialize to a dictionary."""
        return self.model_dump(mode="json")

    @classmethod
    def from_dict(cls, data: dict) -> Timeline:
        """Deserialize from a dictionary."""
        return cls.model_validate(data)
