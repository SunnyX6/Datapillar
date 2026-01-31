# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Review 数据结构

ReviewerAgent 的产物：ReviewResult
"""

import json
from typing import Any

from pydantic import BaseModel, Field, field_validator


def _try_parse_json(value: object) -> object:
    """尝试解析字符串化的 JSON"""
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
    Review 结果（ReviewerAgent 输出）

    passed 表示被审对象是否通过 review。
    """

    passed: bool = Field(..., description="被审对象是否通过 review")
    score: int = Field(..., ge=0, le=100, description="评分 0-100")
    summary: str = Field(..., description="整体评价")
    issues: list[str] = Field(default_factory=list, description="阻断级问题")
    warnings: list[str] = Field(default_factory=list, description="警告/建议")
    review_stage: str = Field(..., description="review 阶段：design/development")
    metadata: dict[str, Any] = Field(default_factory=dict, description="其他元信息")

    @field_validator("issues", "warnings", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        """容错：null -> 空列表，字符串化 JSON -> 解析"""
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
        """容错：null -> 空字典，字符串化 JSON -> 解析"""
        v = _try_parse_json(v)
        if v is None:
            return {}
        if isinstance(v, str):
            return {}
        return v
