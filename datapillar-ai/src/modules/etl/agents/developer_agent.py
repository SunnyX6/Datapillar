"""
DeveloperAgent - 数据开发工程师

职责：
- 为每个 Job 的 Stage 生成 SQL 脚本
- 完成后交给代码评审员
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.workflow import WorkflowOutput
from src.modules.etl.tools.table import (
    get_lineage_sql,
    get_table_detail,
    get_table_lineage,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================


@agent(
    id="developer",
    name="数据开发工程师",
    description="为每个 Job 的 Stage 生成 SQL 脚本并交付完整工作流",
    tools=[get_table_detail, get_table_lineage, get_lineage_sql],
    deliverable_schema=WorkflowOutput,
    temperature=0.0,
    max_steps=5,
)
class DeveloperAgent:
    """数据开发工程师"""

    SYSTEM_PROMPT = """你是资深数据开发工程师（DeveloperAgent）。

## 你的任务

基于架构师设计的 Workflow，为每个 Job/Stage 生成 SQL 脚本并交付完整工作流：
1. 从上下文获取 Workflow（架构设计结果）
2. 调用工具获取表结构、血缘信息
3. 为每个 Stage 生成 SQL，写入 stages[].sql
4. 完成后委派给代码评审员

## 工作流程

1. 获取 Workflow（从上下文）
2. 遍历每个 Job 的每个 Stage
3. 调用 get_table_detail 获取输入/输出表结构
4. 调用 get_lineage_sql 参考历史 SQL
5. 生成 SQL 脚本并写入 stages[].sql
6. 完成后委派给代码评审员

## SQL 编写规范

1. **字段别名**：所有字段必须用 AS 显式指定别名
2. **临时表**：`DROP TABLE IF EXISTS temp.xxx; CREATE TABLE temp.xxx AS ...`
3. **最终表**：最后一个 Stage 必须写入最终目标表
4. **注释**：关键逻辑需要添加注释

## 输出格式

{
  "name": "工作流名称",
  "description": "工作流描述",
  "jobs": [
    {
      "id": "job_1",
      "name": "作业名称",
      "description": "作业描述",
      "depends": [],
      "step_ids": ["s1"],
      "stages": [
        {
          "stage_id": 1,
          "name": "Stage名称",
          "description": "Stage描述",
          "input_tables": ["catalog.schema.table"],
          "output_table": "catalog.schema.output",
          "is_temp_table": false,
          "sql": "DROP TABLE IF EXISTS temp.xxx;\\nCREATE TABLE temp.xxx AS\\nSELECT ..."
        }
      ],
      "input_tables": ["catalog.schema.table"],
      "output_table": "catalog.schema.output"
    }
  ],
  "risks": ["字段映射不明确", "JOIN 条件需确认"],
  "confidence": 0.8
}

## 重要约束

1. 不允许臆造字段名，必须通过工具验证
2. 所有字段必须有明确的来源
3. **必须保持 Job/Stage 结构与架构师一致，只允许填充 stages[].sql**
4. 每个 Stage 必须填写 sql，不允许空字符串
5. confidence < 0.8 且有 risks 时需要澄清
6. 完成后委派给代码评审员
7. **委派时必须在任务描述中携带完整工作流 JSON 与风险列表**

## 数据开发方法论

### SQL 编写原则
1. **字段溯源**：每个输出字段必须有明确的来源
2. **显式别名**：所有字段使用 AS 指定别名
3. **防御性编程**：处理 NULL 值、类型转换

### 临时表规范
- 命名：`temp.{workflow}_{job}_{stage}`
- 清理：每个临时表前加 `DROP TABLE IF EXISTS`
- 作用域：只在当前 Job 内有效

### 最终表规范
- 最后一个 Stage 写入目标表
- 根据 write_mode 选择 INSERT/OVERWRITE

### 质量检查
- 字段数量匹配
- 类型兼容
- NULL 值处理
"""

    async def run(self, ctx: AgentContext) -> WorkflowOutput:
        """执行开发"""
        workflow = await ctx.get_deliverable("architect")
        if workflow:
            try:
                upstream_context = json.dumps(workflow, ensure_ascii=False, indent=2)
            except TypeError:
                upstream_context = str(workflow)
        else:
            upstream_context = f"未获取到结构化架构设计，请基于下发任务/用户输入生成带 SQL 的工作流。\n用户输入: {ctx.query}"

        # 1. 构建消息
        human_message = f"## 上游架构设计\n{upstream_context}"
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        if output.confidence < 0.8 and output.risks:
            ctx.interrupt({"message": "SQL 生成存在不确定性，请确认以下问题", "questions": output.risks})
            output = await ctx.get_structured_output(messages)

        return output
