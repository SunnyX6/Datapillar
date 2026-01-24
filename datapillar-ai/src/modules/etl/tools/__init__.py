"""
Agent 工具集

模块结构：
- table.py: 表相关工具（get_table_detail, get_table_lineage, get_lineage_sql）
- column.py: 列相关工具（get_column_valuedomain）
- component.py: 组件工具（list_component）
- recommend.py: 推荐引导工具（recommend_guidance）
- doc.py: 文档指针工具（resolve_doc_pointer）

工具使用框架的 @tool 装饰器定义，自动注册到 ToolRegistry。
Agent 通过工具名引用：tools=["search_tables", "get_table_detail"]
"""

from src.modules.etl.tools.column import COLUMN_TOOLS
from src.modules.etl.tools.component import COMPONENT_TOOLS
from src.modules.etl.tools.table import TABLE_TOOLS

# 核心工具：按需查询细节
DETAIL_TOOLS = TABLE_TOOLS + COLUMN_TOOLS

# 所有工具
ALL_TOOLS = DETAIL_TOOLS + COMPONENT_TOOLS 


__all__ = [
    "TABLE_TOOLS",
    "COLUMN_TOOLS",
    "COMPONENT_TOOLS",
    "DETAIL_TOOLS",
    "ALL_TOOLS",
]
