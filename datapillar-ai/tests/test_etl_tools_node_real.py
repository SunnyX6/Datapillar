# -*- coding: utf-8 -*-
"""
ETL 知识导航工具真实数据库测试（node.py）

要求：只做真实调用，打印输入/输出，不做断言。
"""

from __future__ import annotations

import json

from src.modules.etl.tools.component import COMPONENT_TOOLS
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.registry import REGISTRY
from src.modules.etl.tools.table import TABLE_TOOLS


def _pretty(raw: str) -> str:
    try:
        return json.dumps(json.loads(raw), ensure_ascii=False, indent=2)
    except json.JSONDecodeError:
        return raw


def _print_tool(name: str, payload: dict, raw: str) -> None:
    print("\n" + "=" * 80)
    print(f"工具: {name}")
    print(f"输入: {payload}")
    print("输出(raw):")
    print(raw)
    pretty = _pretty(raw)
    if pretty != raw:
        print("输出(json):")
        print(pretty)


def _collect_allowed_tools() -> list[str]:
    names = [meta.name for meta in REGISTRY.list_all()]
    if names:
        return names
    return [tool.name for tool in (TABLE_TOOLS + COMPONENT_TOOLS)]


def test_node_tool_real_db() -> None:
    allowed_tools = _collect_allowed_tools()
    tool = build_knowledge_navigation_tool(allowed_tools)
    raw = tool.invoke({})
    _print_tool("get_knowledge_navigation", {}, raw)


if __name__ == "__main__":
    test_node_tool_real_db()
