"""
ETL column tool real database test (column.py).

There is no standalone tool currently; printing the tool list is enough.
"""

from __future__ import annotations

from src.modules.etl.tools.column import COLUMN_TOOLS


def test_column_tools_real_db() -> None:
    print("\n" + "=" * 80)
    print("Tool file: column.py")
    print("Input: none")
    print("Output: COLUMN_TOOLS")
    print(COLUMN_TOOLS)


if __name__ == "__main__":
    test_column_tools_real_db()
