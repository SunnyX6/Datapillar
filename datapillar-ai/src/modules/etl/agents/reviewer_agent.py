"""
ReviewerAgent - 代码评审员

职责：
- 评审架构设计和 SQL 代码
- 给出通过/不通过的判断
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from src.modules.etl.schemas.review import ReviewResult
from src.modules.oneagentic import Clarification, agent
from src.modules.oneagentic.knowledge import KnowledgeDomain, KnowledgeLevel, KnowledgeStore

if TYPE_CHECKING:
    from src.modules.oneagentic import AgentContext


# ==================== Agent 定义 ====================


@agent(
    id="reviewer",
    name="代码评审员",
    description="评审架构设计和 SQL 代码，给出通过/不通过的判断",
    tools=[],  # 评审不需要工具，纯 LLM 判断
    deliverable_schema=ReviewResult,
    deliverable_key="review",
    knowledge_domains=["reviewer_methodology"],
    temperature=0.0,
    max_iterations=3,
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
"""

    async def run(self, ctx: AgentContext) -> ReviewResult | Clarification:
        """执行评审"""
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（评审一般不需要工具）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_output(messages)

        return output


# ==================== 知识领域 ====================

REVIEWER_METHODOLOGY = KnowledgeDomain(
    domain_id="reviewer_methodology",
    name="代码评审方法论",
    level=KnowledgeLevel.DOMAIN,
    content="""## 代码评审方法论

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
""",
    tags=["代码评审", "方法论", "质量"],
)


def register_reviewer_knowledge() -> None:
    """注册 ReviewerAgent 相关的知识领域"""
    KnowledgeStore.register_domain(REVIEWER_METHODOLOGY)
