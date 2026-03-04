# @author Sunny
# @date 2026-02-19

"""
LLM Playground request contract.DEPRECATED:- This module is reserved for historical link compatibility
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class PlaygroundModelConfig(BaseModel):
    """Playground Model parameters."""

    model_config = ConfigDict(populate_by_name=True)

    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0, alias="topP")
    thinking_enabled: bool = Field(default=False, alias="thinkingEnabled")
    system_instruction: str = Field(default="", max_length=2000, alias="systemInstruction")


class PlaygroundChatRequest(BaseModel):
    """Playground chat request."""

    model_config = ConfigDict(populate_by_name=True)

    ai_model_id: int = Field(alias="aiModelId", gt=0)
    message: str
    model_options: PlaygroundModelConfig = Field(
        default_factory=PlaygroundModelConfig,
        alias="modelConfig",
    )
