"""
LLM 使用量（token/费用）标准化与估算

目标：
- 统一不同厂商/不同 LangChain 包装器的 usage 字段口径
- 当拿不到真实 usage 时，提供保守的启发式估算（estimated=true）
- 费用预估：按模型单价（USD / 1K tokens）换算
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage

from src.infrastructure.llm.token_counter import estimate_messages_tokens


@dataclass(frozen=True, slots=True)
class NormalizedTokenUsage:
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int
    estimated: bool
    raw_usage: dict[str, Any] | None = None


@dataclass(frozen=True, slots=True)
class ModelPricingUsd:
    prompt_usd_per_1k_tokens: Decimal
    completion_usd_per_1k_tokens: Decimal


@dataclass(frozen=True, slots=True)
class UsageCostUsd:
    prompt_cost_usd: Decimal
    completion_cost_usd: Decimal
    total_cost_usd: Decimal


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


def _parse_usage(usage: dict[str, Any]) -> tuple[int | None, int | None, int | None]:
    """
    尽可能兼容多厂商 usage 字段：
    - OpenAI: {"prompt_tokens":..,"completion_tokens":..,"total_tokens":..}
    - Anthropic/LangChain: usage_metadata / token_usage / input_tokens/output_tokens
    """

    prompt = _safe_int(usage.get("prompt_tokens")) or _safe_int(usage.get("input_tokens"))
    completion = _safe_int(usage.get("completion_tokens")) or _safe_int(usage.get("output_tokens"))
    total = _safe_int(usage.get("total_tokens"))

    if total is None and prompt is not None and completion is not None:
        total = int(prompt + completion)
    return prompt, completion, total


def extract_usage(msg: Any) -> NormalizedTokenUsage | None:
    """
    从 LangChain 消息/输出对象里提取 token usage（真实值优先）。
    """

    if msg is None:
        return None

    if isinstance(msg, AIMessage):
        usage_meta = getattr(msg, "usage_metadata", None)
        if isinstance(usage_meta, dict):
            prompt, completion, total = _parse_usage(usage_meta)
            if prompt is not None and completion is not None and total is not None:
                return NormalizedTokenUsage(
                    prompt_tokens=prompt,
                    completion_tokens=completion,
                    total_tokens=total,
                    estimated=False,
                    raw_usage=usage_meta,
                )

        resp_meta = getattr(msg, "response_metadata", None)
        if isinstance(resp_meta, dict):
            token_usage = resp_meta.get("token_usage") or resp_meta.get("usage")
            if isinstance(token_usage, dict):
                prompt, completion, total = _parse_usage(token_usage)
                if prompt is not None and completion is not None and total is not None:
                    return NormalizedTokenUsage(
                        prompt_tokens=prompt,
                        completion_tokens=completion,
                        total_tokens=total,
                        estimated=False,
                        raw_usage=token_usage,
                    )

    if isinstance(msg, dict):
        usage = msg.get("usage") or msg.get("token_usage")
        if isinstance(usage, dict):
            prompt, completion, total = _parse_usage(usage)
            if prompt is not None and completion is not None and total is not None:
                return NormalizedTokenUsage(
                    prompt_tokens=prompt,
                    completion_tokens=completion,
                    total_tokens=total,
                    estimated=False,
                    raw_usage=usage,
                )

    return None


def estimate_usage(
    *, prompt_messages: list[BaseMessage] | None, completion_text: str | None
) -> NormalizedTokenUsage:
    """
    启发式估算（保守）：用于没有真实 usage 的情况。
    """

    prompt_tokens = int(estimate_messages_tokens(messages=list(prompt_messages or [])))
    completion_tokens = int(
        estimate_messages_tokens(messages=[HumanMessage(content=str(completion_text or ""))])
    )
    total = int(prompt_tokens + completion_tokens)
    raw = {"estimated": True, "method": "heuristic"}
    return NormalizedTokenUsage(
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        total_tokens=total,
        estimated=True,
        raw_usage=raw,
    )


def parse_pricing(config_json: Any) -> ModelPricingUsd | None:
    """
    从 ai_model.config_json 解析单价（USD / 1K tokens）。
    """

    if config_json is None:
        return None
    if isinstance(config_json, str):
        try:
            parsed = json.loads(config_json)
        except Exception:
            return None
    elif isinstance(config_json, dict):
        parsed = config_json
    else:
        return None

    try:
        prompt = Decimal(str(parsed.get("prompt_usd_per_1k_tokens")))
        completion = Decimal(str(parsed.get("completion_usd_per_1k_tokens")))
    except (InvalidOperation, TypeError):
        return None

    if prompt < 0 or completion < 0:
        return None
    return ModelPricingUsd(prompt_usd_per_1k_tokens=prompt, completion_usd_per_1k_tokens=completion)


def estimate_cost_usd(
    *, usage: NormalizedTokenUsage, pricing: ModelPricingUsd | None
) -> UsageCostUsd | None:
    if pricing is None:
        return None

    prompt_cost = (Decimal(usage.prompt_tokens) / Decimal(1000)) * pricing.prompt_usd_per_1k_tokens
    completion_cost = (
        Decimal(usage.completion_tokens) / Decimal(1000)
    ) * pricing.completion_usd_per_1k_tokens
    total_cost = prompt_cost + completion_cost
    return UsageCostUsd(
        prompt_cost_usd=prompt_cost,
        completion_cost_usd=completion_cost,
        total_cost_usd=total_cost,
    )
