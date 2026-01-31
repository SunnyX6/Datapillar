# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
CatalogAgent - 元数据问答专员

职责：
- 回答用户关于数据目录/元数据的问题
- 查询 catalog/schema/表结构/字段/血缘
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.catalog import CatalogResultOutput
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.table import (
    get_table_detail,
    get_table_lineage,
    get_lineage_sql,
    list_catalogs,
    list_schemas,
    list_tables,
    search_columns,
    search_tables,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================

_CATALOG_TOOLS = [
    list_catalogs,
    list_schemas,
    list_tables,
    search_tables,
    search_columns,
    get_table_detail,
    get_table_lineage,
    get_lineage_sql,
]
_CATALOG_TOOL_NAMES = [tool.name for tool in _CATALOG_TOOLS]
_CATALOG_NAV_TOOL = build_knowledge_navigation_tool(_CATALOG_TOOL_NAMES)

@agent(
    id="catalog",
    name="元数据专员",
    description="回答元数据相关问题（数据目录、表结构、字段、血缘）",
    tools=[
        _CATALOG_NAV_TOOL,
        *_CATALOG_TOOLS,
    ],
    deliverable_schema=CatalogResultOutput,
    temperature=0.0,
    max_steps=5,
)
class CatalogAgent:
    """元数据问答专员"""

    SYSTEM_PROMPT = """你是 Datapillar 的元数据问答专员（CatalogAgent）。

## 你的任务

回答用户关于数据目录/元数据的问题：
- "有哪些 catalog/schema/表"
- "某张表有哪些字段/表结构是什么"
- "某张表的上下游血缘概览"

你不做 ETL 需求分析，不生成工作流，不写 SQL。

## 工作流程

1. 分析用户问题，在合适的场景使用合适的工具获取知识
2. **必须调用工具获取数据**，不能凭空回答
3. 基于工具返回的实际数据诚实回答用户的问题

## 输出格式

{
  "summary": "一句话概括查询结果",
  "answer": "详细回答内容",
  "options": [
    {
      "type": "catalog/schema/table",
      "name": "名称",
      "path": "完整路径",
      "description": "描述",
      "tools": ["list_tables"],
      "extra": {"column": "data_type"}
    }
  ],
  "ambiguities": ["需要澄清的问题"],
  "recommendations": ["推荐的catalog或者schema", "推荐的表"],
  "confidence": 0.9
}

## 输出强约束

1. **只能输出一个 JSON 对象**，不允许 Markdown、代码块或任何额外文本
2. **必须包含所有字段**，字段缺失视为错误

## 重要约束
1. 所有数据必须来自工具返回，禁止编造
2. 工具返回的路径始终是完整的 catalog.schema.table 格式，直接使用
3. 如果用户问题范围不明确，设置 confidence < 0.7 并在 ambiguities 中询问
4. **必须遵循输出强约束，返回完整 JSON**

### 路径格式
所有路径采用三段式：`{catalog}.{schema}.{table}`

## 禁止
1. 禁止滥用工具
2. 禁止胡编乱造
"""

    async def run(self, ctx: AgentContext) -> CatalogResultOutput:
        """执行查询"""
        # 1. 构建消息
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)

        # 2. 工具调用循环
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        if output.confidence < 0.7 and output.ambiguities:
            ctx.interrupt({"message": "需要更多信息", "questions": output.ambiguities})
            output = await ctx.get_structured_output(messages)

        return output
