"""
LLM error definitions.
"""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.categories import LLMErrorCategory


class RetryableError(Exception):
    """Retryable error (wraps original exception)."""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class NonRetryableError(Exception):
    """Non-retryable error (wraps original exception)."""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class LLMError(Exception):
    """LLM error with category and recovery action."""

    def __init__(
        self,
        message: str,
        *,
        category: LLMErrorCategory,
        action: RecoveryAction,
        provider: str | None = None,
        model: str | None = None,
        agent_id: str | None = None,
        original: Exception | None = None,
        raw: Any | None = None,
        parsing_error: Any | None = None,
    ) -> None:
        super().__init__(message)
        self.category = category
        self.action = action
        self.provider = provider
        self.model = model
        self.agent_id = agent_id
        self.original = original
        self.raw = raw
        self.parsing_error = parsing_error

    def attach_agent_id(self, agent_id: str) -> None:
        """Attach agent ID when unknown at LLM layer."""
        self.agent_id = agent_id

    def to_dict(self) -> dict[str, Any]:
        """Convert to a structured dict (for logs/SSE)."""
        return {
            "category": self.category.value,
            "action": self.action.value,
            "provider": self.provider,
            "model": self.model,
            "agent_id": self.agent_id,
            "message": str(self),
        }
