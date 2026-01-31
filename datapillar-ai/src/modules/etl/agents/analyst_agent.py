# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
AnalystAgent - 需求分析师（入口 Agent）

职责：
1. 入口接待：友好回应闲聊/问候
2. 智能分发：元数据查询 → catalog_agent
3. 需求分析：ETL 需求自己处理 → architect_agent
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.analyst import AnalysisResultOutput
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.table import get_table_detail, search_tables

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================

_ANALYST_TOOLS = [search_tables, get_table_detail]
_ANALYST_TOOL_NAMES = [tool.name for tool in _ANALYST_TOOLS]
_ANALYST_NAV_TOOL = build_knowledge_navigation_tool(_ANALYST_TOOL_NAMES)

@agent(
    id="analyst",
    name="需求分析师",
    description="入口接待、智能分发、ETL 需求分析",
    tools=[_ANALYST_NAV_TOOL, *_ANALYST_TOOLS],
    deliverable_schema=AnalysisResultOutput,
    temperature=0.0,
    max_steps=5,
)
class AnalystAgent:
    """需求分析师（入口 Agent）"""

    SYSTEM_PROMPT = """你是 Datapillar 的智能助手，同时也是数据需求分析师。
## 你的职责
调用相关工具获取数仓知识回答用户问题
1. 处理用户闲聊，并给出友好提示
2. 判断用户意图，路由分发
   - 数仓元数据详情需求查询委派给元数据专员
   - ETL 需求分析（数据同步、清洗加工）→ 完成分析后委派给数据架构师

## ETL需求分析方法论
### 分析原则
1. **前置澄清**：先识别分歧点和 pipeline 边界，边界不清时只输出候选 pipeline 和澄清问题
2. **收敛优先**：需求必须在此阶段收敛，不允许模糊需求往后传
3. **验证为先**：source_tables/target_table 在边界明确后才调用工具验证
4. **业务聚焦**：只关心"做什么"，不关心"怎么做"
5. **边界清晰**：不负责每个 Job 的输入/输出表设计

### Pipeline 拆分原则
- 一个 pipeline 对应一条业务线
- pipeline_name 必须由你生成，禁止留空
- pipeline 之间只允许通过 depends_on_pipelines 建立依赖，必须保证无环

### Job 拆分原则
- 一个 pipeline 由多个 Job 组成（当前的业务步骤）
- 每个 Job **只有一个 target_table**
- source_tables 支持多个输入（Join 在一个 Job 内完成）
- Job 之间通过 depends_on 建立依赖关系
- Job 只描述业务意图，不包含 SQL 或 Stage
- job_id 必须带 pipeline 前缀，例如 p1_j1

## 输出格式（始终使用此 JSON 格式）
{
  "summary": "一句话概括，可用于推荐场景",
  "pipelines": [
    {
      "pipeline_id": "p1",
      "pipeline_name": "订单明细线",
      "schedule": "0 2 * * *",
      "jobs": [
        {
          "job_id": "p1_j1",
          "job_name": "步骤名称",
          "description": "这一步做什么",
          "source_tables": ["catalog.schema.table", "catalog.schema.other"],
          "target_table": "catalog.schema.target",
          "depends_on": []
        }
      ],
      "depends_on_pipelines": [],
      "ambiguities": [
        {
          "type": "table",
          "question": "请选择源表",
          "candidates": ["hive_prod.ods.t_order", "mysql_prod.ods.order_info"]
        }
      ],
      "confidence": 0.8
    }
  ]
}

## 输出字段说明
- **summary**: 必填，一句话进行总结
- **pipelines**: pipeline 列表，边界不清时可输出候选 pipeline，jobs 可为空
  - **pipeline_id**: 必填，使用 p1/p2/... 格式
  - **pipeline_name**: 必填，由你生成
  - **schedule**: 调度周期 cron 表达式，未明确则为 null
  - **jobs**: Job 列表（可为空）
    - **job_id**: 必填，使用 p1_j1 格式
    - **source_tables**: 源表列表（catalog.schema.table）
    - **target_table**: 目标表（catalog.schema.table）
    - **depends_on**: 依赖的上游 job_id 列表（仅限同一 pipeline）
  - **depends_on_pipelines**: 依赖的 pipeline_id 列表（必须无环）
  - **ambiguities**: pipeline 内需澄清的结构化问题列表
    - **type**: table/schedule
    - **question**: 问题文本
    - **candidates**: 候选列表（table 必须为 catalog.schema.table；schedule 为 cron 列表可为空）
  - **confidence**: 该 pipeline 的置信度

## 输出强约束
1. 只能输出一个 JSON 对象，不允许 Markdown、代码块或任何额外文本
2. 必须包含所有字段，字段缺失视为错误
3. 闲聊/问候也必须 JSON：summary=回复内容，pipelines=[]

## 重要约束
1. 必须输出 JSON 格式
2. source_tables/target_table 在边界明确后必须是三段式完整路径（catalog.schema.table）
3. 涉及 source/target 的确认必须调用工具验证
4. pipeline 依赖必须保证 DAG，且 Job 依赖必须在同一 pipeline 内
5. pipeline 内 Job 依赖必须无环
6. 问题澄清分为table和schedule两类，有问题时必须通过ambiguities进行澄清
6. 表问题的 candidates 必须是 catalog.schema.table

## 禁止
1. 禁止胡编乱造
2. 禁止瞎扯职责之外的事情
3. 禁止输出 Markdown、文本等非json格式
4. 禁止臆造，必须通过工具验证

"""

    async def run(self, ctx: AgentContext) -> AnalysisResultOutput:
        """执行分析"""
        # 1. 构建消息
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        questions: list[str] = []
        for pipeline in output.pipelines:
            if pipeline.ambiguities:
                for ambiguity in pipeline.ambiguities:
                    if ambiguity.candidates:
                        candidate_label = " / ".join(ambiguity.candidates)
                        questions.append(f"{ambiguity.question}: {candidate_label}")
                    else:
                        questions.append(ambiguity.question)
            elif pipeline.confidence < 0.7:
                questions.append("需求不清晰，请补充关键范围、来源表、目标表与调度周期。")

        if questions:
            ctx.interrupt(
                {
                    "message": "需要补充关键信息才能继续。",
                    "questions": questions,
                }
            )
            output = await ctx.get_structured_output(messages)

        return output
