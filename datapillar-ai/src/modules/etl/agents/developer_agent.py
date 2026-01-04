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

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.plan import Job, Stage, TestResult, Workflow
from src.modules.etl.tools.agent_tools import (
    get_column_valuedomain,
    get_lineage_sql,
    get_table_columns,
    get_table_lineage,
)

logger = logging.getLogger(__name__)


DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 DeveloperAgent（数据开发）。

## 任务
根据"任务参数 JSON"和"知识上下文 JSON"，为指定 Job 生成完整 SQL 脚本。

## 任务参数（系统注入，不是用户输入）
系统会提供一段"任务参数 JSON"（SystemMessage），其中包含：
- user_query：用户原始需求（仅用于理解业务）
- current_job：本次需要生成 SQL 的 Job（含 stages）
- evidence：已通过工具获取的证据（表结构/列级血缘/历史 SQL）
- test_feedback：上一轮测试反馈（如有）

## 知识上下文（系统注入，不是用户输入）
系统会提供一段"知识上下文 JSON"（SystemMessage），其中包含：
- tables：可用的 schema.table 列表（导航指针）
- etl_pointers：可验证的 ETL 指针（含 qualified_name/element_id/tools）
- allowlist_tools：你允许调用的工具名列表

你必须把该 JSON 视为唯一可信知识入口：
- 禁止臆造任何 schema.table
- 工具调用只能使用该 JSON 中出现的表指针（按 qualified_name 精确匹配）
- 仅当 ETLPointer.tools 包含工具名时，才允许对该表调用该工具

## 生成要求（严格）

### 1. 列别名规范（严格执行）
所有 SELECT 字段必须使用 AS 别名：
- 普通字段：`t.order_id AS order_id`
- 计算字段：`t.amount * 2 AS double_amount`
- 聚合函数：`SUM(t.amount) AS total_amount`
- 不允许无别名字段：`t.order_id` ❌ → `t.order_id AS order_id` ✅

目标表列对齐：
- SELECT 的别名必须与目标表列名完全一致
- 例：目标表有列 `adjusted_amount` → 写 `t.amount AS adjusted_amount`

临时表列引用：
- 临时表的 SELECT 列必须全部有明确别名
- 后续 Stage 通过别名引用临时表列

### 2. 临时表规范
- 创建临时表前必须先删除：`DROP TABLE IF EXISTS temp.xxx;` 然后再 `CREATE TABLE temp.xxx AS ...`
- 临时表必须放在 temp 库下，格式为 `temp.tmp_<描述性名称>`

### 3. 参考证据（严格）
- 必须参考历史 SQL 中的 JOIN 条件，不要自己猜测关联字段
- 参考列级血缘中的字段映射关系，确保字段转换正确
- 保持与历史 SQL 相同的写法风格

### 4. 输出格式参考（仅示例）
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
        self.max_retries = 2
        self.max_tool_calls = 6
        self._referenced_sql_ids: list[str] = []
        self.allowlist = get_agent_tools(AgentType.DEVELOPER)

    async def run(
        self,
        *,
        user_query: str,
        workflow: Workflow,
        test_feedback: TestResult | None = None,
        knowledge_agent=None,
    ) -> AgentResult:
        """
        执行 SQL 生成

        参数：
        - user_query: 用户输入
        - workflow: 工作流（包含 Jobs）
        - test_feedback: 上一轮测试反馈
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）

        返回：
        - AgentResult: 执行结果
        """
        self._referenced_sql_ids = []
        self._knowledge_agent = knowledge_agent

        is_iteration = test_feedback is not None
        if is_iteration:
            logger.info("💻 DeveloperAgent 根据测试反馈重新生成 SQL")
        else:
            logger.info("💻 DeveloperAgent 开始生成 SQL")

        all_errors: list[str] = []
        generated_count = 0

        try:
            sorted_jobs = workflow.topological_sort()

            for job in sorted_jobs:
                if not job.stages:
                    all_errors.append(f"Job {job.id} 没有 Stage 信息")
                    break

                previous_sql = job.config.get("content") if job.config else None
                job_test_feedback = self._format_test_feedback(test_feedback, previous_sql)

                sql_script, success, errors = await self._generate_job_sql(
                    user_query=user_query,
                    job=job,
                    test_feedback=job_test_feedback,
                )

                if success:
                    job.config = {"content": sql_script}
                    job.config_generated = True
                    generated_count += 1
                    logger.info(f"✅ Job {job.id} SQL 生成成功 ({len(job.stages)} 个 Stage)")
                else:
                    all_errors.extend(errors)
                    logger.error(f"❌ Job {job.id} SQL 生成失败: {errors}")
                    break

            if all_errors or generated_count < len(sorted_jobs):
                logger.error(f"❌ DeveloperAgent 失败: {generated_count}/{len(sorted_jobs)} 成功")
                return AgentResult.failed(
                    summary=f"SQL 生成失败: {all_errors[0] if all_errors else '部分 Job 未生成'}",
                    error="\n".join(all_errors) if all_errors else "部分 Job 生成失败",
                )

            logger.info(f"✅ DeveloperAgent 完成: 全部 {generated_count} 个 Job 成功")

            unique_sql_ids = list(set(self._referenced_sql_ids))
            if unique_sql_ids:
                logger.info(f"📝 参考了 {len(unique_sql_ids)} 个历史 SQL: {unique_sql_ids}")

            return AgentResult.completed(
                summary=f"SQL 生成完成: {generated_count} 个 Job",
                deliverable=workflow,
                deliverable_type="workflow",
            )

        except Exception as e:
            logger.error(f"DeveloperAgent 生成失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"SQL 生成失败: {str(e)}",
                error=str(e),
            )

    def _format_test_feedback(
        self, test_result: TestResult | None, previous_sql: str | None = None
    ) -> str:
        """格式化测试反馈（包含上一轮错误 SQL）"""
        if not test_result:
            return ""

        validation_errors = test_result.validation_errors or []
        failed_tests = test_result.failed_tests or 0

        if not validation_errors and failed_tests == 0:
            return ""

        lines = ["\n## ⚠️ 上一轮测试反馈（必须修复）\n"]

        if failed_tests > 0:
            lines.append(f"失败测试数: {failed_tests}\n")

        if validation_errors:
            lines.append("### 错误列表")
            for error in validation_errors:
                lines.append(f"- {error}")

        if previous_sql:
            lines.append("\n### 上一轮生成的错误 SQL（不要重复这些错误！）")
            lines.append(f"```sql\n{previous_sql[:1500]}\n```")

        lines.append("")
        return "\n".join(lines)

    async def _generate_job_sql(
        self,
        *,
        user_query: str,
        job: Job,
        test_feedback: str = "",
    ) -> tuple[str, bool, list[str]]:
        """为整个 Job 生成 SQL 脚本（通过工具获取知识）"""
        all_input_tables = set(job.input_tables or [])
        output_table = job.output_table

        table_schemas = await self._fetch_table_schemas(list(all_input_tables))
        column_lineage = await self._fetch_column_lineage(list(all_input_tables), output_table)
        reference_sql = await self._fetch_reference_sql(list(all_input_tables), output_table)

        stages_info = self._format_stages(job.stages)

        for attempt in range(self.max_retries):
            try:
                task_payload = {
                    "user_query": user_query,
                    "current_job": {
                        "id": job.id,
                        "name": job.name,
                        "description": job.description,
                        "type": job.type,
                        "input_tables": job.input_tables,
                        "output_table": job.output_table,
                        "stages_info": stages_info,
                        "stages": [
                            {
                                "stage_id": st.stage_id,
                                "name": st.name,
                                "description": st.description,
                                "input_tables": st.input_tables,
                                "output_table": st.output_table,
                                "is_temp_table": st.is_temp_table,
                            }
                            for st in sorted((job.stages or []), key=lambda s: s.stage_id)
                        ],
                    },
                    "evidence": {
                        "table_schemas": table_schemas,
                        "column_lineage": column_lineage,
                        "reference_sql": reference_sql,
                    },
                    "test_feedback": test_feedback,
                }

                sql = await self._generate_sql(
                    user_query=user_query,
                    task_payload=task_payload,
                )

                if not sql or len(sql) < 20:
                    continue

                if not any(kw in sql.upper() for kw in ["SELECT", "INSERT", "CREATE"]):
                    continue

                return sql, True, []

            except Exception as e:
                logger.error(f"Job {job.id} SQL 生成失败 (尝试 {attempt + 1}): {e}")

        return "", False, [f"Job {job.id} SQL 生成失败"]

    async def _generate_sql(
        self,
        *,
        user_query: str,
        task_payload: dict,
    ) -> str:
        """使用工具生成 SQL"""
        llm_with_tools = self._bind_tools()
        messages = build_llm_messages(
            system_instructions=DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="developer_agent",
            user_query=user_query,
            task_payload=task_payload,
        )
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
            messages.append(response)

            if not response.tool_calls:
                return self._clean_sql(response.content)

            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 DeveloperAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(ToolMessage(content=tool_result, tool_call_id=tool_id))

                if tool_call_count >= self.max_tool_calls:
                    break

        response = await self.llm.ainvoke(messages)
        return self._clean_sql(response.content)

    def _bind_tools(self):
        """绑定工具到 LLM"""
        tool_registry = {
            "get_table_columns": get_table_columns,
            "get_column_valuedomain": get_column_valuedomain,
            "get_table_lineage": get_table_lineage,
            "get_lineage_sql": get_lineage_sql,
        }
        tools = [tool_registry[name] for name in self.allowlist if name in tool_registry]
        return self.llm.bind_tools(tools)

    @staticmethod
    def _tool_error(message: str, **extra: object) -> str:
        payload: dict[str, object] = {"status": "error", "message": message}
        payload.update(extra)
        return json.dumps(payload, ensure_ascii=False)

    # 工具处理器映射
    _TOOL_HANDLERS: dict[str, str] = {
        "get_table_columns": "_exec_columns",
        "get_column_valuedomain": "_exec_valuedomain",
        "get_table_lineage": "_exec_lineage",
        "get_lineage_sql": "_exec_lineagesql",
    }

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """
        执行工具调用（按需获取指针 + 权限校验）

        流程：
        1. 调用 query_pointers 获取对应类型的指针
        2. 检查指针上的 tools 是否包含要调用的工具
        3. 用指针的信息调用工具
        """
        try:
            if tool_name not in self.allowlist:
                return self._tool_error(f"工具不在 allowlist 中: {tool_name}")

            if not self._knowledge_agent:
                return self._tool_error("无法查询指针：knowledge_agent 未注入")

            handler_name = self._TOOL_HANDLERS.get(tool_name)
            if not handler_name:
                return self._tool_error(f"未知工具: {tool_name}")

            handler = getattr(self, handler_name)
            return await handler(tool_args or {})
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return self._tool_error(str(e))

    async def _exec_columns(self, args: dict) -> str:
        """执行 get_table_columns 工具"""
        table_name = args.get("table_name") or ""
        if not table_name:
            return self._tool_error("缺少 table_name 参数")

        pointers = await self._knowledge_agent.query_pointers(
            table_name, node_types=["Table"], top_k=5
        )
        pointer = self._find_matching_pointer(pointers, table_name)
        if not pointer:
            return self._tool_error("未找到指针", table_name=table_name)
        if "get_table_columns" not in (pointer.tools or []):
            return self._tool_error("指针未授权此工具", table_name=table_name)

        logger.info(f"📊 调用 get_table_columns: {pointer.qualified_name}")
        return await get_table_columns.ainvoke({"table_name": pointer.qualified_name})

    async def _exec_valuedomain(self, args: dict) -> str:
        """执行 get_column_valuedomain 工具"""
        column_name = args.get("column_name") or ""
        if not column_name:
            return self._tool_error("缺少 column_name 参数")

        pointers = await self._knowledge_agent.query_pointers(
            column_name, node_types=["Column"], top_k=5
        )
        pointer = self._find_matching_pointer(pointers, column_name)
        if not pointer:
            return self._tool_error("未找到指针", column_name=column_name)
        if "get_column_valuedomain" not in (pointer.tools or []):
            return self._tool_error("指针未授权此工具", column_name=column_name)

        logger.info(f"📊 调用 get_column_valuedomain: {pointer.qualified_name}")
        return await get_column_valuedomain.ainvoke({"column_name": pointer.qualified_name})

    async def _exec_lineage(self, args: dict) -> str:
        """执行 get_table_lineage 工具"""
        table_name = args.get("table_name") or ""
        direction = args.get("direction") or "both"
        if not table_name:
            return self._tool_error("缺少 table_name 参数")

        pointers = await self._knowledge_agent.query_pointers(
            table_name, node_types=["Table"], top_k=5
        )
        pointer = self._find_matching_pointer(pointers, table_name)
        if not pointer:
            return self._tool_error("未找到指针", table_name=table_name)
        if "get_table_lineage" not in (pointer.tools or []):
            return self._tool_error("指针未授权此工具", table_name=table_name)

        logger.info(f"📊 调用 get_table_lineage: {pointer.qualified_name}")
        return await get_table_lineage.ainvoke(
            {"table_name": pointer.qualified_name, "direction": direction}
        )

    async def _exec_lineagesql(self, args: dict) -> str:
        """执行 get_lineage_sql 工具"""
        source_tables = args.get("source_tables") or []
        target_table = args.get("target_table") or ""
        if not source_tables or not target_table:
            return self._tool_error("缺少 source_tables 或 target_table 参数")

        target_pointers = await self._knowledge_agent.query_pointers(
            target_table, node_types=["Table"], top_k=3
        )
        target_ptr = self._find_matching_pointer(target_pointers, target_table)
        if not target_ptr:
            return self._tool_error("未找到指针", target_table=target_table)
        if "get_lineage_sql" not in (target_ptr.tools or []):
            return self._tool_error("指针未授权此工具", target_table=target_table)

        validated_sources = []
        for src in source_tables:
            src_pointers = await self._knowledge_agent.query_pointers(
                src, node_types=["Table"], top_k=3
            )
            src_ptr = self._find_matching_pointer(src_pointers, src)
            if src_ptr:
                validated_sources.append(src_ptr.qualified_name)

        logger.info(f"📊 调用 get_lineage_sql: {validated_sources} -> {target_ptr.qualified_name}")
        return await get_lineage_sql.ainvoke(
            {"source_tables": validated_sources, "target_table": target_ptr.qualified_name}
        )

    def _find_matching_pointer(self, pointers: list, name: str):
        """从指针列表中找到匹配的指针"""
        if not pointers:
            return None
        # 精确匹配
        for p in pointers:
            if p.qualified_name == name:
                return p
        # 部分匹配
        for p in pointers:
            if name in (p.qualified_name or ""):
                return p
        # 返回第一个
        return pointers[0] if pointers else None

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

    async def _fetch_table_schemas(self, input_tables: list[str]) -> str:
        """通过工具获取表结构信息"""
        lines = []
        for table_name in input_tables:
            try:
                result = await self._execute_tool(
                    "get_table_columns",
                    {"table_name": table_name},
                )
                data = json.loads(result)
                if data.get("status") == "success":
                    columns = data.get("columns", [])
                    col_info = [
                        f"{c.get('name')} ({c.get('data_type', 'string')})" for c in columns[:20]
                    ]
                    lines.append(f"### {table_name}")
                    lines.append(f"字段: {', '.join(col_info)}")
                    if len(columns) > 20:
                        lines.append(f"...共 {len(columns)} 个字段")
                    lines.append("")
            except Exception as e:
                logger.warning(f"获取表 {table_name} 结构失败: {e}")
        return "\n".join(lines) if lines else "（无）"

    async def _fetch_column_lineage(
        self,
        input_tables: list[str],
        output_table: str | None,
    ) -> str:
        """通过 get_table_lineage 获取列级血缘信息"""
        if not output_table or not input_tables:
            return "（无）"
        lines = []
        try:
            # 调用 get_table_lineage 获取目标表的上游血缘（含列映射）
            result = await self._execute_tool(
                "get_table_lineage",
                {"table_name": output_table, "direction": "upstream"},
            )
            data = json.loads(result)
            if data.get("status") == "success":
                lineage_edges = data.get("lineage_edges", [])
                input_set = set(input_tables)
                for edge in lineage_edges:
                    source = edge.get("source_table", "")
                    # 匹配输入表
                    if source in input_set or any(source.endswith(f".{t}") for t in input_set):
                        mappings = edge.get("column_mappings", [])
                        if mappings:
                            lines.append(f"### {source} → {output_table}")
                            for m in mappings[:20]:
                                src_col = m.get("source_column", "")
                                tgt_col = m.get("target_column", "")
                                transform = m.get("transformation", "direct")
                                if src_col and tgt_col:
                                    lines.append(f"- {src_col} → {tgt_col} ({transform})")
                            lines.append("")
        except Exception as e:
            logger.warning(f"获取列级血缘失败: {e}")
        return "\n".join(lines) if lines else "（无）"

    async def _fetch_reference_sql(
        self,
        input_tables: list[str],
        output_table: str | None,
    ) -> str:
        """通过工具精准匹配历史 SQL（根据血缘关系）"""
        if not input_tables or not output_table:
            return "（无历史 SQL）"
        try:
            result = await self._execute_tool(
                "get_lineage_sql",
                {"source_tables": input_tables, "target_table": output_table},
            )
            data = json.loads(result)
            if data.get("status") == "success":
                sql_content = data.get("sql_content")
                sql_id = data.get("sql_id")
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
        sql_match = re.search(r"```sql\s*([\s\S]*?)\s*```", content)
        if sql_match:
            return sql_match.group(1).strip()
        code_match = re.search(r"```\s*([\s\S]*?)\s*```", content)
        if code_match:
            return code_match.group(1).strip()
        return content.strip()
