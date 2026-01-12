"""
Token 计数（OpenAI 兼容）

原则：
- 优先使用 tiktoken（更贴近 OpenAI 兼容模型的 token 口径）
- 如果 tiktoken 不可用：退回启发式估算（保守，用于防爆与 estimated=true）

说明：
- 本模块只做"计数/估算"，不做厂商计费规则。
"""

from __future__ import annotations

import json
from functools import lru_cache
from typing import Any

from langchain_core.messages import BaseMessage


def _content_to_text(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    try:
        return json.dumps(content, ensure_ascii=False, default=str)
    except Exception:
        return str(content)


def _estimate_heuristic(text: str) -> int:
    t = (text or "").strip()
    if not t:
        return 0

    total = len(t)
    ascii_count = sum(1 for ch in t if ord(ch) < 128)
    ascii_ratio = ascii_count / max(1, total)

    if ascii_ratio >= 0.95:
        return int((total + 3) // 4)
    if ascii_ratio >= 0.6:
        return int((total + 2) // 3)
    return int((total + 1) // 2)


@lru_cache(maxsize=8)
def _get_tiktoken_encoder(encoding_name: str):
    try:
        import tiktoken  # type: ignore[import-not-found]
        return tiktoken.get_encoding(encoding_name)
    except ImportError:
        return None


def estimate_text_tokens(*, text: str, encoding_name: str = "cl100k_base") -> int:
    """
    估算文本 token 数。

    注意：
    - OpenAI 兼容模型的 token 计数以 tiktoken 为主
    - 估算结果用于"裁剪/预算"与 estimated=true 的场景，不用于计费真值
    """

    raw = (text or "").strip()
    if not raw:
        return 0

    try:
        enc = _get_tiktoken_encoder(encoding_name)
        if enc:
            return int(len(enc.encode(raw)))
        return _estimate_heuristic(raw)
    except Exception:
        return _estimate_heuristic(raw)


def estimate_messages_tokens(
    *,
    messages: list[BaseMessage],
    encoding_name: str = "cl100k_base",
    per_message_overhead_tokens: int = 4,
) -> int:
    total = 0
    for m in list(messages or []):
        text = _content_to_text(getattr(m, "content", ""))
        total += estimate_text_tokens(text=text, encoding_name=encoding_name) + int(
            per_message_overhead_tokens
        )
    return int(total)
