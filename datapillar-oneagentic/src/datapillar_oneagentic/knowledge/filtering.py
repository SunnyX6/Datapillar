# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-29
"""Knowledge metadata filtering."""

from __future__ import annotations

import re
from typing import Any, Callable, TYPE_CHECKING

from pydantic import BaseModel, Field

from datapillar_oneagentic.knowledge.config import MetadataFilterConfig
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.utils.structured_output import parse_structured_output

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.llm import ResilientChatModel


class MetadataFilterCondition(BaseModel):
    field: str = Field(description="Metadata field name")
    operator: str = Field(default="eq", description="Comparison operator")
    value: str | int | float | list[str] = Field(description="Filter value")
    confidence: float = Field(default=0.0, ge=0, le=1)


class MetadataFilterOutput(BaseModel):
    conditions: list[MetadataFilterCondition] = Field(default_factory=list)


_KV_PATTERN = re.compile(r"\b([a-zA-Z_][\w-]{0,63})\s*[:=]\s*([\w\.-]{1,128})\b")
_VERSION_PATTERN = re.compile(r"\bv?(\d+(?:\.\d+){1,3})\b")
_YEAR_PATTERN = re.compile(r"\b(20\d{2})\b")


def _allowed_fields(config: MetadataFilterConfig) -> set[str]:
    builtin = {"source_id", "doc_id", "doc_title", "version", "status", "chunk_type", "section_path"}
    return set(config.fields.keys()) | builtin


def _rule_conditions(query: str, config: MetadataFilterConfig) -> list[MetadataFilterCondition]:
    conditions: list[MetadataFilterCondition] = []
    if not query:
        return conditions

    allowed = _allowed_fields(config)
    text = query.strip()
    lowered = text.lower()

    for key, value in _KV_PATTERN.findall(text):
        field = key.strip()
        if field not in allowed:
            continue
        conditions.append(
            MetadataFilterCondition(
                field=field,
                operator="eq",
                value=value,
                confidence=0.95,
            )
        )

    if "version" in allowed:
        for match in _VERSION_PATTERN.findall(text):
            conditions.append(
                MetadataFilterCondition(
                    field="version",
                    operator="eq",
                    value=match,
                    confidence=0.7,
                )
            )
        for match in _YEAR_PATTERN.findall(text):
            conditions.append(
                MetadataFilterCondition(
                    field="version",
                    operator="eq",
                    value=match,
                    confidence=0.6,
                )
            )

    if "status" in allowed:
        if "draft" in lowered:
            conditions.append(
                MetadataFilterCondition(
                    field="status",
                    operator="eq",
                    value="draft",
                    confidence=0.6,
                )
            )
        if "published" in lowered:
            conditions.append(
                MetadataFilterCondition(
                    field="status",
                    operator="eq",
                    value="published",
                    confidence=0.6,
                )
            )

    if config.fields:
        for field, values in config.fields.items():
            if field not in allowed:
                continue
            for value in values:
                if not value:
                    continue
                if value.lower() in lowered:
                    conditions.append(
                        MetadataFilterCondition(
                            field=field,
                            operator="eq",
                            value=value,
                            confidence=0.65,
                        )
                    )

    return conditions


async def _llm_conditions(
    *,
    query: str,
    config: MetadataFilterConfig,
    llm_provider: Callable[[], "ResilientChatModel"],
) -> list[MetadataFilterCondition]:
    allowed = sorted(_allowed_fields(config))
    system_prompt = (
        "You extract metadata filters for knowledge retrieval. "
        "Only use the allowed fields. "
        "Return conditions with a confidence score between 0 and 1."
    )
    user_prompt = (
        f"Query: {query}\n"
        f"Allowed fields: {', '.join(allowed)}\n"
        "Return the filters."
    )
    messages = Messages([Message.system(system_prompt), Message.user(user_prompt)])

    llm = llm_provider()
    structured_llm = llm.with_structured_output(
        MetadataFilterOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)
    output = parse_structured_output(result, MetadataFilterOutput, strict=False)
    return output.conditions if output else []


def _filter_by_confidence(
    conditions: list[MetadataFilterCondition],
    min_confidence: float,
    allowed: set[str],
) -> list[MetadataFilterCondition]:
    selected: list[MetadataFilterCondition] = []
    for cond in conditions:
        if cond.field not in allowed:
            continue
        if cond.confidence < min_confidence:
            continue
        selected.append(cond)
    return selected


def _conditions_to_filters(conditions: list[MetadataFilterCondition]) -> dict[str, Any]:
    merged: dict[str, Any] = {}
    for cond in conditions:
        field = cond.field
        value = cond.value
        if field in merged:
            existing = merged[field]
            if isinstance(existing, list):
                if isinstance(value, list):
                    for item in value:
                        if item not in existing:
                            existing.append(item)
                else:
                    if value not in existing:
                        existing.append(value)
            else:
                merged[field] = [existing] + (value if isinstance(value, list) else [value])
        else:
            merged[field] = value
    return merged


async def build_auto_filters(
    *,
    query: str,
    config: MetadataFilterConfig,
    llm_provider: Callable[[], "ResilientChatModel"] | None = None,
) -> dict[str, Any] | None:
    if config.mode != "auto":
        return None

    allowed = _allowed_fields(config)
    conditions = _rule_conditions(query, config)
    selected = _filter_by_confidence(conditions, config.min_confidence, allowed)

    if config.use_llm and llm_provider is not None:
        should_call = not selected
        if selected:
            max_conf = max((cond.confidence for cond in selected), default=0.0)
            should_call = max_conf < config.min_confidence
        if should_call:
            llm_conditions = await _llm_conditions(query=query, config=config, llm_provider=llm_provider)
            selected = _filter_by_confidence(llm_conditions, config.min_confidence, allowed)

    if not selected:
        return None
    return _conditions_to_filters(selected)
