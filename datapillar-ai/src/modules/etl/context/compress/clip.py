"""
Payload 裁剪（确定性）

目标：
- 在预算触发压缩时，对 task_payload/context_payload 做“结构化裁剪”，降低 token 占用。
- 这是“预算层的裁剪”，不是 LLM 总结，不改变事实，仅限制投喂体积。
"""

from __future__ import annotations

import json
from typing import Any


def _clip_text(text: str, *, max_chars: int) -> str:
    if max_chars <= 0:
        return ""
    t = text or ""
    if len(t) <= max_chars:
        return t
    return t[:max_chars]


def _is_primitive(value: Any) -> bool:
    return value is None or isinstance(value, (bool, int, float, str))


def clip_payload(
    value: Any,
    *,
    max_string_chars: int,
    max_list_items: int,
    max_dict_items: int,
    max_depth: int = 6,
) -> Any:
    """
    递归裁剪任意 payload（dict/list/str）。

    规则：
    - str：截断到 max_string_chars
    - list：保留前 max_list_items 项
    - dict：保留前 max_dict_items 个 key（按 key 排序，保证确定性）
    - 超过 max_depth：转为字符串预览（确定性）
    """

    if max_depth <= 0:
        try:
            raw = json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)
        except Exception:
            raw = str(value)
        return _clip_text(raw, max_chars=max_string_chars)

    if _is_primitive(value):
        if isinstance(value, str):
            return _clip_text(value, max_chars=max_string_chars)
        return value

    if isinstance(value, list):
        items = value[: max(0, int(max_list_items))]
        return [
            clip_payload(
                v,
                max_string_chars=max_string_chars,
                max_list_items=max_list_items,
                max_dict_items=max_dict_items,
                max_depth=max_depth - 1,
            )
            for v in items
        ]

    if isinstance(value, dict):
        keys = sorted([k for k in value if isinstance(k, str)])
        kept = keys[: max(0, int(max_dict_items))]
        out: dict[str, Any] = {}
        for k in kept:
            out[k] = clip_payload(
                value.get(k),
                max_string_chars=max_string_chars,
                max_list_items=max_list_items,
                max_dict_items=max_dict_items,
                max_depth=max_depth - 1,
            )
        return out

    # 兜底：序列化为文本
    try:
        raw = json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)
    except Exception:
        raw = str(value)
    return _clip_text(raw, max_chars=max_string_chars)
