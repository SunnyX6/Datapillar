"""
DeveloperAgent - 数据开发工程师

职责：
- 为每个 Job 的 Stage 生成 SQL 脚本
- 完成后交给代码评审员
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from src.modules.etl.schemas.developer import DeveloperSqlOutput
from src.modules.oneagentic import Clarification, agent
from src.modules.oneagentic.knowledge import KnowledgeDomain, KnowledgeLevel, KnowledgeStore

if TYPE_CHECKING:
    from src.modules.oneagentic import AgentContext


# ==================== Agent 定义 ====================


@agent(
    id="developer",
    name="数据开发工程师",
    description="为每个 Job 的 Stage 生成 SQL 脚本",
    tools=["get_table_detail", "get_table_lineage", "get_lineage_sql", "search_tables"],
    deliverable_schema=DeveloperSqlOutput,
    deliverable_key="sql",
    knowledge_domains=["developer_methodology"],
    temperature=0.0,
    max_iterations=5,
)
class DeveloperAgent:
    """数据开发工程师"""

    SYSTEM_PROMPT = """你是资深数据开发工程师（DeveloperAgent）。

## 你的任务

为架构师设计的每个 Job/Stage 生成 SQL 脚本：
1. 从上下文获取 Workflow（架构设计结果）
2. 调用工具获取表结构、血缘信息
3. 为每个 Stage 生成 SQL
4. 完成后委派给代码评审员

## 工作流程

1. 获取 Workflow（从上下文）
2. 遍历每个 Job 的每个 Stage
3. 调用 get_table_detail 获取输入/输出表结构
4. 调用 get_lineage_sql 参考历史 SQL
5. 生成 SQL 脚本
6. 完成后调用 delegate_to_reviewer

## SQL 编写规范

1. **字段别名**：所有字段必须用 AS 显式指定别名
2. **临时表**：`DROP TABLE IF EXISTS temp.xxx; CREATE TABLE temp.xxx AS ...`
3. **最终表**：最后一个 Stage 必须写入最终目标表
4. **注释**：关键逻辑需要添加注释

## 输出格式

```json
{
  "sql": "-- Stage 1: xxx\nDROP TABLE IF EXISTS temp.xxx;\nCREATE TABLE temp.xxx AS\nSELECT ...\n\n-- Stage 2: xxx\n...",
  "confidence": 0.8,
  "issues": ["字段映射不明确", "JOIN 条件需确认"]
}
```

## 重要约束

1. 不允许臆造字段名，必须通过工具验证
2. 所有字段必须有明确的来源
3. confidence < 0.8 且有 issues 时需要澄清
4. 完成后调用 delegate_to_reviewer
"""

    async def run(self, ctx: AgentContext) -> DeveloperSqlOutput | Clarification:
        """执行开发"""
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_output(messages)

        # 4. 业务判断：需要澄清？
        if isinstance(output, DeveloperSqlOutput) and output.confidence < 0.8 and output.issues:
            return ctx.clarify(
                message="SQL 生成存在不确定性，请确认以下问题",
                questions=output.issues,
            )

        return output


# ==================== 知识领域 ====================

DEVELOPER_METHODOLOGY = KnowledgeDomain(
    domain_id="developer_methodology",
    name="数据开发方法论",
    level=KnowledgeLevel.DOMAIN,
    content="""## 数据开发方法论

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
""",
    tags=["数据开发", "SQL", "方法论"],
)


def register_developer_knowledge() -> None:
    """注册 DeveloperAgent 相关的知识领域"""
    KnowledgeStore.register_domain(DEVELOPER_METHODOLOGY)
