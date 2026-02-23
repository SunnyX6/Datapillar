# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Core type definitions.

Public:
- SessionKey: session identifier (namespace + session_id)

Framework internal:
- AgentResult: agent execution result
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Self

from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.messages import Messages


@dataclass(frozen=True, slots=True)
class SessionKey:
    """
    Session identifier (immutable value object).

    Uses namespace + session_id for system-wide consistency.
    All subsystems (Checkpoint, Timeline, SSE, Store) must use this type as key.

    Example:
    ```python
    key = SessionKey(namespace="etl_team", session_id="abc123")

    # As a storage key
    buffer[str(key)]  # "etl_team:abc123"

    # Parse
    key = SessionKey.parse("etl_team:abc123")
    key.namespace  # "etl_team"
    key.session_id  # "abc123"
    ```
    """

    namespace: str
    session_id: str

    def __post_init__(self) -> None:
        if not self.namespace or not self.session_id:
            raise ValueError("namespace and session_id cannot be empty")

    def __str__(self) -> str:
        """Return storage key (Redis/Dict/Checkpoint)."""
        return f"{self.namespace}:{self.session_id}"

    @classmethod
    def parse(cls, key: str) -> Self:
        """Parse from a string."""
        if ":" not in key:
            raise ValueError(f"Invalid SessionKey format: {key}")
        namespace, session_id = key.split(":", 1)
        return cls(namespace=namespace, session_id=session_id)


# ==================== Framework internal types ====================


class AgentResult(BaseModel):
    """
    Agent execution result (framework internal).

    Application code should not build this directly; the framework handles it.

    Status semantics:
    - completed: agent completed successfully
    - failed: execution failed
    - aborted: user aborted interrupt
    """

    model_config = {"arbitrary_types_allowed": True}

    status: ExecutionStatus = Field(..., description="Execution status")
    deliverable: Any | None = Field(None, description="Deliverable")
    deliverable_type: str | None = Field(None, description="Deliverable type")
    error: str | None = Field(None, description="Error message")
    messages: Messages = Field(default_factory=Messages, description="Messages during execution")

    @classmethod
    def completed(
        cls,
        deliverable: Any,
        deliverable_type: str,
        messages: Messages | None = None,
    ) -> AgentResult:
        """Create a success result."""
        return cls(
            status=ExecutionStatus.COMPLETED,
            deliverable=deliverable,
            deliverable_type=deliverable_type,
            messages=messages or Messages(),
        )

    @classmethod
    def failed(
        cls,
        error: str,
        messages: Messages | None = None,
    ) -> AgentResult:
        """Create a failure result."""
        return cls(
            status=ExecutionStatus.FAILED,
            error=error,
            messages=messages or Messages(),
        )

    @classmethod
    def aborted(
        cls,
        error: str | None = None,
        messages: Messages | None = None,
    ) -> AgentResult:
        """Create an aborted result."""
        return cls(
            status=ExecutionStatus.ABORTED,
            error=error,
            messages=messages or Messages(),
        )
