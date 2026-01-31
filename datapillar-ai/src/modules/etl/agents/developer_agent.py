# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
DeveloperAgent - 数据开发工程师

职责：
- 为每个 Pipeline Job 的 Stage 生成 SQL 脚本
- 完成后交给代码评审员
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.architect import ArchitectOutput
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.table import (
    get_lineage_sql,
    get_table_detail,
    get_table_lineage,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================

_DEVELOPER_TOOLS = [get_table_detail, get_table_lineage, get_lineage_sql]
_DEVELOPER_TOOL_NAMES = [tool.name for tool in _DEVELOPER_TOOLS]
_DEVELOPER_NAV_TOOL = build_knowledge_navigation_tool(_DEVELOPER_TOOL_NAMES)

@agent(
    id="developer",
    name="数据开发工程师",
    description="为每个 Job 的 Stage 生成 SQL 脚本并交付完整 pipeline 方案",
    tools=[_DEVELOPER_NAV_TOOL, *_DEVELOPER_TOOLS],
    deliverable_schema=ArchitectOutput,
    temperature=0.0,
    max_steps=5,
)
class DeveloperAgent:
    """数据开发工程师"""

    SYSTEM_PROMPT = """你是资深数据开发工程师（DeveloperAgent）。

## 你的任务

基于架构师设计的 pipeline 方案，为每个 Job/Stage 生成 SQL 脚本：
1. 从上下文获取架构设计结果（pipeline 输出）
2. 调用工具获取表结构、血缘信息
3. 为每个 Stage 生成 SQL，写入 stages[].sql
4. 完成后委派给代码评审员

## 工作流程

1. 获取架构设计结果（pipeline 输出）
2. 遍历每个 Pipeline 的每个 Job 的每个 Stage
3. 调用 get_table_detail 获取输入/输出表结构
4. 调用 get_lineage_sql 参考历史 SQL
5. 生成 SQL 脚本并写入 stages[].sql
6. 完成后委派给代码评审员

## SQL 编写规范

1. **字段别名**：所有字段必须用 AS 显式指定别名
2. **临时表**：`DROP TABLE IF EXISTS temp.xxx; CREATE TABLE temp.xxx AS ...`
3. **最终表**：最后一个 Stage 必须写入最终目标表
4. **注释**：关键逻辑需要添加注释
5. **临时表命名**：使用 temp.{job_id}_s{stage_id} 规则

## 输出格式

{
  "summary": "整体方案一句话概括",
  "pipelines": [
    {
      "pipeline_id": "p1",
      "pipeline_name": "订单明细线",
      "schedule": "0 2 * * *",
      "depends_on_pipelines": [],
      "jobs": [
        {
          "job_id": "p1_j1",
          "job_name": "清洗合并",
          "description": "清洗订单并补齐用户信息",
          "source_tables": ["catalog.schema.a", "catalog.schema.b"],
          "target_table": "catalog.schema.c",
          "depends_on": [],
          "stages": [
            {
              "stage_id": 1,
              "name": "清洗",
              "description": "清洗订单数据",
              "input_tables": ["catalog.schema.a"],
              "output_table": "temp.p1_j1_s1",
              "is_temp_table": true,
              "sql": "DROP TABLE IF EXISTS temp.p1_j1_s1;\\nCREATE TABLE temp.p1_j1_s1 AS\\nSELECT ..."
            },
            {
              "stage_id": 2,
              "name": "合并",
              "description": "合并用户信息",
              "input_tables": ["temp.p1_j1_s1", "catalog.schema.b"],
              "output_table": "catalog.schema.c",
              "is_temp_table": false,
              "sql": "INSERT OVERWRITE TABLE catalog.schema.c\\nSELECT ..."
            }
          ]
        }
      ],
      "confidence": 0.8
    }
  ]
}

## 输出强约束

1. **只能输出一个 JSON 对象**，不允许 Markdown、代码块或任何额外文本
2. **必须包含所有字段**，字段缺失视为错误

## 重要约束

1. 不允许臆造字段名，必须通过工具验证
2. 所有字段必须有明确的来源
3. **必须保持 pipeline/job/stage 结构与架构师一致，只允许填充 stages[].sql**
4. 每个 Stage 必须填写 sql，不允许空字符串
5. 不允许修改 summary、pipeline_name、schedule、depends_on_pipelines、depends_on、source_tables、target_table
6. 完成后委派给代码评审员
7. **委派时必须在任务描述中携带完整 pipeline JSON**
8. 仅对持久表调用工具验证，临时表无需调用工具

## 数据开发方法论

### SQL 编写原则
1. **字段溯源**：每个输出字段必须有明确的来源
2. **显式别名**：所有字段使用 AS 指定别名
3. **防御性编程**：处理 NULL 值、类型转换

### 临时表规范
- 命名：`temp.{job_id}_s{stage_id}`
- 清理：每个临时表前加 `DROP TABLE IF EXISTS`
- 作用域：只在当前 Job 内有效

### 最终表规范
- 最后一个 Stage 写入目标表

### 质量检查
- 字段数量匹配
- 类型兼容
- NULL 值处理
"""

    async def run(self, ctx: AgentContext) -> ArchitectOutput:
        """执行开发"""
        design = await ctx.get_deliverable("architect")
        if design:
            try:
                upstream_context = json.dumps(design, ensure_ascii=False, indent=2)
            except TypeError:
                upstream_context = str(design)
        else:
            upstream_context = (
                "未获取到结构化架构设计，请基于下发任务/用户输入生成带 SQL 的 pipeline。\n"
                f"用户输入: {ctx.query}"
            )

        # 1. 构建消息
        human_message = f"## 上游架构设计\n{upstream_context}"
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        return output
