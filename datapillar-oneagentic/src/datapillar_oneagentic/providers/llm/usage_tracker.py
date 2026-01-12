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

from datapillar_oneagentic.providers.llm.token_counter import estimate_messages_tokens


@dataclass(frozen=True, slots=True)
class NormalizedTokenUsage:
    """
    标准化 Token 使用量

    兼容多厂商：
    - OpenAI: prompt_tokens, completion_tokens, cached_tokens, reasoning_tokens
    - Anthropic: input_tokens, output_tokens, cache_creation_input_tokens, cache_read_input_tokens
    - GLM/DeepSeek: 同 OpenAI 格式
    """

    prompt_tokens: int
    """输入 token 数（包含缓存命中的部分）"""

    completion_tokens: int
    """输出 token 数"""

    total_tokens: int
    """总 token 数"""

    estimated: bool
    """是否为估算值（True 表示非真实值）"""

    # === 缓存 Token（重要：影响计费）===

    cached_tokens: int = 0
    """
    OpenAI 缓存命中的 prompt token 数
    这部分 token 计费更低（通常是正常价格的 50%）
    """

    cache_creation_tokens: int = 0
    """
    Anthropic 缓存创建 token 数
    首次写入缓存时产生，计费略高于正常价格
    """

    cache_read_tokens: int = 0
    """
    Anthropic 缓存读取 token 数
    从缓存读取时产生，计费更低（通常是正常价格的 10%）
    """

    # === 推理 Token（OpenAI o1/o3 模型）===

    reasoning_tokens: int = 0
    """
    OpenAI o1/o3 模型的推理 token 数
    模型内部"思考"消耗的 token，计入 completion_tokens
    """

    raw_usage: dict[str, Any] | None = None
    """原始 usage 数据（用于调试）"""


@dataclass(frozen=True, slots=True)
class ModelPricingUsd:
    """
    模型定价（USD / 1K tokens）

    支持缓存 token 差异化定价：
    - OpenAI: 缓存命中的 prompt token 通常是正常价格的 50%
    - Anthropic: 缓存读取约 10%，缓存创建约 125%
    """

    prompt_usd_per_1k_tokens: Decimal
    """标准 prompt token 单价"""

    completion_usd_per_1k_tokens: Decimal
    """标准 completion token 单价"""

    # === 缓存定价（可选）===

    cached_prompt_usd_per_1k_tokens: Decimal | None = None
    """
    OpenAI 缓存命中 token 单价
    默认为 prompt 价格的 50%
    """

    cache_creation_usd_per_1k_tokens: Decimal | None = None
    """
    Anthropic 缓存创建 token 单价
    默认为 prompt 价格的 125%
    """

    cache_read_usd_per_1k_tokens: Decimal | None = None
    """
    Anthropic 缓存读取 token 单价
    默认为 prompt 价格的 10%
    """


@dataclass(frozen=True, slots=True)
class UsageCostUsd:
    """
    使用成本（USD）

    包含缓存 token 的成本细分，帮助用户了解缓存带来的节省。
    """

    prompt_cost_usd: Decimal
    """标准 prompt token 成本"""

    completion_cost_usd: Decimal
    """completion token 成本"""

    total_cost_usd: Decimal
    """总成本"""

    # === 缓存成本细分（可选）===

    cached_prompt_cost_usd: Decimal = Decimal("0")
    """OpenAI 缓存命中 token 成本"""

    cache_creation_cost_usd: Decimal = Decimal("0")
    """Anthropic 缓存创建成本"""

    cache_read_cost_usd: Decimal = Decimal("0")
    """Anthropic 缓存读取成本"""

    savings_from_cache_usd: Decimal = Decimal("0")
    """缓存带来的节省金额（相比全价计费）"""


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
    解析多厂商 usage 字段，提取所有 token 信息

    支持的厂商和字段：
    - OpenAI: prompt_tokens, completion_tokens, total_tokens
             prompt_tokens_details.cached_tokens
             completion_tokens_details.reasoning_tokens
    - Anthropic: input_tokens, output_tokens
                 cache_creation_input_tokens, cache_read_input_tokens
    - GLM: input_tokens, output_tokens, total_tokens
           input_token_details.cache_read
           output_token_details.reasoning
    - DeepSeek: 同 OpenAI 格式

    返回：
        dict 包含以下字段：
        - prompt_tokens: 输入 token
        - completion_tokens: 输出 token
        - total_tokens: 总 token
        - cached_tokens: 缓存命中（OpenAI/GLM）
        - cache_creation_tokens: Anthropic 缓存创建
        - cache_read_tokens: Anthropic 缓存读取
        - reasoning_tokens: 推理 token（OpenAI o1/o3, GLM）
    """
    # 基础字段
    prompt = _safe_int(usage.get("prompt_tokens")) or _safe_int(usage.get("input_tokens"))
    completion = _safe_int(usage.get("completion_tokens")) or _safe_int(usage.get("output_tokens"))
    total = _safe_int(usage.get("total_tokens"))

    if total is None and prompt is not None and completion is not None:
        total = int(prompt + completion)

    # OpenAI 缓存 token（在 prompt_tokens_details 中）
    cached_tokens = 0
    prompt_details = usage.get("prompt_tokens_details")
    if isinstance(prompt_details, dict):
        cached_tokens = _safe_int(prompt_details.get("cached_tokens")) or 0

    # GLM 缓存 token（在 input_token_details 中）
    input_details = usage.get("input_token_details")
    if isinstance(input_details, dict):
        cached_tokens = cached_tokens or _safe_int(input_details.get("cache_read")) or 0

    # OpenAI 推理 token（在 completion_tokens_details 中，o1/o3 模型）
    reasoning_tokens = 0
    completion_details = usage.get("completion_tokens_details")
    if isinstance(completion_details, dict):
        reasoning_tokens = _safe_int(completion_details.get("reasoning_tokens")) or 0

    # GLM 推理 token（在 output_token_details 中）
    output_details = usage.get("output_token_details")
    if isinstance(output_details, dict):
        reasoning_tokens = reasoning_tokens or _safe_int(output_details.get("reasoning")) or 0

    # Anthropic 缓存 token（直接在顶层）
    cache_creation_tokens = _safe_int(usage.get("cache_creation_input_tokens")) or 0
    cache_read_tokens = _safe_int(usage.get("cache_read_input_tokens")) or 0

    return {
        "prompt_tokens": prompt,
        "completion_tokens": completion,
        "total_tokens": total,
        "cached_tokens": cached_tokens,
        "cache_creation_tokens": cache_creation_tokens,
        "cache_read_tokens": cache_read_tokens,
        "reasoning_tokens": reasoning_tokens,
    }


def _build_usage(parsed: dict[str, int | None], raw_usage: dict) -> NormalizedTokenUsage | None:
    """从解析结果构建 NormalizedTokenUsage"""
    prompt = parsed.get("prompt_tokens")
    completion = parsed.get("completion_tokens")
    total = parsed.get("total_tokens")

    if prompt is None or completion is None or total is None:
        return None

    return NormalizedTokenUsage(
        prompt_tokens=prompt,
        completion_tokens=completion,
        total_tokens=total,
        estimated=False,
        cached_tokens=parsed.get("cached_tokens") or 0,
        cache_creation_tokens=parsed.get("cache_creation_tokens") or 0,
        cache_read_tokens=parsed.get("cache_read_tokens") or 0,
        reasoning_tokens=parsed.get("reasoning_tokens") or 0,
        raw_usage=raw_usage,
    )


def extract_usage(msg: Any) -> NormalizedTokenUsage | None:
    """
    从 LangChain 消息/输出对象里提取 token usage（真实值优先）

    支持的对象类型：
    - AIMessage（LangChain 标准）
    - dict（原始 API 响应）

    自动提取：
    - 基础 token：prompt_tokens, completion_tokens, total_tokens
    - 缓存 token：cached_tokens（OpenAI）, cache_creation/read_tokens（Anthropic）
    - 推理 token：reasoning_tokens（OpenAI o1/o3）
    """

    if msg is None:
        return None

    if isinstance(msg, AIMessage):
        # 尝试从 usage_metadata 提取（LangChain 标准位置）
        usage_meta = getattr(msg, "usage_metadata", None)
        if isinstance(usage_meta, dict):
            parsed = _parse_usage(usage_meta)
            result = _build_usage(parsed, usage_meta)
            if result:
                return result

        # 尝试从 response_metadata 提取（某些包装器）
        resp_meta = getattr(msg, "response_metadata", None)
        if isinstance(resp_meta, dict):
            token_usage = resp_meta.get("token_usage") or resp_meta.get("usage")
            if isinstance(token_usage, dict):
                parsed = _parse_usage(token_usage)
                result = _build_usage(parsed, token_usage)
                if result:
                    return result

    if isinstance(msg, dict):
        usage = msg.get("usage") or msg.get("token_usage")
        if isinstance(usage, dict):
            parsed = _parse_usage(usage)
            result = _build_usage(parsed, usage)
            if result:
                return result

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
    从配置中解析模型定价（USD / 1K tokens）

    支持的配置格式：
    ```json
    {
        "prompt_usd_per_1k_tokens": "0.01",
        "completion_usd_per_1k_tokens": "0.03",
        // 可选：缓存定价
        "cached_prompt_usd_per_1k_tokens": "0.005",      // OpenAI 缓存命中
        "cache_creation_usd_per_1k_tokens": "0.0125",    // Anthropic 缓存创建
        "cache_read_usd_per_1k_tokens": "0.001"          // Anthropic 缓存读取
    }
    ```
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

    # 解析可选的缓存定价
    cached_prompt = None
    cache_creation = None
    cache_read = None

    try:
        if "cached_prompt_usd_per_1k_tokens" in parsed:
            cached_prompt = Decimal(str(parsed["cached_prompt_usd_per_1k_tokens"]))
        if "cache_creation_usd_per_1k_tokens" in parsed:
            cache_creation = Decimal(str(parsed["cache_creation_usd_per_1k_tokens"]))
        if "cache_read_usd_per_1k_tokens" in parsed:
            cache_read = Decimal(str(parsed["cache_read_usd_per_1k_tokens"]))
    except (InvalidOperation, TypeError):
        pass  # 缓存定价解析失败，使用默认值

    return ModelPricingUsd(
        prompt_usd_per_1k_tokens=prompt,
        completion_usd_per_1k_tokens=completion,
        cached_prompt_usd_per_1k_tokens=cached_prompt,
        cache_creation_usd_per_1k_tokens=cache_creation,
        cache_read_usd_per_1k_tokens=cache_read,
    )


def estimate_cost_usd(
    *, usage: NormalizedTokenUsage, pricing: ModelPricingUsd | None
) -> UsageCostUsd | None:
    """
    计算使用成本（USD）

    支持缓存 token 的差异化计费：
    - OpenAI: cached_tokens 使用 cached_prompt 价格（默认 50% 折扣）
    - Anthropic: cache_creation 和 cache_read 分别计价

    返回包含成本细分和节省金额的 UsageCostUsd。
    """
    if pricing is None:
        return None

    prompt_price = pricing.prompt_usd_per_1k_tokens
    completion_price = pricing.completion_usd_per_1k_tokens

    # 缓存定价（使用默认折扣如果未配置）
    cached_prompt_price = pricing.cached_prompt_usd_per_1k_tokens or (prompt_price * Decimal("0.5"))
    cache_creation_price = pricing.cache_creation_usd_per_1k_tokens or (prompt_price * Decimal("1.25"))
    cache_read_price = pricing.cache_read_usd_per_1k_tokens or (prompt_price * Decimal("0.1"))

    # === 计算各部分成本 ===

    # OpenAI 缓存命中成本
    cached_prompt_cost = (Decimal(usage.cached_tokens) / Decimal(1000)) * cached_prompt_price

    # Anthropic 缓存成本
    cache_creation_cost = (Decimal(usage.cache_creation_tokens) / Decimal(1000)) * cache_creation_price
    cache_read_cost = (Decimal(usage.cache_read_tokens) / Decimal(1000)) * cache_read_price

    # 标准 prompt 成本（扣除缓存部分）
    # OpenAI: prompt_tokens 包含 cached_tokens，需要分开计算
    # Anthropic: input_tokens 不包含缓存 token，但需要加上缓存成本
    non_cached_prompt_tokens = max(0, usage.prompt_tokens - usage.cached_tokens)
    standard_prompt_cost = (Decimal(non_cached_prompt_tokens) / Decimal(1000)) * prompt_price

    # 总 prompt 成本
    prompt_cost = standard_prompt_cost + cached_prompt_cost + cache_creation_cost + cache_read_cost

    # Completion 成本
    completion_cost = (Decimal(usage.completion_tokens) / Decimal(1000)) * completion_price

    # 总成本
    total_cost = prompt_cost + completion_cost

    # === 计算缓存节省 ===
    # 如果全部按标准价格计费的成本
    full_price_prompt_cost = (Decimal(usage.prompt_tokens) / Decimal(1000)) * prompt_price
    full_price_cache_creation = (Decimal(usage.cache_creation_tokens) / Decimal(1000)) * prompt_price
    full_price_cache_read = (Decimal(usage.cache_read_tokens) / Decimal(1000)) * prompt_price
    full_price_total = full_price_prompt_cost + full_price_cache_creation + full_price_cache_read + completion_cost

    savings = full_price_total - total_cost
    if savings < 0:
        savings = Decimal("0")  # 缓存创建可能导致负节省，显示为 0

    return UsageCostUsd(
        prompt_cost_usd=prompt_cost,
        completion_cost_usd=completion_cost,
        total_cost_usd=total_cost,
        cached_prompt_cost_usd=cached_prompt_cost,
        cache_creation_cost_usd=cache_creation_cost,
        cache_read_cost_usd=cache_read_cost,
        savings_from_cache_usd=savings,
    )
