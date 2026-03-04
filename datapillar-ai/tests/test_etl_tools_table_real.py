"""
ETL table tools real database test (table.py).

Requirement: run real calls only, print input/output, do not assert.
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

# Fixed inputs (samples expected to exist in real environments; replace as needed).
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
    print(f"Tool: {name}")
    print(f"Input: {payload}")
    print("Output (raw):")
    print(raw)
    pretty = _pretty(raw)
    if pretty != raw:
        print("Output (json):")
        print(pretty)


def test_table_tools_real_db() -> None:
    # 1) Catalog list.
    raw_catalogs = list_catalogs.invoke({"limit": 50})
    _print_tool("list_catalogs", {"limit": 50}, raw_catalogs)

    # 2) Schema list (fixed catalog).
    raw_schemas = list_schemas.invoke({"catalog": DEFAULT_CATALOG, "limit": 50})
    _print_tool("list_schemas", {"catalog": DEFAULT_CATALOG, "limit": 50}, raw_schemas)

    # 3) Table list (fixed catalog + schema).
    raw_tables = list_tables.invoke(
        {"catalog": DEFAULT_CATALOG, "schema_name": DEFAULT_SCHEMA, "limit": 50}
    )
    _print_tool(
        "list_tables",
        {"catalog": DEFAULT_CATALOG, "schema_name": DEFAULT_SCHEMA, "limit": 50},
        raw_tables,
    )

    # 4) Table detail (fixed table path).
    raw_detail = get_table_detail.invoke({"path": DEFAULT_TABLE_PATH})
    _print_tool("get_table_detail", {"path": DEFAULT_TABLE_PATH}, raw_detail)

    # 5) Table lineage (fixed table path).
    raw_lineage = get_table_lineage.invoke({"path": DEFAULT_TABLE_PATH, "direction": "both"})
    _print_tool(
        "get_table_lineage",
        {"path": DEFAULT_TABLE_PATH, "direction": "both"},
        raw_lineage,
    )

    # 6) Semantic search (fixed keywords).
    raw_search_tables = search_tables.invoke({"query": "Order", "top_k": 5})
    _print_tool("search_tables", {"query": "Order", "top_k": 5}, raw_search_tables)

    raw_search_columns = search_columns.invoke({"query": "Amount", "top_k": 5})
    _print_tool("search_columns", {"query": "Amount", "top_k": 5}, raw_search_columns)

    # 7) Lineage SQL (fixed input).
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
