# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

_GRAVITINO_NS_WITH_CATALOG = re.compile(r"^gravitino://([^/]+)/([^/]+)(?:/.*)?$")
_GRAVITINO_NS_ONLY_METALAKE = re.compile(r"^gravitino://([^/]+)$")


def parse_gravitino_namespace(namespace: str) -> tuple[str, str | None] | None:
    """
    解析 Gravitino namespace。

    支持：
    - gravitino://{metalake}/{catalog}
    - gravitino://{metalake}
    """
    raw = (namespace or "").strip()
    if not raw.startswith("gravitino://"):
        return None

    match = _GRAVITINO_NS_WITH_CATALOG.match(raw)
    if match:
        return (match.group(1), match.group(2))

    match = _GRAVITINO_NS_ONLY_METALAKE.match(raw)
    if match:
        return (match.group(1), None)

    return None


def symlink_table_name(facets: Mapping[str, Any] | None) -> str | None:
    """
    从 Spark OpenLineage 的 symlinks facet 中提取逻辑表名（schema.table）。

    symlinks 格式：
    {
        "identifiers": [
            {"namespace": "...", "name": "schema.table", "type": "TABLE"}
        ]
    }
    """
    if not facets:
        return None

    symlinks = facets.get("symlinks")
    if not isinstance(symlinks, dict):
        return None

    identifiers = symlinks.get("identifiers")
    if not isinstance(identifiers, list):
        return None

    for ident in identifiers:
        if not isinstance(ident, dict):
            continue
        if ident.get("type") != "TABLE":
            continue
        name = ident.get("name")
        if isinstance(name, str) and name:
            return name

    return None


def dataset_table_name(namespace: str, name: str, facets: Mapping[str, Any] | None) -> str | None:
    """
    尝试从 Dataset 信息中提取逻辑表名（schema.table）。

    优先级：
    1) symlinks facet
    2) dataset.namespace 为 gravitino:// 时，使用 dataset.name（Gravitino OpenLineage）
    """
    table_name = symlink_table_name(facets)
    if table_name:
        return table_name

    if (namespace or "").startswith("gravitino://"):
        return name

    return None
