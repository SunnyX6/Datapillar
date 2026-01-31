# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-29
"""Query routing and expansion."""

from __future__ import annotations

import re
from typing import Callable, TYPE_CHECKING

from pydantic import BaseModel, Field

from datapillar_oneagentic.knowledge.config import QueryExpansionConfig, QueryRouterConfig
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.utils.structured_output import parse_structured_output

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.llm import ResilientChatModel


class QueryRouteOutput(BaseModel):
    use_rag: bool = Field(default=True, description="Whether retrieval is needed")
    method: str | None = Field(default=None, description="semantic | hybrid | full_text")
    rerank: bool | None = Field(default=None, description="Enable reranking")
    confidence: float = Field(default=0.0, ge=0, le=1)


class QueryExpansionOutput(BaseModel):
    queries: list[str] = Field(default_factory=list)


class HyDEOutput(BaseModel):
    document: str = Field(default="", description="Hypothetical answer passage")


_QUERY_SPACE = re.compile(r"\s+")


def _normalize_query(text: str) -> str:
    return _QUERY_SPACE.sub(" ", text.strip().lower())


def _unique_queries(items: list[str], *, max_items: int | None = None) -> list[str]:
    seen: set[str] = set()
    selected: list[str] = []
    for item in items:
        if not item:
            continue
        key = _normalize_query(item)
        if not key or key in seen:
            continue
        seen.add(key)
        selected.append(item)
        if max_items is not None and len(selected) >= max_items:
            break
    return selected


async def build_query_route(
    *,
    query: str,
    config: QueryRouterConfig,
    supports_hybrid: bool,
    supports_full_text: bool,
    llm_provider: Callable[[], "ResilientChatModel"] | None = None,
) -> QueryRouteOutput | None:
    mode = (config.mode or "off").lower()
    if mode != "auto":
        return None
    if not config.use_llm or llm_provider is None:
        return None

    text = query.strip()
    if not text:
        return QueryRouteOutput(use_rag=False, confidence=1.0)

    supported_methods = ["semantic"]
    if supports_hybrid:
        supported_methods.append("hybrid")
    if supports_full_text:
        supported_methods.append("full_text")
    method_hint = " / ".join(supported_methods)
    system_prompt = (
        "You are a retrieval router. Decide if the query needs knowledge retrieval. "
        f"If retrieval is needed, optionally select a method ({method_hint}) and "
        "whether to enable reranking."
    )
    user_prompt = (
        f"Query: {text}\n"
        "Return a decision with confidence between 0 and 1."
    )
    messages = Messages([Message.system(system_prompt), Message.user(user_prompt)])

    llm = llm_provider()
    structured_llm = llm.with_structured_output(
        QueryRouteOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)
    output = parse_structured_output(result, QueryRouteOutput, strict=False)
    if output.confidence < config.min_confidence:
        return None

    supported = set(supported_methods)
    method = output.method.lower().strip() if output.method else None
    if method not in supported:
        method = None

    return QueryRouteOutput(
        use_rag=bool(output.use_rag),
        method=method,
        rerank=output.rerank,
        confidence=output.confidence,
    )


async def expand_queries(
    *,
    query: str,
    config: QueryExpansionConfig,
    llm_provider: Callable[[], "ResilientChatModel"] | None = None,
) -> list[str]:
    text = query.strip()
    if not text:
        return []
    mode = (config.mode or "off").lower()
    if mode == "off":
        return [text]
    if not config.use_llm or llm_provider is None:
        return [text]

    base = [text] if config.include_original else []

    if mode == "multi":
        return await _expand_multi_query(text, base, config, llm_provider)
    if mode == "hyde":
        return await _expand_hyde(text, base, config, llm_provider)

    raise ValueError(f"Unsupported expansion mode: {config.mode}")


async def _expand_multi_query(
    query: str,
    base: list[str],
    config: QueryExpansionConfig,
    llm_provider: Callable[[], "ResilientChatModel"],
) -> list[str]:
    system_prompt = (
        "You generate alternative search queries for knowledge retrieval. "
        "Keep them concise and preserve the original intent."
    )
    user_prompt = (
        f"Original query: {query}\n"
        f"Generate up to {config.max_queries} alternative search queries."
    )
    messages = Messages([Message.system(system_prompt), Message.user(user_prompt)])

    llm = llm_provider()
    structured_llm = llm.with_structured_output(
        QueryExpansionOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)
    output = parse_structured_output(result, QueryExpansionOutput, strict=False)
    expanded = [item.strip() for item in (output.queries or []) if item and item.strip()]
    expanded = _unique_queries(expanded, max_items=config.max_queries)
    return _unique_queries(base + expanded)


async def _expand_hyde(
    query: str,
    base: list[str],
    config: QueryExpansionConfig,
    llm_provider: Callable[[], "ResilientChatModel"],
) -> list[str]:
    system_prompt = (
        "You write a concise hypothetical answer passage to help retrieval. "
        "Return a standalone paragraph in the same language as the query."
    )
    user_prompt = f"Query: {query}\nWrite the hypothetical answer passage."
    messages = Messages([Message.system(system_prompt), Message.user(user_prompt)])

    llm = llm_provider()
    structured_llm = llm.with_structured_output(
        HyDEOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)
    output = parse_structured_output(result, HyDEOutput, strict=False)
    doc = output.document.strip() if output.document else ""
    if not doc:
        return base
    expanded = _unique_queries([doc], max_items=1)
    return _unique_queries(base + expanded)
