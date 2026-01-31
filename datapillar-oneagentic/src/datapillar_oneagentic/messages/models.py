# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class ToolCall(BaseModel):
    """Tool call information (aligned with LLM tool_calls)."""

    id: str
    name: str
    args: dict[str, Any] = Field(default_factory=dict)


class Message(BaseModel):
    """Framework message protocol (does not expose LangChain types)."""

    role: Literal["system", "user", "assistant", "tool"]
    content: str
    name: str | None = None
    id: str | None = None
    tool_calls: list[ToolCall] = Field(default_factory=list)
    tool_call_id: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def system(cls, content: str, **kwargs: Any) -> "Message":
        return cls(role="system", content=content, **kwargs)

    @classmethod
    def user(cls, content: str, **kwargs: Any) -> "Message":
        return cls(role="user", content=content, **kwargs)

    @classmethod
    def assistant(cls, content: str, **kwargs: Any) -> "Message":
        return cls(role="assistant", content=content, **kwargs)

    @classmethod
    def tool(cls, content: str, **kwargs: Any) -> "Message":
        return cls(role="tool", content=content, **kwargs)
