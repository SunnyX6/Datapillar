"""
文本预处理
"""

from __future__ import annotations

import re


_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]")
_MULTI_SPACE = re.compile(r"[ \t]+")


def apply_preprocess(text: str, rules: list[str]) -> str:
    output = text
    for rule in rules:
        if rule == "strip":
            output = output.strip()
        elif rule == "normalize_newlines":
            output = output.replace("\r\n", "\n").replace("\r", "\n")
        elif rule == "collapse_whitespace":
            output = _MULTI_SPACE.sub(" ", output)
        elif rule == "remove_control":
            output = _CONTROL_CHARS.sub("", output)
        else:
            raise ValueError(f"不支持的预处理规则: {rule}")
    return output
