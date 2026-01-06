"""
ETL 多智能体编排器 v2

基于新架构：
- Blackboard: 唯一共享状态（Boss工作台）
- Boss: 老板（协调员工）
- Handover: 员工交接物存储
- AgentResult: Agent 统一返回类型
- SessionMemory: 按 Agent 隔离的短期记忆

设计原则：
- 状态清晰：Blackboard 只放老板关心的信息
- Agent 接收明确参数，返回 AgentResult
- Orchestrator 负责存储、协调和压缩触发
- 压缩时机：用户手动 /compress 或 Agent 上下文达到阈值
"""

from __future__ import annotations

import logging
import time
import uuid
from collections.abc import AsyncGenerator
from typing import Any, Literal

from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt

from src.infrastructure.llm.client import call_llm
from src.infrastructure.repository.checkpoint import Checkpoint
from src.modules.etl.agents import (
    AnalystAgent,
    ArchitectAgent,
    DeveloperAgent,
    KnowledgeAgent,
    ReviewerAgent,
)
from src.modules.etl.boss import EtlBoss
from src.modules.etl.context import Handover
from src.modules.etl.context.compress.agent_compressor import (
    estimate_context_tokens,
    maybe_compress,
    should_compress,
)
from src.modules.etl.context.compress.budget import (
    ContextBudget,
    get_default_budget,
    parse_compress,
)
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import AnalysisResult
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import Workflow
from src.modules.etl.state import AgentReport, Blackboard
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class EtlOrchestratorV2:
    """
    ETL 多智能体编排器 v2

    使用新架构：Blackboard + Boss + Handover + AgentResult + SessionMemory
    """

    def __init__(
        self,
        review_retry_threshold: int | None = None,
        context_budget: ContextBudget | None = None,
    ):
        self.review_retry_threshold = int(
            review_retry_threshold
            if review_retry_threshold is not None
            else settings.get("etl_review_retry_threshold", 3)
        )

        # Boss（内部使用 structured output）
        self.boss = EtlBoss()
        self.compress_llm = call_llm(temperature=0.0)  # 用于压缩的 LLM
        self.context_budget = context_budget or get_default_budget()

        # Agent 实例
        self.knowledge_agent = KnowledgeAgent()
        self.analyst_agent = AnalystAgent()
        self.architect_agent = ArchitectAgent()
        self.developer_agent = DeveloperAgent()
        self.reviewer_agent = ReviewerAgent()

        # Handover 存储（运行时交接物，不持久化）
        self._handover: dict[str, Handover] = {}

        self._graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建 LangGraph 图"""
        graph = StateGraph(Blackboard)

        # 添加节点
        graph.add_node("boss", self._boss_node)
        graph.add_node("human_in_the_loop", self._human_node)
        graph.add_node("knowledge_agent", self._knowledge_node)
        graph.add_node("analyst_agent", self._analyst_node)
        graph.add_node("architect_agent", self._architect_node)
        graph.add_node("developer_agent", self._developer_node)
        graph.add_node("reviewer_agent", self._reviewer_node)
        graph.add_node("finalize", self._finalize_node)

        # 设置入口
        graph.set_entry_point("boss")

        # 添加条件边
        graph.add_conditional_edges(
            "boss",
            self._route_from_boss,
            {
                "human_in_the_loop": "human_in_the_loop",
                "knowledge_agent": "knowledge_agent",
                "analyst_agent": "analyst_agent",
                "architect_agent": "architect_agent",
                "developer_agent": "developer_agent",
                "reviewer_agent": "reviewer_agent",
                "finalize": "finalize",
            },
        )

        # 所有 Agent 执行完后回到 Boss
        for node in [
            "human_in_the_loop",
            "knowledge_agent",
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "reviewer_agent",
        ]:
            graph.add_edge(node, "boss")

        # finalize 结束
        graph.add_edge("finalize", END)

        return graph

    def _get_handover(self, session_id: str) -> Handover:
        """获取或创建 Handover（运行时交接物存储）"""
        if session_id not in self._handover:
            self._handover[session_id] = Handover(session_id=session_id)
        return self._handover[session_id]

    async def _maybe_compress_agent(self, blackboard: Blackboard, agent_id: str) -> bool:
        """
        检查并执行 Agent 上下文压缩（如果需要）

        触发条件：
        - 用户手动 /compress
        - 该 Agent 上下文 token 数 >= 阈值

        返回：
        - True: 执行了压缩（成功或失败但保留了数据）
        - False: 未触发压缩
        """
        memory = blackboard.ensure_memory()
        user_query = blackboard.task or ""

        # 检查是否用户手动触发
        manual_trigger = parse_compress(user_query) is not None

        # 获取记忆上下文用于估算
        memory_context = memory.get_agent_context(agent_id)

        # 估算 token 数（使用简化的系统指令估算）
        estimated = estimate_context_tokens(
            system_instructions="[Agent System Prompt]",
            context_payload=None,
            memory_context=memory_context,
            user_query=user_query,
        )

        if not should_compress(
            estimated_tokens=estimated,
            budget=self.context_budget,
            manual_trigger=manual_trigger,
        ):
            return False

        # 执行压缩
        logger.info(f"Agent {agent_id} 触发压缩: tokens={estimated}, manual={manual_trigger}")

        result = await maybe_compress(
            llm=self.compress_llm,
            memory=memory,
            agent_id=agent_id,
            system_instructions="[Agent System Prompt]",
            context_payload=None,
            user_query=user_query,
            budget=self.context_budget,
            manual_trigger=manual_trigger,
        )

        # 处理压缩结果
        if result.status == "failed":
            logger.warning(
                f"Agent {agent_id} 压缩失败: {result.error}, "
                f"保留原始数据: {result.keep_recent_turns}"
            )

        return result.status != "skipped"

    def _route_from_boss(self, blackboard: Blackboard) -> str:
        """从 Boss 决策路由到下一个节点"""
        # _boss_node 已经通过 Command(update={"current_agent": xxx}) 设置了 current_agent
        # 这里直接读取即可
        next_node = blackboard.current_agent or "finalize"

        valid_nodes = {
            "human_in_the_loop",
            "knowledge_agent",
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "reviewer_agent",
            "finalize",
        }
        return next_node if next_node in valid_nodes else "finalize"

    async def _boss_node(self, blackboard: Blackboard) -> Command:
        """Boss 节点：决策下一步（异步版本，调用 LLM）"""
        decision = await self.boss.decide(blackboard)
        return Command(update=decision)

    async def _human_node(self, blackboard: Blackboard) -> Command:
        """人机交互节点"""
        pending = [r for r in blackboard.pending_requests if r.kind == "human"]
        if not pending:
            return Command(update={})

        req = pending[0]
        payload = req.payload or {}

        message = payload.get("message", "请输入")
        questions = payload.get("questions", [])
        if isinstance(questions, list) and questions:
            question_lines = "\n".join([f"- {str(q)}" for q in questions if str(q).strip()])
            if question_lines.strip():
                message = f"{message}\n\n需要确认：\n{question_lines}"

        user_input = interrupt(
            {
                "type": payload.get("type", "question"),
                "message": message,
                "options": payload.get("options", []),
            }
        )

        remaining = [r for r in blackboard.pending_requests if r.request_id != req.request_id]
        results = dict(blackboard.request_results)
        results[req.request_id] = {
            "status": "completed",
            "response": user_input,
            "completed_at_ms": int(time.time() * 1000),
        }

        update: dict[str, Any] = {
            "pending_requests": remaining,
            "request_results": results,
        }

        # 可选：human 输入完成后，自动创建委派请求（用于“超阈值回炉暂停后由用户指定下一步”）
        post_action = payload.get("post_action")
        if post_action == "delegate" and isinstance(user_input, str):
            target_agent = user_input.strip()
            if target_agent in {
                "knowledge_agent",
                "analyst_agent",
                "architect_agent",
                "developer_agent",
                "reviewer_agent",
            }:
                remaining.append(
                    BlackboardRequest(
                        request_id=f"req_{uuid.uuid4().hex}",
                        kind="delegate",
                        created_by="human_in_the_loop",
                        target_agent=target_agent,
                        resume_to="boss",
                        payload={
                            "type": payload.get("delegate_reason", "human_selected_next_step"),
                            "message": payload.get(
                                "delegate_message",
                                "用户已指定下一步处理人，请继续。",
                            ),
                        },
                    )
                )
                update["pending_requests"] = remaining

        reset_fields = payload.get("reset_fields", [])
        if isinstance(reset_fields, list):
            for field_name in reset_fields:
                if field_name in {
                    "design_review_iteration_count",
                    "development_review_iteration_count",
                }:
                    update[field_name] = 0

        return Command(update=update)

    async def _knowledge_node(self, blackboard: Blackboard) -> Command:
        """知识检索节点"""
        handover = self._get_handover(blackboard.session_id)

        # 从 pending_requests 获取额外提示
        additional_hints = []
        for req in blackboard.pending_requests:
            if req.kind == "delegate" and req.target_agent == "knowledge_agent":
                payload = req.payload or {}
                unknown_tables = payload.get("unknown_tables", [])
                if isinstance(unknown_tables, list):
                    additional_hints.extend(unknown_tables)

        result = await self.knowledge_agent.run(
            user_query=blackboard.task or "",
            additional_hints=additional_hints if additional_hints else None,
        )

        return self._handle_agent_result(
            blackboard=blackboard,
            handover=handover,
            agent_id="knowledge_agent",
            result=result,
        )

    async def _analyst_node(self, blackboard: Blackboard) -> Command:
        """需求分析师节点"""
        agent_id = "analyst_agent"
        handover = self._get_handover(blackboard.session_id)

        # 检查是否需要压缩（手动触发或达到阈值）
        await self._maybe_compress_agent(blackboard, agent_id)

        # 记录用户输入到该 Agent 的对话历史
        if blackboard.task:
            blackboard.add_agent_turn(agent_id, "user", blackboard.task)

        result = await self.analyst_agent.run(
            user_query=blackboard.task or "",
            knowledge_agent=self.knowledge_agent,
        )

        # 记录 Agent 响应到对话历史
        if result.summary:
            blackboard.add_agent_turn(agent_id, "assistant", result.summary)

        return self._handle_agent_result(
            blackboard=blackboard,
            handover=handover,
            agent_id=agent_id,
            result=result,
        )

    async def _architect_node(self, blackboard: Blackboard) -> Command:
        """数据架构师节点"""
        agent_id = "architect_agent"
        handover = self._get_handover(blackboard.session_id)

        # 获取需求分析结果
        analysis_result = handover.get_deliverable("analysis")
        if not analysis_result:
            return self._create_delegation_request(
                blackboard=blackboard,
                from_agent=agent_id,
                to_agent="analyst_agent",
                reason="need_analysis_result",
                message="架构设计需要需求分析结果，已委派需求分析师先完成需求收敛。",
            )

        if isinstance(analysis_result, dict):
            analysis_result = AnalysisResult(**analysis_result)

        # 检查是否已选择组件
        selected_component = self._get_selected_component(blackboard)
        if not selected_component:
            # 获取组件列表并请求用户选择
            components = await self.architect_agent.get_components()
            if not components:
                return self._create_error_result(blackboard, agent_id, "未找到可用组件")

            options = []
            for comp in components:
                code = comp.get("code", comp.get("component_code", ""))
                name = comp.get("name", comp.get("component_name", ""))
                comp_type = comp.get("type", comp.get("component_type", ""))
                options.append(
                    {
                        "value": code,
                        "label": f"{code}: {name}",
                        "type": comp_type,
                    }
                )

            return self._create_human_request(
                blackboard=blackboard,
                agent_id=agent_id,
                request_type="component_selection",
                message="请选择要使用的技术组件：",
                options=options,
                writeback_key="selected_component",
            )

        # 获取组件 ID
        selected_component_id = None
        components = await self.architect_agent.get_components()
        for comp in components:
            if comp.get("code") == selected_component:
                selected_component_id = comp.get("id")
                break

        # 检查是否需要压缩（手动触发或达到阈值）
        await self._maybe_compress_agent(blackboard, agent_id)

        # 记录用户输入到该 Agent 的对话历史
        if blackboard.task:
            blackboard.add_agent_turn(agent_id, "user", blackboard.task)

        result = await self.architect_agent.run(
            user_query=blackboard.task or "",
            analysis_result=analysis_result,
            selected_component=selected_component,
            selected_component_id=selected_component_id,
            knowledge_agent=self.knowledge_agent,
        )

        # 记录 Agent 响应到对话历史
        if result.summary:
            blackboard.add_agent_turn(agent_id, "assistant", result.summary)

        return self._handle_agent_result(
            blackboard=blackboard,
            handover=handover,
            agent_id=agent_id,
            result=result,
        )

    async def _developer_node(self, blackboard: Blackboard) -> Command:
        """数据开发节点"""
        agent_id = "developer_agent"
        handover = self._get_handover(blackboard.session_id)

        # 获取工作流
        workflow = handover.get_deliverable("plan")
        if not workflow:
            return self._create_delegation_request(
                blackboard=blackboard,
                from_agent=agent_id,
                to_agent="architect_agent",
                reason="need_architecture_plan",
                message="SQL 生成需要架构方案，已委派数据架构师先完成工作流设计。",
            )

        if isinstance(workflow, dict):
            workflow = Workflow(**workflow)

        # 获取 review 反馈（如果有）
        review_feedback = handover.get_deliverable("review_development")
        if isinstance(review_feedback, dict):
            review_feedback = ReviewResult(**review_feedback)

        # 检查是否需要压缩（手动触发或达到阈值）
        await self._maybe_compress_agent(blackboard, agent_id)

        # 记录用户输入到该 Agent 的对话历史
        if blackboard.task:
            blackboard.add_agent_turn(agent_id, "user", blackboard.task)

        result = await self.developer_agent.run(
            user_query=blackboard.task or "",
            workflow=workflow,
            review_feedback=review_feedback if isinstance(review_feedback, ReviewResult) else None,
            knowledge_agent=self.knowledge_agent,
        )

        # 记录 Agent 响应到对话历史
        if result.summary:
            blackboard.add_agent_turn(agent_id, "assistant", result.summary)

        return self._handle_agent_result(
            blackboard=blackboard,
            handover=handover,
            agent_id=agent_id,
            result=result,
        )

    async def _reviewer_node(self, blackboard: Blackboard) -> Command:
        """Review 节点"""
        agent_id = "reviewer_agent"
        handover = self._get_handover(blackboard.session_id)

        # 获取需求分析结果
        analysis_result = handover.get_deliverable("analysis")
        if not analysis_result:
            return self._create_delegation_request(
                blackboard=blackboard,
                from_agent=agent_id,
                to_agent="analyst_agent",
                reason="need_analysis_result",
                message="Review 需要需求分析结果，已委派需求分析师先完成需求收敛。",
            )

        if isinstance(analysis_result, dict):
            analysis_result = AnalysisResult(**analysis_result)

        # 获取工作流
        workflow = handover.get_deliverable("plan") or handover.get_deliverable("workflow")
        if not workflow:
            return self._create_delegation_request(
                blackboard=blackboard,
                from_agent=agent_id,
                to_agent="architect_agent",
                reason="need_architecture_plan",
                message="Review 需要架构方案，已委派数据架构师先完成工作流设计。",
            )

        if isinstance(workflow, dict):
            workflow = Workflow(**workflow)

        review_stage: Literal["design", "development"] = (
            "design" if not blackboard.design_review_passed else "development"
        )

        # 确定性前置校验：开发阶段必须有 SQL
        if review_stage == "development":
            missing_sql_jobs = self._check_missing_sql(workflow)
            if missing_sql_jobs:
                return self._create_delegation_request(
                    blackboard=blackboard,
                    from_agent=agent_id,
                    to_agent="developer_agent",
                    reason="missing_sql",
                    message=f"存在未生成 SQL 的 Job: {', '.join(missing_sql_jobs)}，请先生成完整 SQL。",
                )

        # 检查是否需要压缩（手动触发或达到阈值）
        await self._maybe_compress_agent(blackboard, agent_id)

        # 记录用户输入到该 Agent 的对话历史
        if blackboard.task:
            blackboard.add_agent_turn(agent_id, "user", blackboard.task)

        # 调用 ReviewerAgent 执行 review
        result = await self.reviewer_agent.run(
            user_query=blackboard.task or "",
            analysis_result=analysis_result,
            workflow=workflow,
            review_stage=review_stage,
        )

        # 记录 Agent 响应到对话历史
        if result.summary:
            blackboard.add_agent_turn(agent_id, "assistant", result.summary)

        # 处理技术故障（Agent 执行失败）
        if result.status == "failed":
            return self._handle_agent_result(
                blackboard=blackboard,
                handover=handover,
                agent_id=agent_id,
                result=result,
            )

        # 获取 review 结果
        review_result = result.deliverable
        if not isinstance(review_result, ReviewResult):
            return self._create_error_result(
                blackboard, agent_id, "ReviewerAgent 未返回有效的 ReviewResult"
            )

        # 存储测试结果
        cmd = self._handle_agent_result(
            blackboard=blackboard,
            handover=handover,
            agent_id=agent_id,
            result=result,
            create_followup_requests=False,  # 打回逻辑由本方法处理
        )
        update = dict(cmd.update or {})

        # 阶段迭代计数
        stage_counter_field = (
            "design_review_iteration_count"
            if review_stage == "design"
            else "development_review_iteration_count"
        )

        # Review 通过：标记阶段通过，重置计数
        if review_result.passed:
            if review_stage == "design":
                update["design_review_passed"] = True
                update["design_review_iteration_count"] = 0
            else:
                update["development_review_passed"] = True
                update["development_review_iteration_count"] = 0
                update["is_completed"] = True
                update["deliverable"] = workflow
            return Command(update=update)

        # Review 未通过：检查迭代次数，决定打回或暂停
        current_count = getattr(blackboard, stage_counter_field)
        next_count = current_count + 1
        update[stage_counter_field] = next_count

        # 确定打回目标
        target_agent = "architect_agent" if review_stage == "design" else "developer_agent"
        stage_cn = "设计阶段" if review_stage == "design" else "开发阶段"

        # 超过阈值：暂停，请求用户介入
        if next_count >= int(blackboard.review_retry_threshold):
            options = [
                {
                    "value": "analyst_agent",
                    "label": "analyst_agent：需求/口径澄清",
                    "type": "agent",
                },
                {
                    "value": "architect_agent",
                    "label": "architect_agent：架构/依赖/写入设计",
                    "type": "agent",
                },
                {
                    "value": "developer_agent",
                    "label": "developer_agent：SQL/字段映射/性能修复",
                    "type": "agent",
                },
            ]

            message = (
                f"{stage_cn} review 已连续 {next_count} 次未通过，已暂停自动回炉。\n"
                "请选择下一步由谁继续处理（将自动委派并继续执行）。"
            )

            pending = list(update.get("pending_requests") or blackboard.pending_requests or [])
            pending.append(
                BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="human",
                    created_by="reviewer_agent",
                    resume_to="boss",
                    payload={
                        "type": "review_threshold_exceeded",
                        "message": message,
                        "options": options,
                        "post_action": "delegate",
                        "delegate_reason": "review_threshold_exceeded",
                        "delegate_message": f"{stage_cn} review 超阈值后由用户指定下一步处理人。",
                        "reset_fields": [stage_counter_field],
                    },
                )
            )
            update["pending_requests"] = pending
            return Command(update=update)

        # 未超过阈值：直接打回给对应 Agent
        pending = list(update.get("pending_requests") or blackboard.pending_requests or [])
        pending.append(
            BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="delegate",
                created_by="reviewer_agent",
                target_agent=target_agent,
                resume_to="reviewer_agent",
                payload={
                    "type": "review_failed",
                    "message": f"{stage_cn} review 未通过（第 {next_count} 次），请根据测试报告修复问题。",
                    "issues": review_result.issues,
                    "warnings": review_result.warnings,
                },
            )
        )
        update["pending_requests"] = pending
        return Command(update=update)

    @staticmethod
    def _check_missing_sql(workflow: Workflow) -> list[str]:
        """检查工作流中缺少 SQL 的 Job"""
        missing: list[str] = []
        for job in workflow.jobs:
            sql = job.config.get("content") if job.config else None
            if not (isinstance(sql, str) and sql.strip()):
                missing.append(job.id)
        return missing

    async def _finalize_node(self, blackboard: Blackboard) -> Command:
        """完成节点：清理运行时交接物，标记任务完成"""
        # 任务完成，清理交接物（遵循"用完即弃"原则）
        self._handover.pop(blackboard.session_id, None)
        return Command(update={"is_completed": True})

    def _handle_agent_result(
        self,
        *,
        blackboard: Blackboard,
        handover: Handover,
        agent_id: str,
        result: AgentResult,
        create_followup_requests: bool = True,
    ) -> Command:
        """处理 Agent 结果，更新 Blackboard 和 Handover"""
        now_ms = int(time.time() * 1000)

        # 如果当前 Agent 是“被委派者”，则在其执行完成后弹出对应的 delegate 请求，避免重复执行。
        blackboard, _popped = self.boss.pop_completed_request(blackboard, agent_id)

        # 存储交付物到 Handover（运行时交接，不持久化）
        if result.deliverable and result.deliverable_type:
            handover.store_deliverable(result.deliverable_type, result.deliverable)

        # 更新短期记忆中的 Agent 状态（通过 Checkpointer 持久化）
        blackboard.update_agent_status(
            agent_id=agent_id,
            status=result.status,
            deliverable_type=result.deliverable_type,
            summary=result.summary or "",
        )

        # 创建 AgentReport
        report = AgentReport(
            status=result.status,
            summary=result.summary,
            deliverable_ref=(
                f"{result.deliverable_type}:{uuid.uuid4().hex[:8]}"
                if result.deliverable_type
                else None
            ),
            updated_at_ms=now_ms,
        )

        reports = dict(blackboard.reports)
        reports[agent_id] = report

        update: dict[str, Any] = {
            "reports": reports,
            # pop_completed_request 可能更新 pending_requests/request_results
            "pending_requests": list(blackboard.pending_requests),
            "request_results": dict(blackboard.request_results),
        }

        if create_followup_requests:
            # 处理需要澄清的情况
            if result.status == "needs_clarification" and result.clarification:
                req = BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="human",
                    created_by=agent_id,
                    resume_to="boss",
                    payload={
                        "type": "clarification",
                        "message": result.clarification.message,
                        "questions": result.clarification.questions,
                        "options": result.clarification.options,
                        "guidance": result.clarification.guidance,
                    },
                )
                pending = list(update.get("pending_requests") or [])
                pending.append(req)
                update["pending_requests"] = pending

            # 处理需要委派的情况
            if result.status == "needs_delegation" and result.delegation:
                req = BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="delegate",
                    created_by=agent_id,
                    target_agent=result.delegation.target_agent,
                    resume_to=agent_id,
                    payload={
                        "type": result.delegation.reason,
                        **result.delegation.payload,
                    },
                )
                pending = list(update.get("pending_requests") or [])
                pending.append(req)
                update["pending_requests"] = pending

        # 处理失败情况
        if result.status == "failed":
            update["error"] = result.error

        return Command(update=update)

    def _get_selected_component(self, blackboard: Blackboard) -> str | None:
        """从 request_results 获取用户选择的组件"""
        for _req_id, result in blackboard.request_results.items():
            if isinstance(result, dict):
                response = result.get("response")
                if isinstance(response, str) and response.strip():
                    return response.strip()
                if isinstance(response, dict):
                    comp = response.get("component") or response.get("value")
                    if isinstance(comp, str) and comp.strip():
                        return comp.strip()
        return None

    def _create_delegation_request(
        self,
        *,
        blackboard: Blackboard,
        from_agent: str,
        to_agent: str,
        reason: str,
        message: str,
    ) -> Command:
        """创建委派请求"""
        req = BlackboardRequest(
            request_id=f"req_{uuid.uuid4().hex}",
            kind="delegate",
            created_by=from_agent,
            target_agent=to_agent,
            resume_to=from_agent,
            payload={
                "type": reason,
                "message": message,
            },
        )
        pending = list(blackboard.pending_requests)
        pending.append(req)

        report = AgentReport(
            status="waiting",
            summary=message,
            updated_at_ms=int(time.time() * 1000),
        )
        reports = dict(blackboard.reports)
        reports[from_agent] = report

        return Command(
            update={
                "reports": reports,
                "pending_requests": pending,
            }
        )

    def _create_human_request(
        self,
        *,
        blackboard: Blackboard,
        agent_id: str,
        request_type: str,
        message: str,
        options: list[dict],
        writeback_key: str,
    ) -> Command:
        """创建人机交互请求"""
        req = BlackboardRequest(
            request_id=f"req_{uuid.uuid4().hex}",
            kind="human",
            created_by=agent_id,
            resume_to="boss",
            payload={
                "type": request_type,
                "message": message,
                "options": options,
                "writeback_key": writeback_key,
            },
        )
        pending = list(blackboard.pending_requests)
        pending.append(req)

        report = AgentReport(
            status="waiting",
            summary=message,
            updated_at_ms=int(time.time() * 1000),
        )
        reports = dict(blackboard.reports)
        reports[agent_id] = report

        return Command(
            update={
                "reports": reports,
                "pending_requests": pending,
            }
        )

    def _create_error_result(
        self,
        blackboard: Blackboard,
        agent_id: str,
        error: str,
    ) -> Command:
        """创建错误结果"""
        report = AgentReport(
            status="failed",
            summary=error,
            updated_at_ms=int(time.time() * 1000),
        )
        reports = dict(blackboard.reports)
        reports[agent_id] = report

        return Command(
            update={
                "reports": reports,
                "error": error,
            }
        )

    async def compile(self):
        """编译图（使用 Checkpoint 获取 checkpointer）"""
        saver = await Checkpoint._ensure_saver()
        return self._graph.compile(checkpointer=saver)

    def _get_thread_id(self, session_id: str, user_id: str) -> str:
        """生成 thread_id"""
        return f"etl:user:{user_id}:session:{session_id}"

    async def stream(
        self,
        *,
        user_input: str,
        session_id: str,
        user_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        运行编排器（SSE 流）

        Args:
            user_input: 用户输入
            session_id: 会话ID
            user_id: 用户ID
            resume_value: 恢复值（用于 interrupt 后继续）

        Yields:
            SSE 事件
        """
        thread_id = self._get_thread_id(session_id, user_id)
        config = {"configurable": {"thread_id": thread_id}}
        app = await self.compile()

        if resume_value is not None:
            async for event in app.astream(Command(resume=resume_value), config):
                yield self._format_event(event, session_id)
        else:
            blackboard = Blackboard(
                session_id=session_id,
                user_id=user_id,
                task=user_input,
                review_retry_threshold=self.review_retry_threshold,
            )
            async for event in app.astream(blackboard, config):
                yield self._format_event(event, session_id)

        yield {"event": "complete", "data": {"session_id": session_id}}

    def _format_event(self, event: dict[str, Any], session_id: str) -> dict[str, Any]:
        """格式化事件"""
        return {
            "event": "state_update",
            "data": event,
            "session_id": session_id,
        }

    async def clear_session(self, session_id: str, user_id: str) -> None:
        """清除会话"""
        thread_id = self._get_thread_id(session_id, user_id)
        # 清除 checkpoint
        await Checkpoint.delete_thread(thread_id)
        # 清除 handover
        self._handover.pop(session_id, None)
        logger.info(f"Session cleared: {thread_id}")
