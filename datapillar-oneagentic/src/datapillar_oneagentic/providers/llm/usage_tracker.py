"""
LLM Token 使用量标准化

目标：
- 统一不同厂商/不同 LangChain 包装器的 usage 字段口径
- 只暴露 LLM 返回的真实 token 数，拿不到就是 None
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from langchain_core.messages import AIMessage


@dataclass(frozen=True, slots=True)
class TokenUsage:
    """
    标准化 Token 使用量

    兼容多厂商：
    - OpenAI: prompt_tokens, completion_tokens, cached_tokens, reasoning_tokens
    - Anthropic: input_tokens, output_tokens, cache_creation_input_tokens, cache_read_input_tokens
    - GLM/DeepSeek: 同 OpenAI 格式
    """

    input_tokens: int
    """输入 token 数"""

    output_tokens: int
    """输出 token 数"""

    total_tokens: int
    """总 token 数"""

    # === 缓存 Token ===

    cached_tokens: int = 0
    """OpenAI 缓存命中的 input token 数"""

    cache_creation_tokens: int = 0
    """Anthropic 缓存创建 token 数"""

    cache_read_tokens: int = 0
    """Anthropic 缓存读取 token 数"""

    # === 推理 Token ===

    reasoning_tokens: int = 0
    """推理 token 数（OpenAI o1/o3, GLM thinking）"""


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
    """
    # 基础字段
    input_tokens = _safe_int(usage.get("prompt_tokens")) or _safe_int(usage.get("input_tokens"))
    output_tokens = _safe_int(usage.get("completion_tokens")) or _safe_int(
        usage.get("output_tokens")
    )
    total = _safe_int(usage.get("total_tokens"))

    if total is None and input_tokens is not None and output_tokens is not None:
        total = input_tokens + output_tokens

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
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total,
        "cached_tokens": cached_tokens,
        "cache_creation_tokens": cache_creation_tokens,
        "cache_read_tokens": cache_read_tokens,
        "reasoning_tokens": reasoning_tokens,
    }


def _build_usage(parsed: dict[str, int | None]) -> TokenUsage | None:
    """从解析结果构建 TokenUsage"""
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
    从 LLM 响应中提取 token usage

    支持的对象类型：
    - AIMessage（LangChain 标准）
    - dict（原始 API 响应）

    返回：
    - TokenUsage: 包含 input/output/total tokens 及缓存、推理 token
    - None: 无法提取（LLM 未返回 usage 信息）
    """
    if msg is None:
        return None

    if isinstance(msg, AIMessage):
        # 尝试从 usage_metadata 提取（LangChain 标准位置）
        usage_meta = getattr(msg, "usage_metadata", None)
        if isinstance(usage_meta, dict):
            parsed = _parse_usage(usage_meta)
            result = _build_usage(parsed)
            if result:
                return result

        # 尝试从 response_metadata 提取（某些包装器）
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

    return None
