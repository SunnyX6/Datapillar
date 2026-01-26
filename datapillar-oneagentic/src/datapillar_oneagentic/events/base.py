"""Event base class."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from uuid import uuid4


@dataclass
class BaseEvent:
    """
    Base event class.

    All event types should inherit from this class.

    Attributes:
    - event_id: Unique event identifier
    - timestamp: Event timestamp
    - metadata: Additional metadata
    """

    event_id: str = field(default_factory=lambda: str(uuid4()))
    """Unique event identifier."""

    timestamp: datetime = field(default_factory=datetime.now)
    """Event timestamp."""

    metadata: dict[str, Any] = field(default_factory=dict)
    """Additional metadata."""

    @property
    def event_type(self) -> str:
        """Event type name."""
        return self.__class__.__name__

    def to_dict(self) -> dict[str, Any]:
        """Convert to a dictionary."""
        return {
            "event_id": self.event_id,
            "event_type": self.event_type,
            "timestamp": self.timestamp.isoformat(),
            "metadata": self.metadata,
        }
