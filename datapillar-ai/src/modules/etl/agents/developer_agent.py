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
import uuid

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType
from src.modules.etl.schemas.plan import Job, Stage, Workflow
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import (
    get_table_columns,
    get_column_lineage,
    get_sql_by_lineage,
    get_column_value_domain,
    recommend_guidance,
)

logger = logging.getLogger(__name__)


DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 DeveloperAgent（数据开发）。

## 任务
根据“任务参数 JSON”和“知识上下文 JSON”，为指定 Job 生成完整 SQL 脚本。

## 任务参数（系统注入，不是用户输入）
系统会提供一段“任务参数 JSON”（SystemMessage），其中包含：
- user_query：用户原始需求（仅用于理解业务）
- current_job：本次需要生成 SQL 的 Job（含 stages）
- evidence：已通过工具获取的证据（表结构/列级血缘/历史 SQL）
- tools_description：可用工具说明
- test_feedback：上一轮测试反馈（如有）

## 知识上下文（系统注入，不是用户输入）
系统会提供一段“知识上下文 JSON”（SystemMessage），其中包含：
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
        # 记录本次参考的 SQL ID
        self._referenced_sql_ids: list[str] = []

    async def __call__(self, state: AgentState) -> Command:
        """执行 SQL 生成"""
        architecture_plan = state.architecture_plan
        test_result = state.test_result

        # 清空参考 SQL ID 列表（每次执行重新收集）
        self._referenced_sql_ids = []

        if not architecture_plan:
            req = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="delegate",
                created_by="developer_agent",
                target_agent="architect_agent",
                resume_to="developer_agent",
                payload={
                    "type": "need_architecture_plan",
                    "message": "SQL 生成需要架构方案，已委派数据架构师先完成工作流设计。",
                },
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，已委派数据架构师")],
                    "current_agent": "developer_agent",
                    "pending_requests": [r.model_dump() for r in pending],
                }
            )

        # 检查是否是迭代（有测试反馈）
        is_iteration = test_result is not None
        if is_iteration:
            logger.info("💻 DeveloperAgent 根据测试反馈重新生成 SQL")
        else:
            logger.info("💻 DeveloperAgent 开始生成 SQL")

        # 获取上下文
        agent_context = state.get_agent_context(AgentType.DEVELOPER)

        if not agent_context:
            agent_context = AgentScopedContext.create_for_agent(
                agent_type=AgentType.DEVELOPER,
                tables=[],
            )
        context_payload = self._build_context_payload(agent_context=agent_context)

        # 转换为 Workflow
        if isinstance(architecture_plan, dict):
            plan = Workflow(**architecture_plan)
        else:
            plan = architecture_plan

        allowed_tables = self._build_allowed_tables(agent_context.etl_pointers)
        unknown_tables = self._find_unknown_tables(plan, allowed_tables=allowed_tables)
        if unknown_tables:
            counters = dict(state.delegation_counters or {})
            counter_key = "developer_agent:delegate:knowledge_agent:unknown_tables"
            delegated = int(counters.get(counter_key) or 0)
            if delegated < 1:
                counters[counter_key] = delegated + 1
                req = BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="delegate",
                    created_by="developer_agent",
                    target_agent="knowledge_agent",
                    resume_to="developer_agent",
                    payload={
                        "type": "refresh_knowledge",
                        "reason": "unknown_tables",
                        "unknown_tables": unknown_tables,
                        "message": "SQL 生成阶段发现未知表，已委派知识检索刷新上下文后再继续。",
                    },
                )
                pending = list(state.pending_requests or [])
                pending.append(req)
                return Command(
                    update={
                        "messages": [AIMessage(content="检测到未知表，已委派知识检索刷新上下文")],
                        "current_agent": "developer_agent",
                        "pending_requests": [r.model_dump() for r in pending],
                        "delegation_counters": counters,
                    }
                )
            request_id = f"req_{uuid.uuid4().hex}"
            guidance = await self._try_recommend_guidance(state.user_input)
            payload = {
                "type": "clarification",
                "message": "SQL 生成无法继续：知识库无法定位架构方案中引用的表，请补充可验证线索。",
                "questions": [
                    f"请确认这些表的准确名称（推荐 schema.table）：{', '.join(unknown_tables[:12])}",
                    "如果你不确定表名：请粘贴现有 SQL/DDL/字段清单，或说明上游来源系统与目标表。",
                ],
            }
            if guidance:
                payload["guidance"] = guidance
            req = BlackboardRequest(
                request_id=request_id,
                kind="human",
                created_by="developer_agent",
                resume_to="blackboard_router",
                payload=payload,
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="无法定位表指针：需要你补充上下文信息后才能继续")],
                    "current_agent": "developer_agent",
                    "pending_requests": [r.model_dump() for r in pending],
                    "delegation_counters": counters,
                }
            )

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
                    user_query=state.user_input,
                    job=job,
                    agent_context=agent_context,
                    context_payload=context_payload,
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

    @staticmethod
    async def _try_recommend_guidance(user_query: str) -> dict | None:
        """
        no-hit/需澄清场景的轻量引导数据（tag/catalog 导航）

        约束：
        - 只返回导航信息，不返回 element_id/指针
        - 失败时静默降级，不影响主链路
        """
        try:
            raw = await recommend_guidance.ainvoke({"user_query": user_query})
            parsed = json.loads(raw or "")
            if isinstance(parsed, dict) and parsed.get("status") == "success":
                return parsed
            return None
        except Exception:
            return None

    async def _generate_job_sql(
        self,
        *,
        user_query: str,
        job: Job,
        agent_context: AgentScopedContext,
        context_payload: dict,
        test_feedback: str = "",
    ) -> tuple[str, bool, list[str]]:
        """为整个 Job 生成 SQL 脚本（通过工具获取知识）"""
        # 收集持久化输入表（跳过临时表，临时表在 temp 库下）
        all_input_tables = set(job.input_tables or [])
        output_table = job.output_table

        # 通过工具获取表结构
        table_schemas = await self._get_table_schemas_via_tool(list(all_input_tables), agent_context=agent_context)

        # 通过工具获取列级血缘
        column_lineage = await self._get_column_lineage_via_tool(
            list(all_input_tables), output_table, agent_context=agent_context
        )

        # 通过工具精准匹配历史 SQL（根据血缘关系）
        reference_sql = await self._get_reference_sql_via_tool(
            list(all_input_tables), output_table, agent_context=agent_context
        )

        # 格式化 Stage 信息（用于模型快速扫读；结构化数据仍在 task_payload.current_job.stages）
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
                    "tools_description": agent_context.get_tools_description(),
                    "test_feedback": test_feedback,
                }

                # 使用带工具的 LLM 生成 SQL
                sql = await self._generate_sql_with_tools(
                    user_query=user_query,
                    task_payload=task_payload,
                    agent_context=agent_context,
                    context_payload=context_payload,
                )

                if not sql or len(sql) < 20:
                    continue

                if not any(kw in sql.upper() for kw in ["SELECT", "INSERT", "CREATE"]):
                    continue

                return sql, True, []

            except Exception as e:
                logger.error(f"Job {job.id} SQL 生成失败 (尝试 {attempt + 1}): {e}")

        return "", False, [f"Job {job.id} SQL 生成失败"]

    async def _generate_sql_with_tools(
        self,
        *,
        user_query: str,
        task_payload: dict,
        agent_context: AgentScopedContext,
        context_payload: dict,
    ) -> str:
        """使用工具生成 SQL"""
        llm_with_tools = self._bind_tools_by_allowlist(agent_context)
        messages = [
            SystemMessage(content=DEVELOPER_AGENT_SYSTEM_INSTRUCTIONS),
            SystemMessage(content=json.dumps(task_payload, ensure_ascii=False)),
            SystemMessage(content=json.dumps(context_payload, ensure_ascii=False)),
            HumanMessage(content=user_query),
        ]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
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

                tool_result = await self._execute_tool(tool_name, tool_args, agent_context=agent_context)

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，获取最终响应
        response = await self.llm.ainvoke(messages)
        return self._clean_sql(response.content)

    def _bind_tools_by_allowlist(self, agent_context: AgentScopedContext):
        """
        按 allowlist 动态绑定工具，避免硬编码导致的“越权/误导”。

        说明：
        - bind_tools 决定 LLM 能否发起工具调用（能力面）
        - allowlist 决定该 Agent 是否允许调用（权限面）
        """
        allowlist = set(agent_context.tools or [])
        tool_registry = {
            "get_table_columns": get_table_columns,
            "get_column_value_domain": get_column_value_domain,
            "get_column_lineage": get_column_lineage,
            "get_sql_by_lineage": get_sql_by_lineage,
        }
        tools = [tool_registry[name] for name in allowlist if name in tool_registry]
        return self.llm.bind_tools(tools)

    async def _execute_tool(self, tool_name: str, tool_args: dict, *, agent_context: AgentScopedContext) -> str:
        """执行工具调用"""
        try:
            allowlist = set(agent_context.tools or [])
            if tool_name not in allowlist:
                return json.dumps(
                    {"status": "error", "message": f"工具不在 allowlist 中: {tool_name}"},
                    ensure_ascii=False,
                )

            table_index = self._build_allowed_table_index(agent_context.etl_pointers)
            column_index = self._build_allowed_column_index(agent_context.etl_pointers)

            if tool_name == "get_table_columns":
                table_name = (tool_args or {}).get("table_name") or ""
                pointer = table_index.get(table_name)
                if not pointer:
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "禁止对未下发的表指针调用工具",
                            "table_name": table_name,
                        },
                        ensure_ascii=False,
                    )
                if tool_name not in set(pointer.tools or []):
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "该表指针未授权此工具（ETLPointer.tools）",
                            "table_name": table_name,
                            "pointer_element_id": pointer.element_id,
                        },
                        ensure_ascii=False,
                    )
                return await get_table_columns.ainvoke({"table_name": table_name})

            if tool_name == "get_column_value_domain":
                column_element_id = (tool_args or {}).get("column_element_id") or ""
                pointer = column_index.get(column_element_id)
                if not pointer:
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "禁止对未下发的列指针调用工具",
                            "column_element_id": column_element_id,
                        },
                        ensure_ascii=False,
                    )
                if tool_name not in set(pointer.tools or []):
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "该列指针未授权此工具（ETLPointer.tools）",
                            "column_element_id": column_element_id,
                            "pointer_element_id": pointer.element_id,
                        },
                        ensure_ascii=False,
                    )
                return await get_column_value_domain.ainvoke({"column_element_id": column_element_id})

            if tool_name == "get_column_lineage":
                source_table = (tool_args or {}).get("source_table") or ""
                target_table = (tool_args or {}).get("target_table") or ""
                missing = [t for t in [source_table, target_table] if not table_index.get(t)]
                if missing:
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "禁止对未下发的表指针调用工具",
                            "tables": missing,
                        },
                        ensure_ascii=False,
                    )
                for t in [source_table, target_table]:
                    pointer = table_index[t]
                    if tool_name not in set(pointer.tools or []):
                        return json.dumps(
                            {
                                "status": "error",
                                "message": "该表指针未授权此工具（ETLPointer.tools）",
                                "table_name": t,
                                "pointer_element_id": pointer.element_id,
                            },
                            ensure_ascii=False,
                        )
                return await get_column_lineage.ainvoke({"source_table": source_table, "target_table": target_table})

            if tool_name == "get_sql_by_lineage":
                source_tables = (tool_args or {}).get("source_tables") or []
                target_table = (tool_args or {}).get("target_table") or ""
                if not isinstance(source_tables, list):
                    source_tables = []
                all_tables = list(source_tables) + ([target_table] if target_table else [])
                missing = [t for t in all_tables if t and not table_index.get(t)]
                if missing:
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "禁止对未下发的表指针调用工具",
                            "tables": missing,
                        },
                        ensure_ascii=False,
                    )
                for t in all_tables:
                    if not t:
                        continue
                    pointer = table_index[t]
                    if tool_name not in set(pointer.tools or []):
                        return json.dumps(
                            {
                                "status": "error",
                                "message": "该表指针未授权此工具（ETLPointer.tools）",
                                "table_name": t,
                                "pointer_element_id": pointer.element_id,
                            },
                            ensure_ascii=False,
                        )
                return await get_sql_by_lineage.ainvoke({"source_tables": source_tables, "target_table": target_table})

            return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"}, ensure_ascii=False)
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)}, ensure_ascii=False)

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

    async def _get_table_schemas_via_tool(self, input_tables: list[str], *, agent_context: AgentScopedContext) -> str:
        """通过工具获取表结构信息"""
        lines = []

        for table_name in input_tables:
            try:
                result = await self._execute_tool(
                    "get_table_columns",
                    {"table_name": table_name},
                    agent_context=agent_context,
                )
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
        self, input_tables: list[str], output_table: str | None, *, agent_context: AgentScopedContext
    ) -> str:
        """通过工具获取列级血缘信息"""
        if not output_table or not input_tables:
            return "（无）"

        lines = []
        for source_table in input_tables:
            try:
                result = await self._execute_tool(
                    "get_column_lineage",
                    {"source_table": source_table, "target_table": output_table},
                    agent_context=agent_context,
                )
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
        self, input_tables: list[str], output_table: str | None, *, agent_context: AgentScopedContext
    ) -> str:
        """通过工具精准匹配历史 SQL（根据血缘关系）"""
        if not input_tables or not output_table:
            return "（无历史 SQL）"

        try:
            result = await self._execute_tool(
                "get_sql_by_lineage",
                {"source_tables": input_tables, "target_table": output_table},
                agent_context=agent_context,
            )
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
    def _build_context_payload(*, agent_context: AgentScopedContext) -> dict:
        """
        构造“知识上下文JSON”（下发给 LLM 的 SystemMessage）

        约束：
        - 只传递指针与导航信息，不传递表明细
        """
        node_pointers = agent_context.etl_pointers or []
        table_pointers = [
            {
                "element_id": p.element_id,
                "qualified_name": p.qualified_name,
                "path": p.path,
                "display_name": p.display_name,
                "description": p.description,
                "tools": p.tools,
            }
            for p in node_pointers
            if "Table" in set(p.labels or []) and p.qualified_name
        ]
        return {
            "agent_type": agent_context.agent_type,
            "allowlist_tools": agent_context.tools,
            "tables": agent_context.tables,
            "table_pointers": table_pointers,
            "etl_pointers": [p.model_dump() for p in node_pointers],
            "doc_pointers": [p.model_dump() for p in (agent_context.doc_pointers or [])],
        }

    @staticmethod
    def _build_allowed_table_index(node_pointers) -> dict:
        table_index: dict[str, object] = {}
        for p in node_pointers or []:
            if "Table" not in set(getattr(p, "labels", None) or []):
                continue
            qualified_name = getattr(p, "qualified_name", None)
            if not qualified_name:
                continue
            table_index[qualified_name] = p
        return table_index

    @staticmethod
    def _build_allowed_column_index(node_pointers) -> dict:
        column_index: dict[str, object] = {}
        for p in node_pointers or []:
            if "Column" not in set(getattr(p, "labels", None) or []):
                continue
            element_id = getattr(p, "element_id", None)
            if element_id:
                column_index[element_id] = p
        return column_index

    @staticmethod
    def _build_allowed_tables(node_pointers) -> set[str]:
        allowed: set[str] = set()
        for p in node_pointers or []:
            if "Table" not in set(getattr(p, "labels", None) or []):
                continue
            qualified_name = getattr(p, "qualified_name", None)
            if qualified_name:
                allowed.add(qualified_name)
        return allowed

    @staticmethod
    def _find_unknown_tables(plan: Workflow, *, allowed_tables: set[str]) -> list[str]:
        unknown: list[str] = []
        seen: set[str] = set()
        for job in plan.jobs or []:
            for t in job.input_tables or []:
                if not t or t.startswith("temp."):
                    continue
                if t not in allowed_tables and t not in seen:
                    seen.add(t)
                    unknown.append(t)
            if job.output_table and not job.output_table.startswith("temp."):
                t = job.output_table
                if t not in allowed_tables and t not in seen:
                    seen.add(t)
                    unknown.append(t)
        return unknown

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
