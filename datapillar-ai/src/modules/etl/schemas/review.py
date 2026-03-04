# @author Sunny
# @date 2026-01-27

"""
Review data structure

ReviewerAgent product of:ReviewResult
"""

import json
from typing import Any

from pydantic import BaseModel, Field, field_validator


def _try_parse_json(value: object) -> object:
    """Try parsing the stringified JSON"""
    if not isinstance(value, str):
        return value
    text = value.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return value


class ReviewResult(BaseModel):
    """
    Review result(ReviewerAgent output)

    passed Indicates whether the object under review has passed or not review."""

    passed: bool = Field(..., description="Whether the object under review passes review")
    score: int = Field(..., ge=0, le=100, description="Rating 0-100")
    summary: str = Field(..., description="Overall rating")
    issues: list[str] = Field(default_factory=list, description="Blocking level problem")
    warnings: list[str] = Field(default_factory=list, description="warning/Suggestions")
    review_stage: str = Field(..., description="review stage:design/development")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Other meta information")

    @field_validator("issues", "warnings", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        """fault tolerance:
        null -> empty list,stringification JSON -> parse"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v

    @field_validator("metadata", mode="before")
    @classmethod
    def _parse_metadata(cls, v: object) -> object:
        """fault tolerance:
        null -> empty dictionary,stringification JSON -> parse"""
        v = _try_parse_json(v)
        if v is None:
            return {}
        if isinstance(v, str):
            return {}
        return v
