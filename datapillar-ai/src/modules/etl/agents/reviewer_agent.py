"""
ReviewerAgent - 代码评审员

职责：
- 评审架构设计和 SQL 代码
- 给出通过/不通过的判断
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent
from src.modules.etl.schemas.review import ReviewResult

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent 定义 ====================


@agent(
    id="reviewer",
    name="代码评审员",
    description="评审架构设计和 SQL 代码，给出通过/不通过的判断",
    tools=[],  # 评审不需要工具，纯 LLM 判断
    deliverable_schema=ReviewResult,
    temperature=0.0,
    max_steps=3,
)
class ReviewerAgent:
    """代码评审员"""

    SYSTEM_PROMPT = """你是资深代码评审员（ReviewerAgent）。

## 你的任务

评审架构设计和 SQL 代码，给出客观的评价：
1. 从上下文获取需求分析、架构设计、SQL 代码
2. 评审是否符合需求和规范
3. 给出 passed/failed 判断

## 评审维度

### 架构设计（design 阶段）
- 需求覆盖：是否覆盖所有业务步骤
- 数据流向：读写表和依赖关系是否合理
- Job 划分：是否合理

### SQL 代码（development 阶段）
- 业务逻辑：SQL 的聚合/过滤/JOIN 是否正确
- 字段映射：输入输出字段是否匹配
- 性能风险：全表扫描、笛卡尔积等

## 评分标准

- 90+：优秀，无阻断问题
- 70-89：良好，有小问题
- 60-69：及格，需要修改
- <60：不及格，必须重做

## 输出格式

```json
{
  "passed": true,
  "score": 85,
  "summary": "架构设计合理，SQL 逻辑正确",
  "issues": [],
  "warnings": ["建议添加索引"],
  "review_stage": "development",
  "metadata": {}
}
```

## 重要约束

1. **客观公正**：基于事实评价，不偏袒
2. **问题具体**：issues 中描述具体问题和位置
3. **passed 规则**：有 issues 时 passed 必须为 false

## 代码评审方法论

### 评审原则
1. **客观公正**：基于事实评价
2. **问题优先**：先找问题，再给建议
3. **具体明确**：问题描述要具体

### 评审清单

#### 架构设计
- [ ] 是否覆盖所有业务步骤
- [ ] Job 划分是否合理
- [ ] 依赖关系是否正确
- [ ] 临时表作用域是否正确

#### SQL 代码
- [ ] 字段映射是否正确
- [ ] JOIN 条件是否完整
- [ ] WHERE 条件是否合理
- [ ] 聚合逻辑是否正确
- [ ] NULL 值处理是否到位

### 问题分级
- **阻断级(issues)**：必须修复才能通过
- **警告级(warnings)**：建议修复，不强制
"""

    async def run(self, ctx: AgentContext) -> ReviewResult:
        """执行评审"""
        sections: list[str] = []
        analysis = await ctx.get_deliverable("analyst")
        workflow = await ctx.get_deliverable("architect")
        sql_result = await ctx.get_deliverable("developer")

        def _append_section(title: str, data: object | None) -> None:
            if not data:
                return
            try:
                payload = json.dumps(data, ensure_ascii=False, indent=2)
            except TypeError:
                payload = str(data)
            sections.append(f"## {title}\n{payload}")

        _append_section("需求分析结果", analysis)
        _append_section("架构设计结果", workflow)
        _append_section("SQL 开发结果", sql_result)

        if not sections:
            sections.append(
                f"未获取到结构化交付物，请基于下发任务/用户输入进行评审。\n用户输入: {ctx.query}"
            )

        # 1. 构建消息
        prompt = f"{self.SYSTEM_PROMPT}\n\n" + "\n\n".join(sections)
        messages = ctx.build_messages(prompt)

        # 2. 工具调用循环（评审一般不需要工具）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        return output
