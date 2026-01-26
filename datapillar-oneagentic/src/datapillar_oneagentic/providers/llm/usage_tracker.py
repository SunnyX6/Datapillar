"""
LLM token usage normalization.

Goals:
- Normalize usage fields across vendors and LangChain wrappers
- Expose only real token counts returned by the LLM; None when unavailable
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from datapillar_oneagentic.messages import Message


@dataclass(frozen=True, slots=True)
class TokenUsage:
    """
    Normalized token usage.

    Compatible vendors:
    - OpenAI: prompt_tokens, completion_tokens, cached_tokens, reasoning_tokens
    - Anthropic: input_tokens, output_tokens, cache_creation_input_tokens, cache_read_input_tokens
    - GLM/DeepSeek: same as OpenAI format
    """

    input_tokens: int
    """Input token count."""

    output_tokens: int
    """Output token count."""

    total_tokens: int
    """Total token count."""

    # === Cached tokens ===

    cached_tokens: int = 0
    """OpenAI cached input tokens."""

    cache_creation_tokens: int = 0
    """Anthropic cache creation tokens."""

    cache_read_tokens: int = 0
    """Anthropic cache read tokens."""

    # === Reasoning tokens ===

    reasoning_tokens: int = 0
    """Reasoning token count (OpenAI o1/o3, GLM thinking)."""


def _safe_int(v: Any) -> int | None:
    if v is None:
        return None
    if isinstance(v, bool):
        return None
    if isinstance(v, int):
        return v
    if isinstance(v, float):
        return int(v)
    if isinstance(v, str) and v.strip().isdigit():
        return int(v.strip())
    return None


def _parse_usage(usage: dict[str, Any]) -> dict[str, int | None]:
    """
    Parse vendor-specific usage fields and extract token usage.

    Supported vendors and fields:
    - OpenAI: prompt_tokens, completion_tokens, total_tokens
             prompt_tokens_details.cached_tokens
             completion_tokens_details.reasoning_tokens
    - Anthropic: input_tokens, output_tokens
                 cache_creation_input_tokens, cache_read_input_tokens
    - GLM: input_tokens, output_tokens, total_tokens
           input_token_details.cache_read
           output_token_details.reasoning
    - DeepSeek: same as OpenAI format
    """
    # Base fields
    input_tokens = _safe_int(usage.get("prompt_tokens")) or _safe_int(usage.get("input_tokens"))
    output_tokens = _safe_int(usage.get("completion_tokens")) or _safe_int(
        usage.get("output_tokens")
    )
    total = _safe_int(usage.get("total_tokens"))

    if total is None and input_tokens is not None and output_tokens is not None:
        total = input_tokens + output_tokens

    # OpenAI cached tokens (prompt_tokens_details)
    cached_tokens = 0
    prompt_details = usage.get("prompt_tokens_details")
    if isinstance(prompt_details, dict):
        cached_tokens = _safe_int(prompt_details.get("cached_tokens")) or 0

    # GLM cached tokens (input_token_details)
    input_details = usage.get("input_token_details")
    if isinstance(input_details, dict):
        cached_tokens = cached_tokens or _safe_int(input_details.get("cache_read")) or 0

    # OpenAI reasoning tokens (completion_tokens_details, o1/o3 models)
    reasoning_tokens = 0
    completion_details = usage.get("completion_tokens_details")
    if isinstance(completion_details, dict):
        reasoning_tokens = _safe_int(completion_details.get("reasoning_tokens")) or 0

    # GLM reasoning tokens (output_token_details)
    output_details = usage.get("output_token_details")
    if isinstance(output_details, dict):
        reasoning_tokens = reasoning_tokens or _safe_int(output_details.get("reasoning")) or 0

    # Anthropic cached tokens (top-level)
    cache_creation_tokens = _safe_int(usage.get("cache_creation_input_tokens")) or 0
    cache_read_tokens = _safe_int(usage.get("cache_read_input_tokens")) or 0

    return {
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total,
        "cached_tokens": cached_tokens,
        "cache_creation_tokens": cache_creation_tokens,
        "cache_read_tokens": cache_read_tokens,
        "reasoning_tokens": reasoning_tokens,
    }


def _build_usage(parsed: dict[str, int | None]) -> TokenUsage | None:
    """Build TokenUsage from parsed results."""
    input_tokens = parsed.get("input_tokens")
    output_tokens = parsed.get("output_tokens")
    total = parsed.get("total_tokens")

    if input_tokens is None or output_tokens is None or total is None:
        return None

    return TokenUsage(
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        total_tokens=total,
        cached_tokens=parsed.get("cached_tokens") or 0,
        cache_creation_tokens=parsed.get("cache_creation_tokens") or 0,
        cache_read_tokens=parsed.get("cache_read_tokens") or 0,
        reasoning_tokens=parsed.get("reasoning_tokens") or 0,
    )


def extract_usage(msg: Any) -> TokenUsage | None:
    """
    Extract token usage from an LLM response.

    Supported input types:
    - Message (framework standard)
    - LangChain message objects (usage_metadata/response_metadata)
    - dict (raw API response)

    Returns:
    - TokenUsage: input/output/total tokens plus cached/reasoning tokens
    - None: unable to extract (LLM did not return usage info)
    """
    if msg is None:
        return None

    # Try extracting from LangChain message objects.
    usage_meta = getattr(msg, "usage_metadata", None)
    if isinstance(usage_meta, dict):
        parsed = _parse_usage(usage_meta)
        result = _build_usage(parsed)
        if result:
            return result

    resp_meta = getattr(msg, "response_metadata", None)
    if isinstance(resp_meta, dict):
        token_usage = resp_meta.get("token_usage") or resp_meta.get("usage")
        if isinstance(token_usage, dict):
            parsed = _parse_usage(token_usage)
            result = _build_usage(parsed)
            if result:
                return result

    if isinstance(msg, dict):
        usage = msg.get("usage") or msg.get("token_usage")
        if isinstance(usage, dict):
            parsed = _parse_usage(usage)
            result = _build_usage(parsed)
            if result:
                return result

    if isinstance(msg, Message):
        usage_meta = msg.metadata.get("usage_metadata")
        if isinstance(usage_meta, dict):
            parsed = _parse_usage(usage_meta)
            result = _build_usage(parsed)
            if result:
                return result
        response_meta = msg.metadata.get("response_metadata")
        if isinstance(response_meta, dict):
            token_usage = response_meta.get("token_usage") or response_meta.get("usage")
            if isinstance(token_usage, dict):
                parsed = _parse_usage(token_usage)
                result = _build_usage(parsed)
                if result:
                    return result

    return None
