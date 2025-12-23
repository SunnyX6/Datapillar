"""
Developer Agent（数据开发）

职责：为每个 Job 的 Stage 生成 SQL
- 按 Job 处理，每个 Job 包含多个 Stage
- 为每个 Stage 生成 SQL
- 将所有 Stage 的 SQL 组合成完整脚本
- 通过工具获取表结构、列级血缘、历史 SQL（精准匹配）
"""

import json
import logging
import re

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, GlobalKGContext
from src.modules.etl.schemas.plan import Job, Stage, Workflow
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import get_table_columns, get_column_lineage, get_sql_by_lineage

logger = logging.getLogger(__name__)


JOB_SQL_PROMPT = """你是资深数据开发工程师。

## 任务
为以下 Job 生成完整的 SQL 脚本。

## Job 信息
- 名称: {job_name}
- 描述: {job_description}
- 组件类型: {job_type}

## Stage 列表
{stages_info}

## 表结构
{table_schemas}

## 列级血缘（字段映射参考）
{column_lineage}

## 历史 SQL（重要：直接参考 JOIN 条件和写法风格！）
{reference_sql}

## 可用工具
{tools_description}
{test_feedback}
## 生成要求

### 1. 列别名规范（严格执行！）

**所有 SELECT 字段必须使用 AS 别名**：
- 普通字段：`t.order_id AS order_id`
- 计算字段：`t.amount * 2 AS double_amount`
- 聚合函数：`SUM(t.amount) AS total_amount`
- 不允许无别名字段：`t.order_id` ❌ → `t.order_id AS order_id` ✅

**目标表列对齐**：
- SELECT 的别名必须与目标表列名完全一致
- 例：目标表有列 `adjusted_amount` → 写 `t.amount AS adjusted_amount`

**临时表列引用**：
- 临时表的 SELECT 列必须全部有明确别名
- 后续 Stage 通过别名引用临时表列

### 2. 临时表规范
- **创建临时表前必须先删除**：`DROP TABLE IF EXISTS temp.xxx;` 然后再 `CREATE TABLE temp.xxx AS ...`
- **临时表命名规范**：必须放在 temp 库下，格式为 `temp.tmp_<描述性名称>`

### 3. 其他规范
- **必须参考历史 SQL 中的 JOIN 条件**，不要自己猜测关联字段！
- 参考列级血缘中的字段映射关系，确保字段转换正确
- 保持与历史 SQL 相同的写法风格

### 4. 输出格式参考
```sql
-- Stage 1: xxx
DROP TABLE IF EXISTS temp.tmp_step1;
CREATE TABLE temp.tmp_step1 AS
SELECT
    t.order_id AS order_id,
    t.user_id AS user_id,
    t.amount AS amount,
    SUM(t.amount) AS total_amount
FROM source_table t
GROUP BY t.order_id, t.user_id, t.amount;

-- Stage 2: xxx
INSERT OVERWRITE TABLE schema.target_table
SELECT
    tmp.order_id AS order_id,
    tmp.user_id AS user_id,
    tmp.total_amount AS total_amount
FROM temp.tmp_step1 tmp;
```

只输出 SQL，不要解释。
"""


# 绑定的工具
DEVELOPER_TOOLS = [get_table_columns, get_column_lineage, get_sql_by_lineage]


class DeveloperAgent:
    """
    数据开发

    职责：
    1. 读取每个 Job 中的 Stage 信息
    2. 通过工具获取表结构、列级血缘、参考 SQL
    3. 为每个 Stage 生成 SQL
    4. 将所有 Stage SQL 组合为完整脚本
    5. 记录参考的 SQL ID，用于后续打分
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        self.llm_with_tools = self.llm.bind_tools(DEVELOPER_TOOLS)
        self.max_retries = 2
        self.max_tool_calls = 6
        # 记录本次参考的 SQL ID
        self._referenced_sql_ids: list[str] = []

    async def __call__(self, state: AgentState) -> Command:
        """执行 SQL 生成"""
        architecture_plan = state.architecture_plan
        test_result = state.test_result

        # 清空参考 SQL ID 列表（每次执行重新收集）
        self._referenced_sql_ids = []

        if not architecture_plan:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，无法生成 SQL")],
                    "current_agent": "developer_agent",
                    "error": "缺少架构方案",
                }
            )

        # 检查是否是迭代（有测试反馈）
        is_iteration = test_result is not None
        if is_iteration:
            logger.info("💻 DeveloperAgent 根据测试反馈重新生成 SQL")
        else:
            logger.info("💻 DeveloperAgent 开始生成 SQL")

        # 获取上下文
        global_kg_context = state.get_global_kg_context()
        agent_context = state.get_agent_context(AgentType.DEVELOPER)

        if not global_kg_context:
            global_kg_context = GlobalKGContext()

        if not agent_context:
            agent_context = AgentScopedContext.create_for_agent(
                agent_type=AgentType.DEVELOPER,
                tables=[],
                user_query=state.user_input,
            )

        # 转换为 Workflow
        if isinstance(architecture_plan, dict):
            plan = Workflow(**architecture_plan)
        else:
            plan = architecture_plan

        all_errors: list[str] = []
        generated_count = 0

        try:
            # 按拓扑顺序处理 Job
            sorted_jobs = plan.topological_sort()

            for job in sorted_jobs:
                if not job.stages:
                    all_errors.append(f"Job {job.id} 没有 Stage 信息")
                    break  # 有错误立即停止

                # 获取上一轮生成的 SQL（如果有）
                previous_sql = job.config.get("content") if job.config else None

                # 格式化测试反馈（带上错误 SQL）
                job_test_feedback = self._format_test_feedback(test_result, previous_sql)

                # 为整个 Job 生成 SQL 脚本
                sql_script, success, errors = await self._generate_job_sql(
                    job=job,
                    agent_context=agent_context,
                    test_feedback=job_test_feedback,
                )

                if success:
                    # 更新 Job 配置
                    job.config = {"content": sql_script}
                    job.config_generated = True
                    generated_count += 1
                    logger.info(f"✅ Job {job.id} SQL 生成成功 ({len(job.stages)} 个 Stage)")
                else:
                    all_errors.extend(errors)
                    logger.error(f"❌ Job {job.id} SQL 生成失败: {errors}")
                    break  # 有错误立即停止，不继续生成

            # 部分成功 = 整体失败
            if all_errors or generated_count < len(sorted_jobs):
                logger.error(f"❌ DeveloperAgent 失败: {generated_count}/{len(sorted_jobs)} 成功")
                return Command(
                    update={
                        "messages": [AIMessage(content=f"SQL 生成失败: {all_errors[0] if all_errors else '部分 Job 未生成'}")],
                        "architecture_plan": plan.model_dump(),
                        "current_agent": "developer_agent",
                        "test_result": None,
                        "error": "\n".join(all_errors) if all_errors else "部分 Job 生成失败",
                    }
                )

            logger.info(f"✅ DeveloperAgent 完成: 全部 {generated_count} 个 Job 成功")

            # 记录参考的 SQL ID（去重）
            unique_sql_ids = list(set(self._referenced_sql_ids))
            if unique_sql_ids:
                logger.info(f"📝 参考了 {len(unique_sql_ids)} 个历史 SQL: {unique_sql_ids}")

            return Command(
                update={
                    "messages": [AIMessage(content=f"SQL 生成完成: {generated_count} 个 Job")],
                    "architecture_plan": plan.model_dump(),
                    "current_agent": "developer_agent",
                    "test_result": None,
                    "referenced_sql_ids": unique_sql_ids,
                }
            )

        except Exception as e:
            logger.error(f"DeveloperAgent 生成失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"SQL 生成失败: {str(e)}")],
                    "current_agent": "developer_agent",
                    "error": str(e),
                }
            )

    def _format_test_feedback(self, test_result, previous_sql: str | None = None) -> str:
        """格式化测试反馈（包含上一轮错误 SQL）"""
        if not test_result:
            return ""

        # 解析 test_result
        if isinstance(test_result, dict):
            validation_errors = test_result.get("validation_errors", [])
            failed_tests = test_result.get("failed_tests", 0)
            notes = test_result.get("notes", "")
        else:
            validation_errors = getattr(test_result, "validation_errors", [])
            failed_tests = getattr(test_result, "failed_tests", 0)
            notes = getattr(test_result, "notes", "")

        if not validation_errors and failed_tests == 0:
            return ""

        lines = ["\n## ⚠️ 上一轮测试反馈（必须修复）\n"]

        if failed_tests > 0:
            lines.append(f"失败测试数: {failed_tests}\n")

        if validation_errors:
            lines.append("### 错误列表")
            for error in validation_errors:
                lines.append(f"- {error}")

        # 展示上一轮错误的 SQL，让 LLM 知道不要再这样写
        if previous_sql:
            lines.append("\n### 上一轮生成的错误 SQL（不要重复这些错误！）")
            lines.append(f"```sql\n{previous_sql[:1500]}\n```")

        if notes:
            lines.append(f"\n备注: {notes}")

        lines.append("")
        return "\n".join(lines)

    async def _generate_job_sql(
        self,
        job: Job,
        agent_context: AgentScopedContext,
        test_feedback: str = "",
    ) -> tuple[str, bool, list[str]]:
        """为整个 Job 生成 SQL 脚本（通过工具获取知识）"""
        # 收集持久化输入表（跳过临时表，临时表在 temp 库下）
        all_input_tables = set(job.input_tables or [])
        output_table = job.output_table

        # 通过工具获取表结构
        table_schemas = await self._get_table_schemas_via_tool(list(all_input_tables))

        # 通过工具获取列级血缘
        column_lineage = await self._get_column_lineage_via_tool(
            list(all_input_tables), output_table
        )

        # 通过工具精准匹配历史 SQL（根据血缘关系）
        reference_sql = await self._get_reference_sql_via_tool(
            list(all_input_tables), output_table
        )

        # 格式化 Stage 信息
        stages_info = self._format_stages(job.stages)

        for attempt in range(self.max_retries):
            try:
                prompt = JOB_SQL_PROMPT.format(
                    job_name=job.name,
                    job_description=job.description or "",
                    job_type=job.type,
                    stages_info=stages_info,
                    table_schemas=table_schemas,
                    column_lineage=column_lineage,
                    reference_sql=reference_sql,
                    tools_description=agent_context.get_tools_description(),
                    test_feedback=test_feedback,
                )

                # 使用带工具的 LLM 生成 SQL
                sql = await self._generate_sql_with_tools(prompt, agent_context)

                if not sql or len(sql) < 20:
                    continue

                if not any(kw in sql.upper() for kw in ["SELECT", "INSERT", "CREATE"]):
                    continue

                return sql, True, []

            except Exception as e:
                logger.error(f"Job {job.id} SQL 生成失败 (尝试 {attempt + 1}): {e}")

        return "", False, [f"Job {job.id} SQL 生成失败"]

    async def _generate_sql_with_tools(
        self, prompt: str, agent_context: AgentScopedContext
    ) -> str:
        """使用工具生成 SQL"""
        messages = [HumanMessage(content=prompt)]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await self.llm_with_tools.ainvoke(messages)
            messages.append(response)

            # 如果没有工具调用，返回 SQL
            if not response.tool_calls:
                return self._clean_sql(response.content)

            # 执行工具调用
            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 DeveloperAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，获取最终响应
        response = await self.llm.ainvoke(messages)
        return self._clean_sql(response.content)

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用"""
        try:
            if tool_name == "get_table_columns":
                return await get_table_columns.ainvoke(tool_args)
            elif tool_name == "get_column_lineage":
                return await get_column_lineage.ainvoke(tool_args)
            elif tool_name == "get_sql_by_lineage":
                return await get_sql_by_lineage.ainvoke(tool_args)
            else:
                return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"})
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    def _format_stages(self, stages: list[Stage]) -> str:
        """格式化 Stage 列表"""
        lines = []
        for stage in sorted(stages, key=lambda s: s.stage_id):
            lines.append(f"### Stage {stage.stage_id}: {stage.name}")
            lines.append(f"- 描述: {stage.description}")
            lines.append(f"- 输入表: {', '.join(stage.input_tables)}")
            lines.append(f"- 输出表: {stage.output_table}")
            lines.append(f"- 是否临时表: {stage.is_temp_table}")
            lines.append("")
        return "\n".join(lines)

    async def _get_table_schemas_via_tool(self, input_tables: list[str]) -> str:
        """通过工具获取表结构信息"""
        lines = []

        for table_name in input_tables:
            try:
                result = await get_table_columns.ainvoke({"table_name": table_name})
                data = json.loads(result)

                if data.get("status") == "success":
                    columns = data.get("columns", [])
                    col_info = [
                        f"{c.get('name')} ({c.get('data_type', 'string')})"
                        for c in columns[:20]
                    ]
                    lines.append(f"### {table_name}")
                    lines.append(f"字段: {', '.join(col_info)}")
                    if len(columns) > 20:
                        lines.append(f"...共 {len(columns)} 个字段")
                    lines.append("")
            except Exception as e:
                logger.warning(f"获取表 {table_name} 结构失败: {e}")

        return "\n".join(lines) if lines else "（无）"

    async def _get_column_lineage_via_tool(
        self, input_tables: list[str], output_table: str | None
    ) -> str:
        """通过工具获取列级血缘信息"""
        if not output_table or not input_tables:
            return "（无）"

        lines = []
        for source_table in input_tables:
            try:
                result = await get_column_lineage.ainvoke({
                    "source_table": source_table,
                    "target_table": output_table,
                })
                data = json.loads(result)

                if data.get("status") == "success":
                    lineage = data.get("lineage", [])
                    if lineage:
                        lines.append(f"### {source_table} → {output_table}")
                        for item in lineage:
                            mappings = item.get("column_mappings", [])
                            for m in mappings[:20]:
                                src_col = m.get("source_column", "")
                                tgt_col = m.get("target_column", "")
                                transform = m.get("transformation", "direct")
                                if src_col and tgt_col:
                                    lines.append(f"- {src_col} → {tgt_col} ({transform})")
                        lines.append("")
            except Exception as e:
                logger.warning(f"获取列级血缘失败 {source_table} → {output_table}: {e}")

        return "\n".join(lines) if lines else "（无）"

    async def _get_reference_sql_via_tool(
        self, input_tables: list[str], output_table: str | None
    ) -> str:
        """通过工具精准匹配历史 SQL（根据血缘关系）"""
        if not input_tables or not output_table:
            return "（无历史 SQL）"

        try:
            result = await get_sql_by_lineage.ainvoke({
                "source_tables": input_tables,
                "target_table": output_table,
            })
            data = json.loads(result)

            if data.get("status") == "success":
                sql_content = data.get("sql_content")
                sql_id = data.get("sql_id")

                # 记录参考的 SQL ID（用于后续打分）
                if sql_id:
                    self._referenced_sql_ids.append(sql_id)
                    logger.info(f"📌 参考历史 SQL: {sql_id}")

                if sql_content:
                    sql_name = data.get("sql_name", "")
                    engine = data.get("engine", "")
                    return f"""### 历史 SQL（{sql_name}，引擎: {engine}）
直接参考此 SQL 的 JOIN 条件和写法风格！

```sql
{sql_content}
```
"""

        except Exception as e:
            logger.warning(f"获取历史 SQL 失败: {e}")

        return "（无历史 SQL）"

    @staticmethod
    def _clean_sql(content: str) -> str:
        """清理 SQL（去掉 markdown 代码块）"""
        sql_match = re.search(r'```sql\s*([\s\S]*?)\s*```', content)
        if sql_match:
            return sql_match.group(1).strip()

        code_match = re.search(r'```\s*([\s\S]*?)\s*```', content)
        if code_match:
            return code_match.group(1).strip()

        return content.strip()
