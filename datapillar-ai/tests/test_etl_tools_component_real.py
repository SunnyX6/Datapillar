# -*- coding: utf-8 -*-
"""
ETL 组件工具真实数据库测试（component.py）

要求：只做真实调用，打印输入/输出，不做断言。
"""

from __future__ import annotations

import json

from src.modules.etl.tools.component import list_component


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


def test_component_tool_real_db() -> None:
    raw = list_component.invoke({})
    _print_tool("list_component", {}, raw)


if __name__ == "__main__":
    test_component_tool_real_db()
