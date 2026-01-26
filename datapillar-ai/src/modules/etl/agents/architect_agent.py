"""
ArchitectAgent - 数据架构师

职责：
- 根据需求分析结果设计技术实现方案
- 规划 Job 和 Stage
- 完成后交给开发工程师
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.workflow import WorkflowOutput
from src.modules.etl.tools.component import list_component
from src.modules.etl.tools.table import get_table_lineage

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================


@agent(
    id="architect",
    name="数据架构师",
    description="根据需求分析结果设计技术实现方案，规划 Job 和 Stage",
    tools=[get_table_lineage, list_component],
    deliverable_schema=WorkflowOutput,
    temperature=0.0,
    max_steps=5,
)
class ArchitectAgent:
    """数据架构师"""

    SYSTEM_PROMPT = """你是资深数据架构师（ArchitectAgent）。

## 你的任务

根据需求分析结果，负责工作流的设计，设计技术实现方案：
1. 决定需要几个 Job（前端节点）
2. 规划每个 Job 的 Stage（SQL 执行单元）
3. 确定 Job 之间的调度依赖
4. 设计完成后委派给数据开发工程师

## 核心概念

- **Workflow**: 工作流，包含多个 Job
- **Job**: 作业，前端一个节点，可包含多个 Stage
- **Stage**: SQL 执行单元，Job 内部的执行阶段
- **depends**: Job 之间的调度依赖

## 工作流程

1. 获取需求分析结果（从上下文）
2. 调用 list_component() 获取组件列表
3. 设计 Job 和 Stage 结构
4. 设计完成后委派给数据开发工程师

## 设计原则

1. **一个 Step 对应一个 Job**：需求分析的每个 Step 映射为一个 Job
2. **临时表作用域**：is_temp_table=true 的表只在 Job 内部有效
3. **依赖关系**：Job 的 depends 表示调度依赖

## 输出格式（JSON）

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
          "sql": null
        }
      ],
      "input_tables": ["catalog.schema.table"],
      "output_table": "catalog.schema.output"
    }
  ],
  "risks": ["风险点"],
  "confidence": 0.8
}

## 重要约束

1. 必须确保 DAG 无环
2. 临时表不能跨 Job 引用
3. 不允许臆造表名，使用工具验证或上下文中的表名
4. 设计完成后委派给数据开发工程师
5. **委派时必须在任务描述中携带完整设计输出**（Workflow JSON）
6. sql 由开发工程师填写，可省略或置空

## 架构设计方法论

### 设计原则
1. **一对一映射**：需求的每个 Step 对应一个 Job
2. **DAG 约束**：Job 之间的依赖必须是有向无环图
3. **作用域隔离**：临时表只在 Job 内部有效

### Job 设计
- id: job_1, job_2, ...
- name: 描述性名称
- stages: 内部执行阶段
- depends: 上游 Job ID 列表

### Stage 设计
- stage_id: 1, 2, 3, ...
- input_tables: 输入表列表
- output_table: 输出表（临时或持久）
- is_temp_table: 临时表标记

### 风险检查
- 环检测：确保 DAG 无环
- 数据依赖：确保所有输入表可用
- 临时表：确保不跨 Job 引用
"""

    async def run(self, ctx: AgentContext) -> WorkflowOutput:
        """执行设计"""
        analysis = await ctx.get_deliverable("analyst")
        if analysis:
            try:
                upstream_context = json.dumps(analysis, ensure_ascii=False, indent=2)
            except TypeError:
                upstream_context = str(analysis)
        else:
            upstream_context = f"未获取到结构化需求分析，请基于下发任务/用户输入进行设计。\n用户输入: {ctx.query}"

        # 1. 构建消息
        human_message = f"## 上游需求分析\n{upstream_context}"
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        if output.confidence < 0.8 and output.risks:
            ctx.interrupt({"message": "设计方案存在风险，请确认", "questions": output.risks})
            output = await ctx.get_structured_output(messages)

        return output
