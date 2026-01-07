"""
Architect Agent（数据架构师）

职责：技术层面的规划
- 根据需求分析结果（AnalystAgent 产物），决定技术实现方案
- 选择合适的组件（HIVE/SPARK_SQL/FLINK 等）
- 决定需要几个 Job（前端节点）
- 规划每个 Job 的 Stage（SQL 执行单元）
- 通过工具获取血缘和组件信息
"""

import asyncio
import json
import logging
from typing import Any

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.infrastructure.resilience import get_resilience_config
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import AnalysisResult
from src.modules.etl.schemas.workflow import Workflow, WorkflowOutput
from src.modules.etl.tools.component import list_component
from src.modules.etl.tools.table import get_table_lineage

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

## 任务参数（系统注入）
系统会提供 analysis_result（需求分析结果）和 selected_component（用户选择的组件）。

## 可用工具

### get_table_lineage
查询表的血缘关系（上下游表）。
- 用于推导 Job 之间的依赖关系
- 如果 Job B 读的表是 Job A 写的，则 Job B 依赖 Job A

### list_component
获取可用组件列表（可选）。

## 设计原则

### Job 划分
- 简单需求用一个 Job
- 每个 Job 对应前端一个节点
- 所有 Job 的 type 都使用 selected_component

### Job 依赖（调度依赖）
- Job 之间是调度依赖，不是数据依赖
- 如果 Job B 读的表是 Job A 写的，则 Job B 依赖 Job A
- 参考表级血缘推导依赖关系

### Stage 规划
- Stage 是 Job 内部的执行单元
- 临时表只在 Job 内部 Stage 之间使用
- 跨 Job 必须用持久化表

## 输出格式（JSON）
设计完成后，直接输出以下 JSON 格式：
```json
{
  "name": "工作流名称",
  "description": "工作流描述",
  "jobs": [
    {
      "id": "job_1",
      "name": "作业名称",
      "description": "作业描述",
      "depends": [],
      "step_ids": ["s1"],
      "stages": [
        {
          "stage_id": 1,
          "name": "Stage名称",
          "description": "Stage描述",
          "input_tables": ["schema.table"],
          "output_table": "schema.output_table",
          "is_temp_table": false
        }
      ],
      "input_tables": ["schema.table"],
      "output_table": "schema.output_table"
    }
  ],
  "risks": ["风险点1", "风险点2"],
  "confidence": 0.8
}
```

## 字段说明
- name: 工作流名称
- description: 工作流描述
- jobs: 作业列表
  - id: Job 唯一标识（job_1, job_2 格式）
  - name: Job 名称
  - depends: 依赖的上游 Job ID 列表
  - step_ids: 关联的业务步骤 ID
  - stages: Stage 列表
    - stage_id: Stage 序号（从 1 开始）
    - name: Stage 名称
    - input_tables: 读取的表
    - output_table: 输出的表
    - is_temp_table: 是否临时表
  - input_tables: Job 读取的持久化表
  - output_table: Job 写入的最终目标表
- risks: 架构风险点
- confidence: 置信度（复杂场景 < 0.8）

## 重要约束
1. 不允许臆造表名，必须使用工具验证或使用 analysis_result 中的表名
2. 设计完成后直接输出 JSON，不要调用任何工具
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
        config = get_resilience_config()
        self.max_iterations = config.max_iterations
        self.allowlist = get_agent_tools(AgentType.ARCHITECT)

    async def run(
        self,
        *,
        user_query: str,
        analysis_result: AnalysisResult,
        selected_component: str,
        selected_component_id: int | None = None,
        knowledge_agent=None,
        memory_context: dict[str, Any] | None = None,
    ) -> AgentResult:
        """
        执行架构设计

        参数：
        - user_query: 用户输入
        - analysis_result: 需求分析结果
        - selected_component: 用户选择的组件
        - selected_component_id: 组件 ID
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）
        - memory_context: 对话历史上下文（支持多轮对话）

        返回：
        - AgentResult: 执行结果
        """
        self._knowledge_agent = knowledge_agent

        logger.info(f"🏗️ ArchitectAgent 开始设计架构, 组件: {selected_component}")

        try:
            llm_with_tools = self._bind_tools()

            output = await self._design_with_tools(
                analysis=analysis_result,
                selected_component=selected_component,
                llm_with_tools=llm_with_tools,
                user_query=user_query,
                memory_context=memory_context,
            )

            workflow_plan = Workflow.from_output(output, selected_component, selected_component_id)

            # completed 标准：必须生成可执行的 Job/Stage 结构
            if not workflow_plan.jobs:
                return AgentResult.failed(
                    summary="架构设计失败：未生成任何 Job",
                    error="Workflow.jobs 为空",
                )
            jobs_missing_stages = [job.id for job in workflow_plan.jobs if not job.stages]
            if jobs_missing_stages:
                return AgentResult.failed(
                    summary=f"架构设计失败：存在没有 Stage 的 Job: {', '.join(jobs_missing_stages)}",
                    error=f"存在没有 Stage 的 Job: {', '.join(jobs_missing_stages)}",
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

            # 检查 LLM 返回的 confidence 和 risks，判断是否需要用户确认
            # 过滤掉已自动修复的风险，只保留需要用户关注的风险
            unresolved_risks = [r for r in workflow_plan.risks if not r.startswith("[已自动修复]")]
            if workflow_plan.confidence < 0.8 and unresolved_risks:
                logger.info(
                    f"⚠️ ArchitectAgent 需要确认: confidence={workflow_plan.confidence}, "
                    f"risks={unresolved_risks}"
                )
                return AgentResult.needs_clarification(
                    summary="架构方案需要确认",
                    message="架构设计存在一些风险点，需要你确认后才能继续",
                    questions=unresolved_risks,
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
        memory_context: dict[str, Any] | None = None,
    ) -> WorkflowOutput:
        """
        带工具调用的架构设计流程：
        1. 预先调用 KnowledgeAgent 获取候选表/列/值域（带权限过滤）
        2. 第一阶段：LLM 调用工具获取血缘信息（bind_tools + ToolMessage）
        3. 第二阶段：LLM 输出结构化结果（with_structured_output + parse_structured_output 兜底）
        """
        # 预先检索知识上下文（带权限过滤）
        context_payload = None
        if self._knowledge_agent:
            ctx = await self._knowledge_agent.global_search(user_query, top_k=10, min_score=0.5)
            logger.info(f"📚 知识检索完成: {ctx.summary()}")
            context_payload = ctx.to_llm_context(allowlist=self.allowlist)

        task_payload = {
            "analysis_result": analysis.model_dump(),
            "selected_component": selected_component,
        }

        messages = build_llm_messages(
            system_instructions=ARCHITECT_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="architect_agent",
            user_query=user_query,
            task_payload=task_payload,
            context_payload=context_payload,
            memory_context=memory_context,
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
                logger.info(f"🔧 ArchitectAgent 调用工具: {tc['name']}({tc['args']})")

            results = await asyncio.gather(
                *[self._execute_tool(tc["name"], tc["args"]) for tc in response.tool_calls]
            )

            for tc, result in zip(response.tool_calls, results, strict=True):
                messages.append(ToolMessage(content=result, tool_call_id=tc["id"]))

        # 第二阶段：结构化输出（with_structured_output 让 LLM 知道 schema）
        return await self._get_structured_output(messages, WorkflowOutput)

    async def _get_structured_output(
        self,
        messages: list,
        schema: type[WorkflowOutput],
    ) -> WorkflowOutput:
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
        """绑定查询工具到 LLM"""
        tool_registry = {
            "get_table_lineage": get_table_lineage,
            "list_component": list_component,
        }
        tools = [tool_registry[name] for name in self.allowlist if name in tool_registry]
        return self.llm.bind_tools(
            tools,
            tool_choice="auto",
        )

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用（通过 knowledge_agent 获取精确路径）"""
        try:
            if tool_name not in self.allowlist:
                return _tool_error(f"工具不在 allowlist 中: {tool_name}")

            # list_component 不需要指针，直接调用
            if tool_name == "list_component":
                return list_component.invoke(tool_args)

            if tool_name == "get_table_lineage":
                # 检查是否已提供精确参数
                catalog = tool_args.get("catalog")
                schema_name = tool_args.get("schema_name") or tool_args.get("schema")
                table = tool_args.get("table")
                direction = tool_args.get("direction", "both")

                # 如果只提供了 table_name，通过 knowledge_agent 查找精确路径
                if not (catalog and schema_name and table):
                    table_name = tool_args.get("table_name") or tool_args.get("table") or ""
                    if not table_name:
                        return _tool_error("缺少 table 参数")

                    if not self._knowledge_agent:
                        return _tool_error("无法查询表位置：knowledge_agent 未注入")

                    # 使用 global_search 查找表
                    ctx = await self._knowledge_agent.global_search(
                        table_name, top_k=5, min_score=0.6
                    )
                    if not ctx.tables:
                        return _tool_error("未找到相关表", table_name=table_name)

                    # 遍历所有匹配的表
                    results = []
                    for pointer in ctx.tables:
                        if "get_table_lineage" not in pointer.tools:
                            continue
                        logger.info(
                            f"📊 调用 get_table_lineage: catalog={pointer.catalog}, "
                            f"schema_name={pointer.schema_name}, table={pointer.table}"
                        )
                        result = await get_table_lineage.ainvoke(
                            {
                                "catalog": pointer.catalog,
                                "schema_name": pointer.schema_name,
                                "table": pointer.table,
                                "direction": direction,
                            }
                        )
                        results.append(result)

                    if not results:
                        return _tool_error("无可用指针授权此工具", table_name=table_name)

                    return json.dumps({"status": "success", "results": results}, ensure_ascii=False)

                # 已提供精确参数，直接调用
                logger.info(
                    f"📊 调用 get_table_lineage: catalog={catalog}, schema_name={schema_name}, table={table}"
                )
                return await get_table_lineage.ainvoke(
                    {
                        "catalog": catalog,
                        "schema_name": schema_name,
                        "table": table,
                        "direction": direction,
                    }
                )

            return _tool_error(f"未知工具: {tool_name}")
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return _tool_error(str(e))
