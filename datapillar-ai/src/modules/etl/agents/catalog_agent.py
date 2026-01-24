"""
CatalogAgent - 元数据问答专员

职责：
- 回答用户关于数据目录/元数据的问题
- 查询 catalog/schema/表结构/字段/血缘
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from pydantic import BaseModel, Field

from datapillar_oneagentic import agent
from src.modules.etl.tools.table import (
    count_catalogs,
    count_schemas,
    count_tables,
    get_table_detail,
    get_table_lineage,
    list_catalogs,
    list_schemas,
    list_tables,
    search_columns,
    search_tables,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== 输出 Schema ====================


class CatalogOption(BaseModel):
    """元数据选项"""

    type: str = Field(..., description="类型: catalog/schema/table/column/lineage")
    name: str = Field(..., description="名称")
    path: str = Field(..., description="完整路径")
    description: str = Field("", description="描述")


class CatalogOutput(BaseModel):
    """元数据查询输出"""

    summary: str = Field(..., description="一句话概括")
    answer: str = Field(..., description="详细回答")
    options: list[CatalogOption] = Field(default_factory=list, description="结构化选项")
    ambiguities: list[str] = Field(default_factory=list, description="需要澄清的问题")
    recommendations: list[str] = Field(default_factory=list, description="推荐引导")
    confidence: float = Field(1.0, ge=0, le=1, description="信息充分程度")


# ==================== Agent 定义 ====================


@agent(
    id="catalog",
    name="元数据专员",
    description="回答元数据相关问题（数据目录、表结构、字段、血缘）",
    tools=[
        count_catalogs,
        count_schemas,
        count_tables,
        list_catalogs,
        list_schemas,
        list_tables,
        search_tables,
        search_columns,
        get_table_detail,
        get_table_lineage,
    ],
    deliverable_schema=CatalogOutput,
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
    {"type": "catalog/schema/table", "name": "名称", "path": "完整路径", "description": "描述"}
  ],
  "ambiguities": ["需要澄清的问题"],
  "recommendations": ["推荐的catalog或者schema", "推荐的表"],
  "confidence": 0.9
}

## 重要约束
1. 所有数据必须来自工具返回，禁止编造
2. 工具返回的路径始终是完整的 catalog.schema.table 格式，直接使用
3. 如果用户问题范围不明确，设置 confidence < 0.7 并在 ambiguities 中询问
4. **必须返回完整的 JSON 格式，包含所有字段**

### 路径格式
所有路径采用三段式：`{catalog}.{schema}.{table}`

## 禁止
1. 禁止滥用工具
2. 禁止胡编乱造
"""

    async def run(self, ctx: AgentContext) -> CatalogOutput:
        """执行查询"""
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        if output.confidence < 0.7 and output.ambiguities:
            ctx.interrupt({"message": "需要更多信息", "questions": output.ambiguities})
            output = await ctx.get_structured_output(messages)

        return output
