# -*- coding: utf-8 -*-
"""
ETL 表类工具真实数据库测试（table.py）

要求：只做真实调用，打印输入/输出，不做断言。
"""

from __future__ import annotations

import json
from src.modules.etl.tools.table import (
    get_lineage_sql,
    get_table_detail,
    get_table_lineage,
    list_catalogs,
    list_schemas,
    list_tables,
    search_columns,
    search_tables,
)

# 固定输入（真实库存在的样例，可按需替换）
DEFAULT_CATALOG = "tt"
DEFAULT_SCHEMA = "datapillar"
DEFAULT_TABLE = "ai_llm_usage"
DEFAULT_TABLE_PATH = f"{DEFAULT_CATALOG}.{DEFAULT_SCHEMA}.{DEFAULT_TABLE}"


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


def test_table_tools_real_db() -> None:
    # 1) Catalog 列表
    raw_catalogs = list_catalogs.invoke({"limit": 50})
    _print_tool("list_catalogs", {"limit": 50}, raw_catalogs)

    # 2) Schema 列表（固定 catalog）
    raw_schemas = list_schemas.invoke({"catalog": DEFAULT_CATALOG, "limit": 50})
    _print_tool("list_schemas", {"catalog": DEFAULT_CATALOG, "limit": 50}, raw_schemas)

    # 3) Table 列表（固定 catalog + schema）
    raw_tables = list_tables.invoke(
        {"catalog": DEFAULT_CATALOG, "schema_name": DEFAULT_SCHEMA, "limit": 50}
    )
    _print_tool(
        "list_tables",
        {"catalog": DEFAULT_CATALOG, "schema_name": DEFAULT_SCHEMA, "limit": 50},
        raw_tables,
    )

    # 4) 表详情（固定表路径）
    raw_detail = get_table_detail.invoke({"path": DEFAULT_TABLE_PATH})
    _print_tool("get_table_detail", {"path": DEFAULT_TABLE_PATH}, raw_detail)

    # 5) 表血缘（固定表路径）
    raw_lineage = get_table_lineage.invoke({"path": DEFAULT_TABLE_PATH, "direction": "both"})
    _print_tool(
        "get_table_lineage",
        {"path": DEFAULT_TABLE_PATH, "direction": "both"},
        raw_lineage,
    )

    # 6) 语义搜索（固定关键词）
    raw_search_tables = search_tables.invoke({"query": "订单", "top_k": 5})
    _print_tool("search_tables", {"query": "订单", "top_k": 5}, raw_search_tables)

    raw_search_columns = search_columns.invoke({"query": "金额", "top_k": 5})
    _print_tool("search_columns", {"query": "金额", "top_k": 5}, raw_search_columns)

    # 7) 血缘 SQL（固定输入）
    raw_sql = get_lineage_sql.invoke(
        {"source_tables": [DEFAULT_TABLE_PATH], "target_table": DEFAULT_TABLE_PATH}
    )
    _print_tool(
        "get_lineage_sql",
        {"source_tables": [DEFAULT_TABLE_PATH], "target_table": DEFAULT_TABLE_PATH},
        raw_sql,
    )


if __name__ == "__main__":
    test_table_tools_real_db()
