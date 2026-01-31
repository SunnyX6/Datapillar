# -*- coding: utf-8 -*-
"""
ETL 列工具真实数据库测试（column.py）

当前没有独立工具，打印工具列表即可。
"""

from __future__ import annotations

from src.modules.etl.tools.column import COLUMN_TOOLS


def test_column_tools_real_db() -> None:
    print("\n" + "=" * 80)
    print("工具文件: column.py")
    print("输入: 无")
    print("输出: COLUMN_TOOLS")
    print(COLUMN_TOOLS)


if __name__ == "__main__":
    test_column_tools_real_db()
