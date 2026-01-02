"""
Architect Agent（数据架构师）

职责：技术层面的规划
- 根据需求分析结果（AnalystAgent 产物），决定技术实现方案
- 选择合适的组件（HIVE/SPARK_SQL/FLINK 等）
- 决定需要几个 Job（前端节点）
- 规划每个 Job 的 Stage（SQL 执行单元）
- 通过工具获取血缘和组件信息
"""

import json
import logging
import uuid

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType
from src.modules.etl.schemas.plan import Job, Stage, Workflow
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import get_table_lineage, list_component

logger = logging.getLogger(__name__)


ARCHITECT_AGENT_SYSTEM_INSTRUCTIONS = """你是资深数据架构师。

## 任务
根据需求分析结果，设计技术实现方案：
1. 决定需要几个 Job（前端节点）
2. 规划每个 Job 的 Stage（SQL 执行单元）
3. 确定 Job 之间的调度依赖

## 任务参数（系统注入，不是用户输入）
系统会提供一段“任务参数 JSON”（SystemMessage），其中包含：
- analysis_result：需求分析结果（AnalystAgent 产物，严格 JSON）
- selected_component：用户选择的组件（所有 Job 都使用此组件）
- tools_description：可用工具说明（用于决定何时调用工具）

## 知识上下文（系统注入，不是用户输入）
系统会提供一段“知识上下文 JSON”（SystemMessage），其中包含：
- tables：可用的 schema.table 列表（导航指针）
- table_pointers/etl_pointers：可验证的 ETL 指针（含 qualified_name/element_id/tools）
- allowlist_tools：你允许调用的工具名列表

你必须把该 JSON 视为唯一可信知识入口：
- 禁止臆造任何 schema.table
- 工具调用只能使用该 JSON 中出现的表指针（按 qualified_name 精确匹配）
- 仅当 ETLPointer.tools 包含工具名时，才允许对该表调用该工具

## 设计原则
1. **Job 划分**：
   - 简单需求用一个 Job
   - 每个 Job 对应前端一个节点
   - 所有 Job 的 type 都使用 selected_component

2. **Job 依赖**（调度依赖）：
   - Job 之间是调度依赖，不是数据依赖
   - 如果 Job B 读的表是 Job A 写的，则 Job B 依赖 Job A
   - 参考表级血缘推导依赖关系

3. **Stage 规划**：
   - Stage 是 Job 内部的执行单元
   - 临时表只在 Job 内部 Stage 之间使用
   - 跨 Job 必须用持久化表

## 输出格式
{{
  "name": "工作流名称",
  "description": "工作流描述",
  "jobs": [
    {{
      "id": "job_1",
      "name": "Job 名称",
      "description": "Job 描述",
      "type": "{selected_component}",
      "depends": ["依赖的 Job ID（调度依赖）"],
      "step_ids": ["关联的业务步骤 ID"],
      "input_tables": ["读取的持久化表"],
      "output_table": "写入的持久化表",
      "stages": [
        {{
          "stage_id": 1,
          "name": "Stage 名称",
          "description": "这个 Stage 做什么",
          "input_tables": ["输入表"],
          "output_table": "输出表或临时表",
          "is_temp_table": true
        }}
      ]
    }}
  ],
  "risks": ["架构风险点"],
  "confidence": 0.85
}}

重要：
- **必须输出纯 JSON**：不得输出 Markdown、不得输出 ```json 代码块、不得输出解释性文字

只输出 JSON，不要解释。
"""


class ArchitectAgent:
    """
    数据架构师

    职责：
    1. 让用户选择组件（技术栈）
    2. 通过工具获取血缘信息
    3. 决定需要几个 Job
    4. 规划每个 Job 的 Stage
    5. 识别架构风险
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        self.llm_json = call_llm(temperature=0.0, enable_json_mode=True)
        self.max_tool_calls = 4

    async def __call__(self, state: AgentState) -> Command:
        """执行架构设计"""
        analysis_result = state.analysis_result
        user_input = state.user_input

        if not analysis_result:
            req = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="delegate",
                created_by="architect_agent",
                target_agent="analyst_agent",
                resume_to="architect_agent",
                payload={
                    "type": "need_analysis_result",
                    "message": "架构设计需要需求分析结果，已委派需求分析师先完成需求收敛。",
                },
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="缺少需求分析结果，已委派需求分析师")],
                    "current_agent": "architect_agent",
                    "pending_requests": [r.model_dump() for r in pending],
                }
            )

        logger.info("🏗️ ArchitectAgent 开始设计架构")

        # 获取上下文
        agent_context = state.get_agent_context(AgentType.ARCHITECT)

        if not agent_context:
            agent_context = AgentScopedContext.create_for_agent(
                agent_type=AgentType.ARCHITECT,
                tables=[],
            )
        context_payload = self._build_context_payload(agent_context=agent_context)
        llm_with_tools = self._bind_tools_by_allowlist(agent_context)

        # 解析 AnalysisResult
        if isinstance(analysis_result, dict):
            analysis = AnalysisResult(**analysis_result)
        else:
            analysis = analysis_result

        # 获取可用组件（必须通过工具获取，禁止依赖全局上下文缓存）
        components = await self._get_components(agent_context=agent_context)
        if not components:
            return Command(
                update={
                    "messages": [AIMessage(content="未找到可用组件")],
                    "current_agent": "architect_agent",
                    "error": "未找到可用组件",
                }
            )

        # 检查是否已选择组件（由统一 human_in_the_loop 写回）
        selected_component = state.selected_component
        if not selected_component:
            options = []
            for comp in components:
                comp_id = comp.get("id")
                code = comp.get("code", comp.get("component_code", ""))
                name = comp.get("name", comp.get("component_name", ""))
                comp_type = comp.get("type", comp.get("component_type", ""))
                options.append({
                    "value": code,
                    "label": f"{code}: {name}",
                    "type": comp_type,
                    "id": comp_id,
                })

            req = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="human",
                created_by="architect_agent",
                resume_to="blackboard_router",
                payload={
                    "type": "component_selection",
                    "message": "请选择要使用的技术组件：",
                    "options": options,
                    "writeback_key": "selected_component",
                },
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="需要你先选择技术组件，才能继续架构设计")],
                    "pending_requests": [r.model_dump() for r in pending],
                    "current_agent": "architect_agent",
                }
            )

        selected_component_id = state.selected_component_id
        for comp in components:
            code = comp.get("code", comp.get("component_code", ""))
            if code == selected_component:
                selected_component_id = comp.get("id")
                break

        logger.info(f"📦 用户选择组件: {selected_component} (id={selected_component_id})")

        try:
            # 执行架构设计（带工具调用）
            result_dict = await self._design_with_tools(
                analysis=analysis,
                selected_component=selected_component,
                agent_context=agent_context,
                context_payload=context_payload,
                llm_with_tools=llm_with_tools,
                user_query=user_input,
            )

            # 构建 Workflow（强制使用用户选择的组件）
            workflow_plan = self._build_workflow(
                result_dict, analysis, selected_component, selected_component_id
            )

            # DAG 验证
            dag_errors = workflow_plan.validate_dag()
            if dag_errors:
                workflow_plan.risks.extend(dag_errors)

            # 数据依赖校验
            dep_errors, dep_warnings = workflow_plan.validate_data_dependencies()
            if dep_errors:
                fixes = workflow_plan.fix_missing_dependencies()
                for fix in fixes:
                    logger.info(f"🔧 {fix}")
                    workflow_plan.risks.append(f"[已自动修复] {fix}")

                dep_errors_after, _ = workflow_plan.validate_data_dependencies()
                if dep_errors_after:
                    workflow_plan.risks.extend(dep_errors_after)

            # 临时表作用域校验
            temp_table_errors = workflow_plan.validate_temp_table_scope()
            if temp_table_errors:
                workflow_plan.risks.extend(temp_table_errors)
                for err in temp_table_errors:
                    logger.warning(f"⚠️ 临时表作用域问题: {err}")

            # 表指针约束：禁止引入知识上下文之外的持久化表（temp.* 除外）
            allowed_tables = self._build_allowed_tables(agent_context.etl_pointers)
            unknown_tables = self._find_unknown_tables(workflow_plan, allowed_tables=allowed_tables)
            if unknown_tables:
                counters = dict(state.delegation_counters or {})
                counter_key = "architect_agent:delegate:knowledge_agent:unknown_tables"
                delegated = int(counters.get(counter_key) or 0)
                if delegated < 1:
                    counters[counter_key] = delegated + 1
                    req = BlackboardRequest(
                        request_id=f"req_{uuid.uuid4().hex}",
                        kind="delegate",
                        created_by="architect_agent",
                        target_agent="knowledge_agent",
                        resume_to="architect_agent",
                        payload={
                            "type": "refresh_knowledge",
                            "reason": "unknown_tables",
                            "unknown_tables": unknown_tables,
                            "message": "架构设计阶段发现未知表，已委派知识检索刷新上下文后再继续。",
                        },
                    )
                    pending = list(state.pending_requests or [])
                    pending.append(req)
                    return Command(
                        update={
                            "messages": [AIMessage(content="检测到未知表，已委派知识检索刷新上下文")],
                            "current_agent": "architect_agent",
                            "pending_requests": [r.model_dump() for r in pending],
                            "delegation_counters": counters,
                        }
                    )
                request_id = f"req_{uuid.uuid4().hex}"
                req = BlackboardRequest(
                    request_id=request_id,
                    kind="human",
                    created_by="architect_agent",
                    resume_to="blackboard_router",
                    payload={
                        "type": "clarification",
                        "message": "架构设计无法继续：知识库无法定位工作流中引用的表，请补充可验证线索。",
                        "questions": [
                            f"请确认这些表是否存在及其准确名称（推荐 schema.table）：{', '.join(unknown_tables[:12])}",
                            "如果你不确定表名：请提供字段清单/样例数据/现有 SQL，或说明上游来源系统与目标表。",
                        ],
                    },
                )
                pending = list(state.pending_requests or [])
                pending.append(req)
                return Command(
                    update={
                        "messages": [AIMessage(content="无法定位表指针：需要你补充上下文信息后才能继续")],
                        "current_agent": "architect_agent",
                        "pending_requests": [r.model_dump() for r in pending],
                        "delegation_counters": counters,
                    }
                )

            logger.info(
                f"✅ ArchitectAgent 完成设计: {workflow_plan.name}, "
                f"Job 数={len(workflow_plan.jobs)}, 风险={len(workflow_plan.risks)}"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"架构设计完成: {workflow_plan.name}")],
                    "architecture_plan": workflow_plan.model_dump(),
                    "current_agent": "architect_agent",
                    "selected_component": selected_component,
                    "selected_component_id": selected_component_id,
                }
            )

        except Exception as e:
            logger.error(f"ArchitectAgent 设计失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"架构设计失败: {str(e)}")],
                    "current_agent": "architect_agent",
                    "error": str(e),
                }
            )

    async def _get_components(self, *, agent_context: AgentScopedContext) -> list[dict]:
        """通过工具获取组件列表"""
        if "list_component" not in set(agent_context.tools or []):
            logger.error("工具不在 allowlist 中: list_component")
            return []
        try:
            result = list_component.invoke({})
            data = json.loads(result)
            if data.get("status") == "success":
                return data.get("components", [])
            return []
        except Exception as e:
            logger.error(f"获取组件列表失败: {e}")
            return []

    async def _design_with_tools(
        self,
        analysis: AnalysisResult,
        selected_component: str,
        agent_context: AgentScopedContext,
        context_payload: dict,
        llm_with_tools,
        user_query: str,
    ) -> dict:
        """执行带工具调用的架构设计"""
        task_payload = {
            "analysis_result": analysis.model_dump(),
            "selected_component": selected_component,
            "tools_description": agent_context.get_tools_description(),
        }

        messages = [
            SystemMessage(content=ARCHITECT_AGENT_SYSTEM_INSTRUCTIONS),
            SystemMessage(content=json.dumps(task_payload, ensure_ascii=False)),
            SystemMessage(content=json.dumps(context_payload, ensure_ascii=False)),
            HumanMessage(content=user_query),
        ]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
            messages.append(response)

            # 如果没有工具调用，解析响应
            if not response.tool_calls:
                return self._parse_response(response.content)

            # 执行工具调用
            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 ArchitectAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args, agent_context=agent_context)

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，强制使用 JSON 模式获取最终响应
        response = await self.llm_json.ainvoke(messages)
        return self._parse_response(response.content)

    def _bind_tools_by_allowlist(self, agent_context: AgentScopedContext):
        """
        按 allowlist 动态绑定工具，避免硬编码导致的“越权/误导”。

        说明：
        - bind_tools 决定 LLM 能否发起工具调用（能力面）
        - allowlist 决定该 Agent 是否允许调用（权限面）
        """
        allowlist = set(agent_context.tools or [])
        tool_registry = {
            "get_table_lineage": get_table_lineage,
            "list_component": list_component,
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

            if tool_name == "get_table_lineage":
                table_name = (tool_args or {}).get("table_name") or ""
                direction = (tool_args or {}).get("direction") or "both"
                table_index = self._build_allowed_table_index(agent_context.etl_pointers)
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
                return await get_table_lineage.ainvoke({"table_name": table_name, "direction": direction})

            if tool_name == "list_component":
                return list_component.invoke(tool_args)

            return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"}, ensure_ascii=False)
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)}, ensure_ascii=False)

    def _parse_response(self, content: str) -> dict:
        """严格解析 LLM 响应（必须是纯 JSON）"""
        text = (content or "").strip()
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as e:
            raise ValueError("LLM 输出不是合法 JSON（必须输出纯 JSON）") from e
        if not isinstance(parsed, dict):
            raise ValueError("LLM 输出必须是 JSON object")
        return parsed

    def _build_workflow(
        self,
        result_dict: dict,
        analysis: AnalysisResult,
        selected_component: str,
        selected_component_id: int | None,
    ) -> Workflow:
        """构建 Workflow 对象（强制使用用户选择的组件）"""
        jobs = []
        for job_dict in result_dict.get("jobs", []):
            # 构建 Stage 列表
            stages = []
            for stage_dict in job_dict.get("stages", []):
                stage = Stage(
                    stage_id=stage_dict.get("stage_id", 1),
                    name=stage_dict.get("name", ""),
                    description=stage_dict.get("description", ""),
                    input_tables=stage_dict.get("input_tables", []),
                    output_table=stage_dict.get("output_table", ""),
                    is_temp_table=stage_dict.get("is_temp_table", True),
                )
                stages.append(stage)

            job = Job(
                id=job_dict.get("id", ""),
                name=job_dict.get("name", ""),
                description=job_dict.get("description"),
                type=selected_component,
                type_id=selected_component_id,
                depends=job_dict.get("depends", []),
                step_ids=job_dict.get("step_ids", []),
                stages=stages,
                input_tables=job_dict.get("input_tables", []),
                output_table=job_dict.get("output_table"),
                config_generated=False,
                config_validated=False,
            )
            jobs.append(job)

        return Workflow(
            name=result_dict.get("name", analysis.summary[:50] if analysis.summary else "etl_workflow"),
            description=result_dict.get("description", analysis.summary),
            schedule=None,
            env="dev",
            jobs=jobs,
            risks=result_dict.get("risks", []),
            decision_points=[],
            confidence=result_dict.get("confidence", analysis.confidence),
        )

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
