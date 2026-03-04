# @author Sunny
# @date 2026-01-27

"""
Agent Toolset

Module structure:- node.py:Knowledge navigation tools(get_knowledge_navigation)
- table.py:Table related tools(get_table_detail,get_table_lineage,get_lineage_sql)
- column.py:List of related tools(get_column_valuedomain)
- component.py:Component tools(list_component)
- recommend.py:Recommended boot tools(recommend_guidance)
- doc.py:Document Pointer Tool(resolve_doc_pointer)

Tool usage AI project side @etl_tool The decorator is registered to ToolRegistry.Knowledge Navigation Tool Pass build_knowledge_navigation_tool generate(press agent Permission filtering).Agent Referenced by tool name:tools=["search_tables","get_table_detail"]
"""

from src.modules.etl.tools.column import COLUMN_TOOLS
from src.modules.etl.tools.component import COMPONENT_TOOLS
from src.modules.etl.tools.node import NODE_TOOLS
from src.modules.etl.tools.table import TABLE_TOOLS

# core tools:Check details on demand
DETAIL_TOOLS = TABLE_TOOLS + COLUMN_TOOLS

# All tools
ALL_TOOLS = DETAIL_TOOLS + COMPONENT_TOOLS + NODE_TOOLS


__all__ = [
    "TABLE_TOOLS",
    "COLUMN_TOOLS",
    "COMPONENT_TOOLS",
    "NODE_TOOLS",
    "DETAIL_TOOLS",
    "ALL_TOOLS",
]
