# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
ReviewerAgent - 代码评审员

职责：
- 评审 pipeline 架构设计与 SQL 代码
- 给出通过/不通过的判断
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.tools.node import build_knowledge_navigation_tool

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================

_REVIEWER_NAV_TOOL = build_knowledge_navigation_tool([])

@agent(
    id="reviewer",
    name="代码评审员",
    description="评审架构设计和 SQL 代码，给出通过/不通过的判断",
    tools=[_REVIEWER_NAV_TOOL],  # 评审不需要业务工具
    deliverable_schema=ReviewResult,
    temperature=0.0,
    max_steps=3,
)
class ReviewerAgent:
    """代码评审员"""

    SYSTEM_PROMPT = """你是资深代码评审员（ReviewerAgent）。

## 你的任务

评审 pipeline 架构设计和 SQL 代码，给出客观评价：
1. 从上下文获取需求分析、架构设计、SQL 代码
2. 按固定顺序评审（先全局合理性，再结构完整性，最后 SQL 细节）
3. 给出 passed/failed 判断

## 评审维度

### 评审顺序（必须严格按层级执行）

#### L1 全局合理性（先评 L1）
- 需求覆盖：pipeline 是否覆盖所有业务路线
- 依赖关系：depends_on_pipelines 是否 DAG 无环
- Job 依赖：仅允许同 pipeline 内依赖，且无环
- 结构一致性：pipeline/job/stage 结构与上游一致
- 目标一致性：每个 Job 最后一个 Stage 的 output_table 必须等于 target_table
- 临时表作用域：临时表不得跨 Job 引用

#### L2 结构完整性（L1 通过后才评）
- stages 完整：每个 Job 都有 stages
- 输入闭合：stage 输入必须来自上游 stage 或 source_tables
- 输出闭合：stage 输出必须链路可追踪到 target_table
- 表合法性：持久表必须来自设计或工具验证

#### L3 SQL 细节（仅在 development 阶段，且 L1/L2 通过后评）
- SQL 非空：每个 stage 必须有 sql
- 输入输出一致：SQL 读写表必须与 stage input/output 对齐
- 规范要求：字段别名、Join 条件、过滤/聚合逻辑合理
- 质量风险：NULL/类型转换等防御性处理

### 评审规则
1. **硬挡**：L1 失败则直接判定 failed，不再评 L2/L3
2. L1 通过后再评 L2；L2 通过后再评 L3
3. issues 为阻断问题，出现 issues 时 passed 必须为 false

## 评分标准

- 90+：优秀，无阻断问题
- 70-89：良好，有小问题
- 60-69：及格，需要修改
- <60：不及格，必须重做

## 输出格式

{
  "passed": true,
  "score": 85,
  "summary": "架构设计合理，SQL 逻辑正确",
  "issues": [],
  "warnings": ["建议添加索引"],
  "review_stage": "development",
  "metadata": {}
}

## 输出强约束

1. **只能输出一个 JSON 对象**，不允许 Markdown、代码块或任何额外文本
2. **必须包含所有字段**，字段缺失视为错误

## 重要约束

1. **客观公正**：基于事实评价，不偏袒
2. **问题具体**：issues 中描述具体问题和位置
3. **passed 规则**：有 issues 时 passed 必须为 false
4. **review_stage**：若存在 SQL 开发结果，则为 development；否则为 design

## 代码评审方法论

### 评审原则
1. **客观公正**：基于事实评价
2. **问题优先**：先找问题，再给建议
3. **具体明确**：问题描述要具体

### 问题分级
- **阻断级(issues)**：必须修复才能通过
- **警告级(warnings)**：建议修复，不强制
"""

    async def run(self, ctx: AgentContext) -> ReviewResult:
        """执行评审"""
        sections: list[str] = []
        analysis = await ctx.get_deliverable("analyst")
        design = await ctx.get_deliverable("architect")
        sql_result = await ctx.get_deliverable("developer")
        review_stage_hint = "development" if sql_result else "design"

        def _append_section(title: str, data: object | None) -> None:
            if not data:
                return
            try:
                payload = json.dumps(data, ensure_ascii=False, indent=2)
            except TypeError:
                payload = str(data)
            sections.append(f"## {title}\n{payload}")

        sections.append(f"## 评审阶段\n{review_stage_hint}")
        _append_section("需求分析结果", analysis)
        _append_section("架构设计结果", design)
        _append_section("SQL 开发结果", sql_result)

        if not sections:
            sections.append(
                f"未获取到结构化交付物，请基于下发任务/用户输入进行评审。\n用户输入: {ctx.query}"
            )

        # 1. 构建消息
        human_message = "\n\n".join(sections)
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # 2. 工具调用循环（评审一般不需要工具）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        return output
