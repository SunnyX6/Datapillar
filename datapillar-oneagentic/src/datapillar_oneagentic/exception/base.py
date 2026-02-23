# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""
Exception base types.
"""

from __future__ import annotations

from enum import Enum
from typing import Any


class RecoveryAction(str, Enum):
    """Recovery action."""

    RETRY = "retry"
    FAIL_FAST = "fail_fast"
    CIRCUIT_BREAK = "circuit_break"


class DatapillarException(Exception):
    """Framework base exception."""

    def __init__(
        self,
        message: str,
        *,
        cause: Exception | None = None,
        agent_id: str | None = None,
        provider: str | None = None,
        model: str | None = None,
        status_code: int | None = None,
        vendor_code: str | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.cause = cause
        self.agent_id = agent_id
        self.provider = provider
        self.model = model
        self.status_code = status_code
        self.vendor_code = vendor_code
        self.metadata = metadata or {}

    def attach_agent_id(self, agent_id: str) -> None:
        if self.agent_id is None:
            self.agent_id = agent_id
