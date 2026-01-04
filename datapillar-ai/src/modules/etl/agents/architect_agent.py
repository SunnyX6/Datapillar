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

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.plan import Job, Stage, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.tools.agent_tools import get_table_lineage, list_component

logger = logging.getLogger(__name__)


def _tool_error(message: str, **extra: object) -> str:
    """构造工具错误响应"""
    payload: dict[str, object] = {"status": "error", "message": message}
    payload.update(extra)
    return json.dumps(payload, ensure_ascii=False)


ARCHITECT_AGENT_SYSTEM_INSTRUCTIONS = """你是资深数据架构师。

## 任务
根据需求分析结果，设计技术实现方案：
1. 决定需要几个 Job（前端节点）
2. 规划每个 Job 的 Stage（SQL 执行单元）
3. 确定 Job 之间的调度依赖

## 任务参数（系统注入，不是用户输入）
系统会提供一段"任务参数 JSON"（SystemMessage），其中包含：
- analysis_result：需求分析结果（AnalystAgent 产物，严格 JSON）
- selected_component：用户选择的组件（本 Agent 的交互结果，用于本次架构规划）

## 知识上下文（系统注入，不是用户输入）
系统会提供一段"知识上下文 JSON"（SystemMessage），其中包含：
- tables：可用的 schema.table 列表（导航指针）
- etl_pointers：可验证的 ETL 指针（含 qualified_name/element_id/tools/labels）
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
        self.allowlist = get_agent_tools(AgentType.ARCHITECT)

    async def run(
        self,
        *,
        user_query: str,
        analysis_result: AnalysisResult,
        selected_component: str,
        selected_component_id: int | None = None,
        knowledge_agent=None,
    ) -> AgentResult:
        """
        执行架构设计

        参数：
        - user_query: 用户输入
        - analysis_result: 需求分析结果
        - selected_component: 用户选择的组件
        - selected_component_id: 组件 ID
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）

        返回：
        - AgentResult: 执行结果
        """
        self._knowledge_agent = knowledge_agent

        logger.info(f"🏗️ ArchitectAgent 开始设计架构, 组件: {selected_component}")

        try:
            llm_with_tools = self._bind_tools()

            result_dict = await self._design_with_tools(
                analysis=analysis_result,
                selected_component=selected_component,
                llm_with_tools=llm_with_tools,
                user_query=user_query,
            )

            workflow_plan = self._build_workflow(
                result_dict, analysis_result, selected_component, selected_component_id
            )

            dag_errors = workflow_plan.validate_dag()
            if dag_errors:
                workflow_plan.risks.extend(dag_errors)

            dep_errors, dep_warnings = workflow_plan.validate_data_dependencies()
            if dep_errors:
                fixes = workflow_plan.fix_missing_dependencies()
                for fix in fixes:
                    logger.info(f"🔧 {fix}")
                    workflow_plan.risks.append(f"[已自动修复] {fix}")

            temp_table_errors = workflow_plan.validate_temp_scope()
            if temp_table_errors:
                workflow_plan.risks.extend(temp_table_errors)

            logger.info(
                f"✅ ArchitectAgent 完成设计: {workflow_plan.name}, "
                f"Job 数={len(workflow_plan.jobs)}, 风险={len(workflow_plan.risks)}"
            )

            return AgentResult.completed(
                summary=f"架构设计完成: {workflow_plan.name}",
                deliverable=workflow_plan,
                deliverable_type="plan",
            )

        except Exception as e:
            logger.error(f"ArchitectAgent 设计失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"架构设计失败: {str(e)}",
                error=str(e),
            )

    async def get_components(self) -> list[dict]:
        """通过工具获取组件列表"""
        if "list_component" not in self.allowlist:
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
        llm_with_tools,
        user_query: str,
    ) -> dict:
        """执行带工具调用的架构设计"""
        task_payload = {
            "analysis_result": analysis.model_dump(),
            "selected_component": selected_component,
        }

        messages = build_llm_messages(
            system_instructions=ARCHITECT_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="architect_agent",
            user_query=user_query,
            task_payload=task_payload,
        )
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
            messages.append(response)

            if not response.tool_calls:
                return self._parse_response(response.content)

            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 ArchitectAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(ToolMessage(content=tool_result, tool_call_id=tool_id))

                if tool_call_count >= self.max_tool_calls:
                    break

        response = await self.llm_json.ainvoke(messages)
        return self._parse_response(response.content)

    def _bind_tools(self):
        """绑定工具到 LLM"""
        tool_registry = {
            "get_table_lineage": get_table_lineage,
            "list_component": list_component,
        }
        tools = [tool_registry[name] for name in self.allowlist if name in tool_registry]
        return self.llm.bind_tools(tools)

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
                return _tool_error(f"工具不在 allowlist 中: {tool_name}")

            # list_component 不需要指针，直接调用
            if tool_name == "list_component":
                return list_component.invoke(tool_args)

            if not self._knowledge_agent:
                return _tool_error("无法查询指针：knowledge_agent 未注入")

            if tool_name == "get_table_lineage":
                table_name = (tool_args or {}).get("table_name") or ""
                direction = (tool_args or {}).get("direction") or "both"
                if not table_name:
                    return _tool_error("缺少 table_name 参数")

                # 按需查询指针
                pointers = await self._knowledge_agent.query_pointers(
                    table_name,
                    node_types=["Table"],
                    top_k=5,
                )
                pointer = self._find_matching_pointer(pointers, table_name)
                if not pointer:
                    return _tool_error("未找到指针", table_name=table_name)
                if "get_table_lineage" not in (pointer.tools or []):
                    return _tool_error("指针未授权此工具", table_name=table_name)

                logger.info(f"📊 调用 get_table_lineage: {pointer.qualified_name}")
                return await get_table_lineage.ainvoke(
                    {"table_name": pointer.qualified_name, "direction": direction}
                )

            return _tool_error(f"未知工具: {tool_name}")
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return _tool_error(str(e))

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
            stages = []
            for stage_dict in job_dict.get("stages", []):
                stage = Stage(
                    stage_id=stage_dict.get("stage_id", 1),
                    name=stage_dict.get("name", ""),
                    description=stage_dict.get("description", ""),
                    input_tables=stage_dict.get("input_tables", []),
                    output_table=stage_dict.get("output_table", ""),
                    is_temp_table=stage_dict.get("is_temp_table", True),
                    sql=None,
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
            id=None,
            name=result_dict.get(
                "name", analysis.summary[:50] if analysis.summary else "etl_workflow"
            ),
            description=result_dict.get("description", analysis.summary),
            schedule=None,
            env="dev",
            jobs=jobs,
            risks=result_dict.get("risks", []),
            decision_points=[],
            confidence=result_dict.get("confidence", analysis.confidence),
        )
