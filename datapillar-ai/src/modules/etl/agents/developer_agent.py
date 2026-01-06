"""
Developer Agent（数据开发）

职责：为每个 Job 的 Stage 生成 SQL
- 按 Job 处理，每个 Job 包含多个 Stage
- 为每个 Stage 生成 SQL
- 将所有 Stage 的 SQL 组合成完整脚本
- 通过工具获取表结构、列级血缘、历史 SQL（精准匹配）
"""

import asyncio
import json
import logging
import re

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.infrastructure.resilience import get_resilience_config
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.developer import DeveloperSqlOutput
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import Job, Stage, Workflow
from src.modules.etl.tools.table import get_lineage_sql, get_table_detail, get_table_lineage

logger = logging.getLogger(__name__)


DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 DeveloperAgent（数据开发）。

## 任务
根据"任务参数 JSON"为指定 Job 生成完整 SQL 脚本。

## 任务参数（系统注入）
系统会提供：
- user_query：用户原始需求
- current_job：本次需要生成 SQL 的 Job（含 stages）
- evidence：已获取的证据（表结构/列级血缘/历史 SQL）
- review_feedback：上一轮 review 反馈（如有）

## 可用工具

### get_table_detail
查询表的详细信息（字段、类型等）。

### get_table_lineage
查询表的血缘关系（上下游表）。

### get_lineage_sql
根据源表和目标表精准查找历史 SQL。

## 生成要求（严格）

### 1. 列别名规范
所有 SELECT 字段必须使用 AS 别名：
- 普通字段：`t.order_id AS order_id`
- 计算字段：`t.amount * 2 AS double_amount`
- 聚合函数：`SUM(t.amount) AS total_amount`

目标表列对齐：
- SELECT 的别名必须与目标表列名完全一致

### 2. 临时表规范
- 创建临时表前必须先删除：`DROP TABLE IF EXISTS temp.xxx;`
- 临时表格式：`temp.tmp_<描述性名称>`

### 3. 参考证据
- 必须参考历史 SQL 中的 JOIN 条件
- 参考列级血缘中的字段映射关系

## 输出格式（JSON）
生成完成后，直接输出以下 JSON 格式：
```json
{
  "sql": "-- Stage 1: xxx\\nDROP TABLE IF EXISTS temp.tmp_step1;\\nCREATE TABLE temp.tmp_step1 AS\\nSELECT ...\\n\\n-- Stage 2: xxx\\nINSERT OVERWRITE TABLE ..."
}
```

## 重要约束
1. sql 字段包含所有 Stage 的 SQL，用换行分隔
2. 最后一个 Stage 必须写入最终目标表
3. 生成完成后直接输出 JSON，不要调用任何工具
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
        config = get_resilience_config()
        self.max_retries = config.max_retries
        self.max_iterations = config.max_iterations
        self._referenced_sql_ids: list[str] = []
        self.allowlist = get_agent_tools(AgentType.DEVELOPER)

    async def run(
        self,
        *,
        user_query: str,
        workflow: Workflow,
        review_feedback: ReviewResult | None = None,
        knowledge_agent=None,
    ) -> AgentResult:
        """
        执行 SQL 生成

        参数：
        - user_query: 用户输入
        - workflow: 工作流（包含 Jobs）
        - review_feedback: 上一轮 review 反馈
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）

        返回：
        - AgentResult: 执行结果
        """
        self._referenced_sql_ids = []
        self._knowledge_agent = knowledge_agent

        is_iteration = review_feedback is not None
        if is_iteration:
            logger.info("💻 DeveloperAgent 根据 review 反馈重新生成 SQL")
        else:
            logger.info("💻 DeveloperAgent 开始生成 SQL")

        all_errors: list[str] = []
        generated_count = 0
        total_jobs = len(workflow.jobs)

        try:
            # 按拓扑分层处理，同一层内并行执行
            layers = workflow.topological_layers()

            for layer_idx, layer in enumerate(layers, 1):
                logger.info(f"📦 处理第 {layer_idx}/{len(layers)} 层: {[j.id for j in layer]}")

                # 并行处理当前层的所有 Job
                layer_results = await asyncio.gather(
                    *[
                        self._process_single_job(
                            job=job,
                            user_query=user_query,
                            review_feedback=review_feedback,
                        )
                        for job in layer
                    ]
                )

                # 检查当前层是否全部成功
                layer_has_error = False
                for job, (sql_script, success, errors) in zip(layer, layer_results, strict=True):
                    if success:
                        job.config = {"content": sql_script}
                        job.config_generated = True
                        generated_count += 1
                        logger.info(f"✅ Job {job.id} SQL 生成成功 ({len(job.stages)} 个 Stage)")
                    else:
                        all_errors.extend(errors)
                        logger.error(f"❌ Job {job.id} SQL 生成失败: {errors}")
                        layer_has_error = True

                # 当前层有失败，终止后续层的处理
                if layer_has_error:
                    break

            if all_errors or generated_count < total_jobs:
                logger.error(f"❌ DeveloperAgent 失败: {generated_count}/{total_jobs} 成功")
                return AgentResult.failed(
                    summary=f"SQL 生成失败: {all_errors[0] if all_errors else '部分 Job 未生成'}",
                    error="\n".join(all_errors) if all_errors else "部分 Job 生成失败",
                )

            logger.info(f"✅ DeveloperAgent 完成: 全部 {generated_count} 个 Job 成功")

            unique_sql_ids = list(set(self._referenced_sql_ids))
            if unique_sql_ids:
                logger.info(f"📝 参考了 {len(unique_sql_ids)} 个历史 SQL: {unique_sql_ids}")

            # completed 标准：所有 Job 必须产出非空 SQL
            missing_sql_jobs: list[str] = []
            for job in workflow.jobs:
                content = job.config.get("content") if job.config else None
                if not (job.config_generated and isinstance(content, str) and content.strip()):
                    missing_sql_jobs.append(job.id)
            if missing_sql_jobs:
                return AgentResult.failed(
                    summary=f"SQL 生成不完整，缺少有效 SQL 的 Job: {', '.join(missing_sql_jobs)}",
                    error=f"缺少有效 SQL 的 Job: {', '.join(missing_sql_jobs)}",
                )

            return AgentResult.completed(
                summary=f"SQL 生成完成: {generated_count} 个 Job",
                deliverable=workflow,
                # DeveloperAgent 的产物是对架构师 plan 的“补全”（填充 SQL），必须写回同一份交付物类型
                # 否则后续迭代会读到旧 plan，导致“看似完成但实际没更新”的甩锅式状态漂移。
                deliverable_type="plan",
            )

        except Exception as e:
            logger.error(f"DeveloperAgent 生成失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"SQL 生成失败: {str(e)}",
                error=str(e),
            )

    def _format_review_feedback(
        self, review_result: ReviewResult | None, previous_sql: str | None = None
    ) -> str:
        """格式化 review 反馈（包含上一轮错误 SQL）"""
        if not review_result:
            return ""

        validation_errors = review_result.validation_errors or []
        failed_tests = review_result.failed_tests or 0

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

    async def _process_single_job(
        self,
        *,
        job: Job,
        user_query: str,
        review_feedback: ReviewResult | None,
    ) -> tuple[str, bool, list[str]]:
        """
        处理单个 Job 的 SQL 生成（可并行调用）

        返回：(sql_script, success, errors)
        """
        if not job.stages:
            return "", False, [f"Job {job.id} 没有 Stage 信息"]

        previous_sql = job.config.get("content") if job.config else None
        job_review_feedback = self._format_review_feedback(review_feedback, previous_sql)

        return await self._generate_job_sql(
            user_query=user_query,
            job=job,
            review_feedback=job_review_feedback,
        )

    async def _generate_job_sql(
        self,
        *,
        user_query: str,
        job: Job,
        review_feedback: str = "",
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
                    "review_feedback": review_feedback,
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
        """
        带工具调用的 SQL 生成流程：
        1. 预先调用 KnowledgeAgent 获取候选表/列/值域（带权限过滤）
        2. 第一阶段：LLM 调用工具获取表结构等信息（bind_tools + ToolMessage）
        3. 第二阶段：LLM 输出结构化结果（with_structured_output + parse_structured_output 兜底）
        """
        # 预先检索知识上下文（带权限过滤）
        context_payload = None
        if self._knowledge_agent:
            ctx = await self._knowledge_agent.global_search(user_query, top_k=10, min_score=0.5)
            logger.info(f"📚 知识检索完成: {ctx.summary()}")
            context_payload = ctx.to_llm_context(allowlist=self.allowlist)

        llm_with_tools = self._bind_tools()
        messages = build_llm_messages(
            system_instructions=DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="developer_agent",
            user_query=user_query,
            task_payload=task_payload,
            context_payload=context_payload,
        )

        # 第一阶段：工具调用收集信息
        for _ in range(self.max_iterations):
            response = await llm_with_tools.ainvoke(messages)

            if not response.tool_calls:
                # 没有工具调用，进入第二阶段
                break

            # 执行工具调用，结果放入 ToolMessage
            messages.append(response)
            for tc in response.tool_calls:
                logger.info(f"🔧 DeveloperAgent 调用工具: {tc['name']}({tc['args']})")

            results = await asyncio.gather(
                *[self._execute_tool(tc["name"], tc["args"]) for tc in response.tool_calls]
            )

            for tc, result in zip(response.tool_calls, results, strict=True):
                messages.append(ToolMessage(content=result, tool_call_id=tc["id"]))

        # 第二阶段：结构化输出（with_structured_output 让 LLM 知道 schema）
        output = await self._get_structured_output(messages, DeveloperSqlOutput)
        return self._clean_sql(output.sql)

    async def _get_structured_output(
        self,
        messages: list,
        schema: type[DeveloperSqlOutput],
    ) -> DeveloperSqlOutput:
        """
        获取结构化输出：with_structured_output(json_mode) + parse_structured_output 兜底
        """
        from src.infrastructure.llm.structured_output import parse_structured_output

        # 使用 json_mode（不是 function_calling，避免和工具调用混淆）
        llm_structured = self.llm.with_structured_output(
            schema,
            method="json_mode",
            include_raw=True,
        )
        result = await llm_structured.ainvoke(messages)

        # 情况 1：直接解析成功
        if isinstance(result, schema):
            return result

        # 情况 2：dict 格式（include_raw=True 的返回）
        if isinstance(result, dict):
            parsed = result.get("parsed")
            if isinstance(parsed, schema):
                return parsed

            # 解析失败，尝试从 raw 中恢复
            parsing_error = result.get("parsing_error")
            raw = result.get("raw")

            if raw:
                raw_text = getattr(raw, "content", None)
                if raw_text:
                    logger.warning(
                        "with_structured_output 解析失败，尝试 parse_structured_output 兜底"
                    )
                    try:
                        return parse_structured_output(raw_text, schema)
                    except ValueError as e:
                        logger.error(f"parse_structured_output 兜底也失败: {e}")
                        raise

            if parsing_error:
                raise parsing_error

        raise ValueError(f"无法获取结构化输出: {type(result)}")

    def _bind_tools(self):
        """绑定工具到 LLM"""
        tool_registry = {
            "get_table_detail": get_table_detail,
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

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用（支持精确参数和模糊参数）"""
        try:
            if tool_name not in self.allowlist:
                return self._tool_error(f"工具不在 allowlist 中: {tool_name}")

            tool_registry = {
                "get_table_detail": get_table_detail,
                "get_table_lineage": get_table_lineage,
                "get_lineage_sql": get_lineage_sql,
            }

            tool_func = tool_registry.get(tool_name)
            if not tool_func:
                return self._tool_error(f"未知工具: {tool_name}")

            # 处理表相关工具的参数
            if tool_name in ["get_table_detail", "get_table_lineage"]:
                catalog = tool_args.get("catalog")
                schema = tool_args.get("schema")
                table = tool_args.get("table")

                # 如果只提供了 table_name，尝试解析路径
                if not (catalog and schema and table):
                    table_name = tool_args.get("table_name") or tool_args.get("table") or ""
                    if not table_name:
                        return self._tool_error("缺少 table 参数")

                    # 尝试解析 schema.table 或 catalog.schema.table 格式
                    parts = table_name.split(".")
                    if len(parts) >= 3:
                        catalog, schema, table = parts[0], parts[1], parts[2]
                    elif len(parts) == 2:
                        schema, table = parts[0], parts[1]
                        catalog = ""
                    else:
                        # 无法解析，尝试通过 knowledge_agent 查找
                        if self._knowledge_agent:
                            ctx = await self._knowledge_agent.global_search(
                                table_name, top_k=1, min_score=0.6
                            )
                            if ctx.tables:
                                pointer = ctx.tables[0]
                                catalog = pointer.catalog
                                schema = pointer.schema
                                table = pointer.table
                            else:
                                return self._tool_error(f"未找到表: {table_name}")
                        else:
                            return self._tool_error(f"无法解析表名: {table_name}")

                # 构造精确参数
                precise_args = {
                    "catalog": catalog,
                    "schema": schema,
                    "table": table,
                }
                if tool_name == "get_table_lineage":
                    precise_args["direction"] = tool_args.get("direction", "both")

                logger.info(f"🔧 调用工具: {tool_name}({precise_args})")
                return await tool_func.ainvoke(precise_args)

            # get_lineage_sql 直接使用参数
            logger.info(f"🔧 调用工具: {tool_name}({tool_args})")
            return await tool_func.ainvoke(tool_args or {})

        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return self._tool_error(str(e))

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
                    "get_table_detail",
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
