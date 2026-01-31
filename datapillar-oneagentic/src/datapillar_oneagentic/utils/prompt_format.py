# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
from __future__ import annotations

from typing import Sequence


def format_markdown(
    *,
    title: str | None,
    sections: Sequence[tuple[str, str | Sequence[str] | None]],
) -> str:
    lines: list[str] = []
    if title:
        lines.append(f"# {title}")
    for section_title, content in sections:
        if not section_title:
            continue
        if lines:
            lines.append("")
        lines.append(f"## {section_title}")
        lines.extend(_normalize_section_content(content))
    return "\n".join(line.rstrip() for line in lines).strip()


def format_code_block(language: str, content: str) -> str:
    body = content.strip("\n")
    return f"```{language}\n{body}\n```"


def _normalize_section_content(content: str | Sequence[str] | None) -> list[str]:
    if content is None:
        return []
    if isinstance(content, str):
        text = content.strip("\n")
        return text.splitlines() if text else []
    lines: list[str] = []
    for item in content:
        text = str(item).strip()
        if text:
            lines.append(f"- {text}")
    return lines
