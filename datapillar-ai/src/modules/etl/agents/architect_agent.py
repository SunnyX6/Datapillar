"""
Architect Agent（数据架构师）

职责：技术层面的规划
- 根据业务分析结果，决定技术实现方案
- 选择合适的组件（HIVE/SPARK_SQL/FLINK 等）
- 决定需要几个 Job（前端节点）
- 规划每个 Job 的 Stage（SQL 执行单元）
- 通过工具获取血缘和组件信息
"""

import json
import logging
import re
from collections import Counter

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.types import Command, interrupt

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, GlobalKGContext
from src.modules.etl.schemas.plan import Job, Stage, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import get_table_lineage, list_component

logger = logging.getLogger(__name__)


ARCHITECT_AGENT_PROMPT = """你是资深数据架构师。

## 任务
根据业务分析结果，设计技术实现方案：
1. 决定需要几个 Job（前端节点）
2. 规划每个 Job 的 Stage（SQL 执行单元）
3. 确定 Job 之间的调度依赖

## 业务分析结果
{analysis_result}

## 用户选择的组件
{selected_component}
（所有 Job 都使用此组件）

## 知识上下文

### 相关表（KnowledgeAgent 发现的）
{discovered_tables}

### 表级血缘（已有的数据流向）
{lineage_info}

### 可用工具
{tools_description}

## 用户原始需求
{user_query}

## 设计原则
1. **Job 划分**：
   - 简单需求用一个 Job
   - 每个 Job 对应前端一个节点
   - 所有 Job 的 type 都使用用户选择的组件: {selected_component}

2. **Job 依赖**（调度依赖）：
   - Job 之间是调度依赖，不是数据依赖
   - 如果 Job B 读的表是 Job A 写的，则 Job B 依赖 Job A
   - 参考表级血缘推导依赖关系

3. **Stage 规划**：
   - Stage 是 Job 内部的执行单元
   - 临时表只在 Job 内部 Stage 之间使用
   - 跨 Job 必须用持久化表

## 输出格式

```json
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
```

只输出 JSON，不要解释。
"""


# 绑定的工具
ARCHITECT_TOOLS = [get_table_lineage, list_component]


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
        self.llm_with_tools = self.llm.bind_tools(ARCHITECT_TOOLS)
        self.max_tool_calls = 4

    async def __call__(self, state: AgentState) -> Command:
        """执行架构设计"""
        analysis_result = state.analysis_result
        user_input = state.user_input

        if not analysis_result:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少需求分析结果，无法设计架构")],
                    "current_agent": "architect_agent",
                    "error": "缺少需求分析结果",
                }
            )

        logger.info("🏗️ ArchitectAgent 开始设计架构")

        # 获取上下文
        global_kg_context = state.get_global_kg_context()
        agent_context = state.get_agent_context(AgentType.ARCHITECT)

        if not global_kg_context:
            global_kg_context = GlobalKGContext()

        if not agent_context:
            agent_context = AgentScopedContext.create_for_agent(
                agent_type=AgentType.ARCHITECT,
                tables=[],
                user_query=user_input,
            )

        # 解析 AnalysisResult
        if isinstance(analysis_result, dict):
            analysis = AnalysisResult(**analysis_result)
        else:
            analysis = analysis_result

        # 获取可用组件
        components = global_kg_context.components
        if not components:
            # 通过工具获取组件列表
            components_result = await self._get_components()
            if not components_result:
                return Command(
                    update={
                        "messages": [AIMessage(content="未找到可用组件")],
                        "current_agent": "architect_agent",
                        "error": "未找到可用组件",
                    }
                )
            # 解析组件列表用于展示
            components = components_result

        # 检查是否已选择组件（interrupt 恢复后会有）
        selected_component = state.metadata.get("selected_component")

        if not selected_component:
            # 让用户选择组件
            selected_component = self._ask_user_select_component(components)
            # interrupt 返回后，selected_component 是用户选择的值

        logger.info(f"📦 用户选择组件: {selected_component}")

        try:
            # 执行架构设计（带工具调用）
            result_dict = await self._design_with_tools(
                analysis=analysis,
                selected_component=selected_component,
                agent_context=agent_context,
                global_kg_context=global_kg_context,
                user_query=user_input,
            )

            # 构建 Workflow（强制使用用户选择的组件）
            workflow_plan = self._build_workflow(result_dict, analysis, selected_component)

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

            logger.info(
                f"✅ ArchitectAgent 完成设计: {workflow_plan.name}, "
                f"Job 数={len(workflow_plan.jobs)}, 风险={len(workflow_plan.risks)}"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"架构设计完成: {workflow_plan.name}")],
                    "architecture_plan": workflow_plan.model_dump(),
                    "current_agent": "architect_agent",
                    "metadata": {**state.metadata, "selected_component": selected_component},
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

    async def _get_components(self) -> list[dict]:
        """通过工具获取组件列表"""
        try:
            result = list_component.invoke({})
            data = json.loads(result)
            if data.get("status") == "success":
                return data.get("components", [])
            return []
        except Exception as e:
            logger.error(f"获取组件列表失败: {e}")
            return []

    def _ask_user_select_component(self, components: list) -> str:
        """
        使用 interrupt 让用户选择组件

        Args:
            components: 可用组件列表

        Returns:
            用户选择的组件代码
        """
        # 构建组件选项
        options = []
        for comp in components:
            if isinstance(comp, dict):
                code = comp.get("component_code", comp.get("code", ""))
                name = comp.get("component_name", comp.get("name", ""))
                comp_type = comp.get("component_type", comp.get("type", ""))
            else:
                code = comp.code
                name = comp.name
                comp_type = comp.type

            options.append({
                "value": code,
                "label": f"{code}: {name}",
                "type": comp_type,
            })

        message = "请选择要使用的技术组件："

        logger.info(f"⏸️ 等待用户选择组件...")

        # 使用 interrupt 暂停执行
        user_selection = interrupt({
            "type": "component_selection",
            "message": message,
            "options": options,
        })

        # 返回用户选择的组件
        if isinstance(user_selection, dict):
            return user_selection.get("component", options[0]["value"] if options else "HIVE")
        return user_selection or (options[0]["value"] if options else "HIVE")

    async def _design_with_tools(
        self,
        analysis: AnalysisResult,
        selected_component: str,
        agent_context: AgentScopedContext,
        global_kg_context: GlobalKGContext,
        user_query: str,
    ) -> dict:
        """执行带工具调用的架构设计"""
        # 格式化血缘信息
        lineage_lines = []
        for edge in global_kg_context.lineage_graph[:20]:
            lineage_lines.append(f"- {edge.source_table} → {edge.target_table}")

        prompt = ARCHITECT_AGENT_PROMPT.format(
            analysis_result=json.dumps(analysis.model_dump(), ensure_ascii=False, indent=2),
            selected_component=selected_component,
            discovered_tables=", ".join(agent_context.tables) if agent_context.tables else "（无）",
            lineage_info="\n".join(lineage_lines) if lineage_lines else "（无）",
            tools_description=agent_context.get_tools_description(),
            user_query=user_query,
        )

        messages = [HumanMessage(content=prompt)]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await self.llm_with_tools.ainvoke(messages)
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

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，获取最终响应
        response = await self.llm.ainvoke(messages)
        return self._parse_response(response.content)

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用"""
        try:
            if tool_name == "get_table_lineage":
                return await get_table_lineage.ainvoke(tool_args)
            elif tool_name == "list_component":
                return list_component.invoke(tool_args)
            else:
                return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"})
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    def _parse_response(self, content: str) -> dict:
        """解析 LLM 响应"""
        json_match = re.search(r'```json\s*([\s\S]*?)\s*```', content)
        if json_match:
            return json.loads(json_match.group(1))

        json_match = re.search(r'\{[\s\S]*\}', content)
        if json_match:
            return json.loads(json_match.group())

        raise ValueError("无法解析 LLM 响应为 JSON")

    def _build_workflow(
        self,
        result_dict: dict,
        analysis: AnalysisResult,
        selected_component: str,
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
