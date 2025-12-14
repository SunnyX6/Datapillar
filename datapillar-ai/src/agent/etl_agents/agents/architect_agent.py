"""
Architect Agent（数据架构师）

职责：将 AnalystAgent 的 Step 映射为 Job
- 一个 Step → 一个 Job → 一个前端节点
"""

import json
import logging
from typing import List, Dict, Any

from langchain_core.messages import AIMessage
from langgraph.types import Command

from src.agent.etl_agents.schemas.state import AgentState
from src.agent.etl_agents.schemas.plan import Workflow, Job
from src.agent.etl_agents.schemas.requirement import AnalysisResult, Step
from src.agent.etl_agents.schemas.kg_context import KnowledgeContext

logger = logging.getLogger(__name__)


class ArchitectAgent:
    """
    数据架构师

    职责：
    1. 将 AnalysisResult 中的 Step 映射为 Job
    2. 验证组件是否存在（从 KnowledgeContext 获取）
    3. 构建 Workflow
    """

    async def __call__(self, state: AgentState) -> Command:
        """执行架构设计"""
        analysis_result = state.analysis_result
        knowledge_context = state.knowledge_context

        if not analysis_result:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少需求分析结果，无法设计架构")],
                    "current_agent": "architect_agent",
                    "error": "缺少需求分析结果",
                }
            )

        logger.info("🏗️ ArchitectAgent 开始设计架构")

        # 从 knowledge_context 获取可用组件
        valid_component_ids = set()
        if knowledge_context:
            if isinstance(knowledge_context, dict):
                context = KnowledgeContext(**knowledge_context)
            else:
                context = knowledge_context
            valid_component_ids = set(context.get_component_ids())

        if not valid_component_ids:
            return Command(
                update={
                    "messages": [AIMessage(content="未找到可用组件，KnowledgeAgent 可能未调用 list_component")],
                    "current_agent": "architect_agent",
                    "error": "未找到可用组件",
                }
            )

        logger.info(f"📦 可用组件: {valid_component_ids}")

        try:
            if isinstance(analysis_result, dict):
                analysis = AnalysisResult(**analysis_result)
            else:
                analysis = analysis_result

            workflow_plan = self._build_workflow_plan(analysis, valid_component_ids)

            dag_errors = workflow_plan.validate_dag()
            if dag_errors:
                workflow_plan.risks.extend(dag_errors)

            logger.info(
                f"✅ ArchitectAgent 完成设计: {workflow_plan.name}, "
                f"节点数={len(workflow_plan.jobs)}"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"架构设计完成: {workflow_plan.name}")],
                    "architecture_plan": workflow_plan.model_dump(),
                    "current_agent": "architect_agent",
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

    def _build_workflow_plan(
        self,
        analysis: AnalysisResult,
        valid_component_ids: set
    ) -> Workflow:
        """将 AnalysisResult 转换为 Workflow"""
        nodes: List[Job] = []

        # Step ID → Job ID 映射
        step_to_node_id: Dict[str, str] = {}

        for step in analysis.steps:
            node_id = f"node_{step.step_id}"
            step_to_node_id[step.step_id] = node_id

            # 验证组件
            component_id = step.suggested_component
            if component_id not in valid_component_ids:
                logger.warning(f"组件 {component_id} 不存在，降级为 hive")
                component_id = "hive" if "hive" in valid_component_ids else list(valid_component_ids)[0]

            # 获取 Step 的外部输入表
            input_tables = step.get_all_input_tables()

            # 获取依赖的上游节点
            depends_on = [step_to_node_id[dep_id] for dep_id in step.depends if dep_id in step_to_node_id]

            # 构建 Job
            node = Job(
                id=node_id,
                name=step.step_name,
                description=step.description,
                component_id=component_id,
                depends_on=depends_on,
                input_tables=input_tables,
                output_table=step.output_table,
                config={
                    "stages": [stage.model_dump() for stage in step.get_ordered_stages()]
                },
                config_generated=False,
                config_validated=False,
            )
            nodes.append(node)

        # 确定数据分层
        layers = set()
        for step in analysis.steps:
            for stage in step.stages:
                output = stage.output_table
                if output.startswith("ods."):
                    layers.add("ODS")
                elif output.startswith("dwd."):
                    layers.add("DWD")
                elif output.startswith("dws."):
                    layers.add("DWS")
                elif output.startswith("ads."):
                    layers.add("ADS")

        return Workflow(
            name=analysis.summary[:50] if analysis.summary else "etl_workflow",
            description=analysis.summary,
            schedule=None,
            env="dev",
            nodes=nodes,
            layers=list(layers),
            risks=[],
            decision_points=[],
            confidence=analysis.confidence,
        )
