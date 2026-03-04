"""
ETL component tool real database test (component.py).

Requirement: run real calls only, print input/output, do not assert.
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
    print(f"Tool: {name}")
    print(f"Input: {payload}")
    print("Output (raw):")
    print(raw)
    pretty = _pretty(raw)
    if pretty != raw:
        print("Output (json):")
        print(pretty)


def test_component_tool_real_db() -> None:
    raw = list_component.invoke({})
    _print_tool("list_component", {}, raw)


if __name__ == "__main__":
    test_component_tool_real_db()
