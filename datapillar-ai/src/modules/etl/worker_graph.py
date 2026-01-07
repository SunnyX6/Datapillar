"""
员工子图（Worker Graph）

包含所有员工 Agent 的执行逻辑，作为独立子图被 Boss 调用。

设计原则：
- 子图有自己的状态（WorkerState），与父图通过 Blackboard 共享
- 员工执行完成后，汇报写入 Blackboard.reports（Boss 可见）
- 交付物存入 Handover（私有，通过 deliverable_ref 引用）
- 子图内部处理员工间的依赖和打回逻辑
"""

from __future__ import annotations

import json
import logging
import time
import uuid
from typing import Literal

from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt
from pydantic import BaseModel, Field

from src.modules.etl.agents import (
    AnalystAgent,
    ArchitectAgent,
    DeveloperAgent,
    KnowledgeAgent,
    ReviewerAgent,
)
from src.modules.etl.context import Handover
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import AnalysisResult
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import Workflow
from src.modules.etl.state import AgentReport, Blackboard

logger = logging.getLogger(__name__)


class WorkerState(BaseModel):
    """
    员工子图状态

    共享 key（与父图同步）：
    - blackboard: Boss 的工作台，包含 reports 等共享信息

    私有 key（子图内部使用）：
    - target_agent: 要执行的目标员工
    - handover: 运行时交接物存储
    """

    # 共享 key
    blackboard: Blackboard = Field(default_factory=Blackboard)

    # 私有 key
    target_agent: str | None = Field(default=None, description="目标员工 ID")
    handover: Handover | None = Field(default=None, description="运行时交接物存储")

    model_config = {"arbitrary_types_allowed": True}


# 有效的员工 ID
WORKER_AGENT_IDS: set[str] = {
    "analyst_agent",
    "architect_agent",
    "developer_agent",
    "reviewer_agent",
}


class WorkerGraph:
    """
    员工子图

    包含所有员工 Agent 的执行逻辑，作为独立子图被 Boss 调用。
    """

    def __init__(
        self,
        review_retry_threshold: int = 3,
    ):
        self.review_retry_threshold = review_retry_threshold

        # Agent 实例
        self.knowledge_agent = KnowledgeAgent()
        self.analyst_agent = AnalystAgent()
        self.architect_agent = ArchitectAgent()
        self.developer_agent = DeveloperAgent()
        self.reviewer_agent = ReviewerAgent()

        # 构建图
        self._graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建员工子图"""
        graph = StateGraph(WorkerState)

        # 添加员工节点
        graph.add_node("analyst_agent", self._analyst_node)
        graph.add_node("architect_agent", self._architect_node)
        graph.add_node("developer_agent", self._developer_node)
        graph.add_node("reviewer_agent", self._reviewer_node)
        graph.add_node("human_in_the_loop", self._human_node)

        # 设置入口：根据 target_agent 路由
        graph.set_conditional_entry_point(
            self._route_entry,
            {
                "analyst_agent": "analyst_agent",
                "architect_agent": "architect_agent",
                "developer_agent": "developer_agent",
                "reviewer_agent": "reviewer_agent",
                "human_in_the_loop": "human_in_the_loop",
                "end": END,
            },
        )

        # 员工执行完后，根据状态决定下一步
        for agent_id in WORKER_AGENT_IDS:
            graph.add_conditional_edges(
                agent_id,
                self._route_after_worker,
                {
                    "analyst_agent": "analyst_agent",
                    "architect_agent": "architect_agent",
                    "developer_agent": "developer_agent",
                    "reviewer_agent": "reviewer_agent",
                    "human_in_the_loop": "human_in_the_loop",
                    "end": END,
                },
            )

        # human_in_the_loop 后根据状态决定下一步
        graph.add_conditional_edges(
            "human_in_the_loop",
            self._route_after_human,
            {
                "analyst_agent": "analyst_agent",
                "architect_agent": "architect_agent",
                "developer_agent": "developer_agent",
                "reviewer_agent": "reviewer_agent",
                "end": END,
            },
        )

        return graph

    def compile(self):
        """编译子图"""
        return self._graph.compile()

    def _route_entry(self, state: WorkerState) -> str:
        """入口路由：根据 target_agent 决定从哪个员工开始"""
        target = state.target_agent
        if target in WORKER_AGENT_IDS:
            return target
        if target == "human_in_the_loop":
            return "human_in_the_loop"
        return "end"

    def _route_after_worker(self, state: WorkerState) -> str:
        """员工执行完后的路由"""
        blackboard = state.blackboard

        # 检查是否有 human 请求
        if blackboard.has_human_request():
            return "human_in_the_loop"

        # 检查是否有 delegate 请求
        pending = blackboard.pending_requests
        if pending:
            req = pending[0]
            if req.kind == "delegate" and req.target_agent in WORKER_AGENT_IDS:
                return req.target_agent

        # 基于状态的确定性推进
        next_agent = self._next_by_progress(blackboard)
        if next_agent:
            return next_agent

        # 默认结束，返回给 Boss 决策
        return "end"

    def _route_after_human(self, state: WorkerState) -> str:
        """human_in_the_loop 后的路由"""
        blackboard = state.blackboard

        # 检查是否有 delegate 请求
        pending = blackboard.pending_requests
        if pending:
            req = pending[0]
            if req.kind == "delegate" and req.target_agent in WORKER_AGENT_IDS:
                return req.target_agent

        # 默认结束
        return "end"

    def _next_by_progress(self, blackboard: Blackboard) -> str | None:
        """基于黑板状态的确定性路由"""
        if blackboard.is_completed:
            return None

        reports = blackboard.reports or {}

        def is_completed(agent_id: str) -> bool:
            report = reports.get(agent_id)
            return bool(report and report.status == "completed")

        analyst_done = is_completed("analyst_agent")
        architect_done = is_completed("architect_agent")
        developer_done = is_completed("developer_agent")
        design_review_passed = bool(blackboard.design_review_passed)
        development_review_passed = bool(blackboard.development_review_passed)

        # 按依赖顺序推进
        if analyst_done and not architect_done:
            return "architect_agent"

        if analyst_done and architect_done and not design_review_passed:
            return "reviewer_agent"

        if analyst_done and architect_done and design_review_passed and not developer_done:
            return "developer_agent"

        if (
            analyst_done
            and architect_done
            and design_review_passed
            and developer_done
            and not development_review_passed
        ):
            return "reviewer_agent"

        return None

    def _ensure_handover(self, state: WorkerState) -> Handover:
        """确保 Handover 已初始化"""
        if state.handover is None:
            state.handover = Handover(session_id=state.blackboard.session_id)
        return state.handover

    async def _analyst_node(self, state: WorkerState) -> Command:
        """需求分析师节点"""
        agent_id = "analyst_agent"
        blackboard = state.blackboard

        user_query = blackboard.task or ""

        # 记录用户输入到对话历史
        if user_query:
            blackboard.add_agent_turn(agent_id, "user", user_query)

        # 获取对话历史上下文（支持多轮对话）
        memory_context = blackboard.get_agent_context(agent_id)

        result = await self.analyst_agent.run(
            user_query=user_query,
            knowledge_agent=self.knowledge_agent,
            memory_context=memory_context,
        )

        # 记录响应到对话历史
        if result.summary:
            response_json = json.dumps(
                {"status": result.status, "summary": result.summary}, ensure_ascii=False
            )
            blackboard.add_agent_turn(agent_id, "assistant", response_json)

        # 如果需要澄清，直接 interrupt 等待用户输入
        if result.status == "needs_clarification" and result.clarification:
            logger.info(f"⏸️ {agent_id} 需要用户澄清，调用 interrupt")
            user_input = interrupt(
                {
                    "type": "clarification",
                    "agent_id": agent_id,
                    "message": result.clarification.message,
                    "questions": result.clarification.questions,
                    "options": result.clarification.options,
                }
            )
            # 用户回复后，记录并再执行一次
            blackboard.add_agent_turn(agent_id, "user", user_input)
            memory_context = blackboard.get_agent_context(agent_id)
            result = await self.analyst_agent.run(
                user_query=user_input,
                knowledge_agent=self.knowledge_agent,
                memory_context=memory_context,
            )
            if result.summary:
                response_json = json.dumps(
                    {"status": result.status, "summary": result.summary}, ensure_ascii=False
                )
                blackboard.add_agent_turn(agent_id, "assistant", response_json)

        return self._handle_agent_result(
            state=state,
            agent_id=agent_id,
            result=result,
        )

    async def _architect_node(self, state: WorkerState) -> Command:
        """数据架构师节点"""
        agent_id = "architect_agent"
        blackboard = state.blackboard
        handover = self._ensure_handover(state)

        # 获取需求分析结果
        analysis_result = handover.get_deliverable("analysis")
        if not analysis_result:
            return self._create_delegation_request(
                state=state,
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
                return self._create_error_result(state, agent_id, "未找到可用组件")

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
                state=state,
                agent_id=agent_id,
                request_type="component_selection",
                message="请选择要使用的技术组件：",
                options=options,
            )

        # 获取组件 ID
        selected_component_id = None
        components = await self.architect_agent.get_components()
        for comp in components:
            if comp.get("code") == selected_component:
                selected_component_id = comp.get("id")
                break

        user_query = blackboard.task or ""

        # 记录用户输入到对话历史
        if user_query:
            blackboard.add_agent_turn(agent_id, "user", user_query)

        # 获取对话历史上下文（支持多轮对话）
        memory_context = blackboard.get_agent_context(agent_id)

        result = await self.architect_agent.run(
            user_query=user_query,
            analysis_result=analysis_result,
            selected_component=selected_component,
            selected_component_id=selected_component_id,
            knowledge_agent=self.knowledge_agent,
            memory_context=memory_context,
        )

        # 记录响应到对话历史
        if result.summary:
            response_json = json.dumps(
                {"status": result.status, "summary": result.summary}, ensure_ascii=False
            )
            blackboard.add_agent_turn(agent_id, "assistant", response_json)

        # 如果需要澄清，直接 interrupt 等待用户输入
        if result.status == "needs_clarification" and result.clarification:
            logger.info(f"⏸️ {agent_id} 需要用户澄清，调用 interrupt")
            user_input = interrupt(
                {
                    "type": "clarification",
                    "agent_id": agent_id,
                    "message": result.clarification.message,
                    "questions": result.clarification.questions,
                    "options": result.clarification.options,
                }
            )
            # 用户回复后，记录并再执行一次
            blackboard.add_agent_turn(agent_id, "user", user_input)
            memory_context = blackboard.get_agent_context(agent_id)
            result = await self.architect_agent.run(
                user_query=user_input,
                analysis_result=analysis_result,
                selected_component=selected_component,
                selected_component_id=selected_component_id,
                knowledge_agent=self.knowledge_agent,
                memory_context=memory_context,
            )
            if result.summary:
                response_json = json.dumps(
                    {"status": result.status, "summary": result.summary}, ensure_ascii=False
                )
                blackboard.add_agent_turn(agent_id, "assistant", response_json)

        return self._handle_agent_result(
            state=state,
            agent_id=agent_id,
            result=result,
        )

    async def _developer_node(self, state: WorkerState) -> Command:
        """数据开发节点"""
        agent_id = "developer_agent"
        blackboard = state.blackboard
        handover = self._ensure_handover(state)

        # 获取工作流
        workflow = handover.get_deliverable("plan")
        if not workflow:
            return self._create_delegation_request(
                state=state,
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

        user_query = blackboard.task or ""

        # 记录用户输入到对话历史
        if user_query:
            blackboard.add_agent_turn(agent_id, "user", user_query)

        # 获取对话历史上下文（支持多轮对话）
        memory_context = blackboard.get_agent_context(agent_id)

        result = await self.developer_agent.run(
            user_query=user_query,
            workflow=workflow,
            review_feedback=review_feedback if isinstance(review_feedback, ReviewResult) else None,
            knowledge_agent=self.knowledge_agent,
            memory_context=memory_context,
        )

        # 记录响应到对话历史
        if result.summary:
            response_json = json.dumps(
                {"status": result.status, "summary": result.summary}, ensure_ascii=False
            )
            blackboard.add_agent_turn(agent_id, "assistant", response_json)

        # 如果需要澄清，直接 interrupt 等待用户输入
        if result.status == "needs_clarification" and result.clarification:
            logger.info(f"⏸️ {agent_id} 需要用户澄清，调用 interrupt")
            user_input = interrupt(
                {
                    "type": "clarification",
                    "agent_id": agent_id,
                    "message": result.clarification.message,
                    "questions": result.clarification.questions,
                    "options": result.clarification.options,
                }
            )
            # 用户回复后，记录并再执行一次
            blackboard.add_agent_turn(agent_id, "user", user_input)
            memory_context = blackboard.get_agent_context(agent_id)
            result = await self.developer_agent.run(
                user_query=user_input,
                workflow=workflow,
                review_feedback=(
                    review_feedback if isinstance(review_feedback, ReviewResult) else None
                ),
                knowledge_agent=self.knowledge_agent,
                memory_context=memory_context,
            )
            if result.summary:
                response_json = json.dumps(
                    {"status": result.status, "summary": result.summary}, ensure_ascii=False
                )
                blackboard.add_agent_turn(agent_id, "assistant", response_json)

        return self._handle_agent_result(
            state=state,
            agent_id=agent_id,
            result=result,
        )

    async def _reviewer_node(self, state: WorkerState) -> Command:
        """Review 节点"""
        agent_id = "reviewer_agent"
        blackboard = state.blackboard
        handover = self._ensure_handover(state)

        # 获取需求分析结果
        analysis_result = handover.get_deliverable("analysis")
        if not analysis_result:
            return self._create_delegation_request(
                state=state,
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
                state=state,
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

        # 开发阶段必须有 SQL
        if review_stage == "development":
            missing_sql_jobs = self._check_missing_sql(workflow)
            if missing_sql_jobs:
                return self._create_delegation_request(
                    state=state,
                    from_agent=agent_id,
                    to_agent="developer_agent",
                    reason="missing_sql",
                    message=f"存在未生成 SQL 的 Job: {', '.join(missing_sql_jobs)}，请先生成完整 SQL。",
                )

        # 记录用户输入到对话历史
        if blackboard.task:
            blackboard.add_agent_turn(agent_id, "user", blackboard.task)

        # 执行 review
        result = await self.reviewer_agent.run(
            user_query=blackboard.task or "",
            analysis_result=analysis_result,
            workflow=workflow,
            review_stage=review_stage,
        )

        # 记录响应到对话历史（使用 JSON 格式，保持与 LLM 输出一致）
        if result.summary:
            response_json = json.dumps(
                {"status": result.status, "summary": result.summary}, ensure_ascii=False
            )
            blackboard.add_agent_turn(agent_id, "assistant", response_json)

        # 处理技术故障
        if result.status == "failed":
            return self._handle_agent_result(
                state=state,
                agent_id=agent_id,
                result=result,
            )

        # 获取 review 结果
        review_result = result.deliverable
        if not isinstance(review_result, ReviewResult):
            return self._create_error_result(
                state, agent_id, "ReviewerAgent 未返回有效的 ReviewResult"
            )

        # 处理 review 结果
        return self._handle_review_result(
            state=state,
            agent_id=agent_id,
            result=result,
            review_result=review_result,
            review_stage=review_stage,
            workflow=workflow,
        )

    async def _human_node(self, state: WorkerState) -> Command:
        """人机交互节点"""
        blackboard = state.blackboard
        pending = [r for r in blackboard.pending_requests if r.kind == "human"]
        if not pending:
            return Command(update={"blackboard": blackboard})

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

        # 移除已处理的请求
        remaining = [r for r in blackboard.pending_requests if r.request_id != req.request_id]
        blackboard.pending_requests = remaining

        # 记录结果
        blackboard.request_results[req.request_id] = {
            "status": "completed",
            "response": user_input,
            "completed_at_ms": int(time.time() * 1000),
        }

        # 处理 post_action
        post_action = payload.get("post_action")
        if post_action == "delegate" and isinstance(user_input, str):
            target_agent = user_input.strip()
            if target_agent in WORKER_AGENT_IDS:
                blackboard.pending_requests.append(
                    BlackboardRequest(
                        request_id=f"req_{uuid.uuid4().hex}",
                        kind="delegate",
                        created_by="human_in_the_loop",
                        target_agent=target_agent,
                        resume_to="boss",
                        payload={
                            "type": payload.get("delegate_reason", "human_selected_next_step"),
                        },
                    )
                )

        # 处理 reset_fields
        reset_fields = payload.get("reset_fields", [])
        if isinstance(reset_fields, list):
            for field_name in reset_fields:
                if field_name == "design_review_iteration_count":
                    blackboard.design_review_iteration_count = 0
                elif field_name == "development_review_iteration_count":
                    blackboard.development_review_iteration_count = 0

        return Command(update={"blackboard": blackboard})

    def _handle_agent_result(
        self,
        *,
        state: WorkerState,
        agent_id: str,
        result: AgentResult,
        create_followup_requests: bool = True,
    ) -> Command:
        """处理 Agent 结果"""
        now_ms = int(time.time() * 1000)
        blackboard = state.blackboard
        handover = self._ensure_handover(state)

        # 弹出已完成的 delegate 请求
        self._pop_completed_request(blackboard, agent_id)

        # 存储交付物到 Handover
        if result.deliverable and result.deliverable_type:
            handover.store_deliverable(result.deliverable_type, result.deliverable)

        # 更新短期记忆中的 Agent 状态
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
        blackboard.reports[agent_id] = report

        # 注意：needs_clarification 已经在各节点中通过 interrupt() 直接处理
        # 不再通过 BlackboardRequest 机制
        # 处理需要委派的情况
        if create_followup_requests and result.status == "needs_delegation" and result.delegation:
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
            blackboard.pending_requests.append(req)

        # 处理失败情况
        if result.status == "failed":
            blackboard.error = result.error

        return Command(update={"blackboard": blackboard, "handover": handover})

    def _handle_review_result(
        self,
        *,
        state: WorkerState,
        agent_id: str,
        result: AgentResult,
        review_result: ReviewResult,
        review_stage: str,
        workflow: Workflow,
    ) -> Command:
        """处理 Review 结果"""
        blackboard = state.blackboard
        handover = self._ensure_handover(state)

        # 先处理基本结果
        self._handle_agent_result(
            state=state,
            agent_id=agent_id,
            result=result,
            create_followup_requests=False,
        )

        # 阶段迭代计数
        stage_counter_field = (
            "design_review_iteration_count"
            if review_stage == "design"
            else "development_review_iteration_count"
        )

        # Review 通过
        if review_result.passed:
            if review_stage == "design":
                blackboard.design_review_passed = True
                blackboard.design_review_iteration_count = 0
            else:
                blackboard.development_review_passed = True
                blackboard.development_review_iteration_count = 0
                blackboard.is_completed = True
                blackboard.deliverable = workflow
            return Command(update={"blackboard": blackboard, "handover": handover})

        # Review 未通过：检查迭代次数
        current_count = getattr(blackboard, stage_counter_field)
        next_count = current_count + 1
        setattr(blackboard, stage_counter_field, next_count)

        # 确定打回目标
        target_agent = "architect_agent" if review_stage == "design" else "developer_agent"
        stage_cn = "设计阶段" if review_stage == "design" else "开发阶段"

        # 超过阈值：暂停，请求用户介入
        if next_count >= self.review_retry_threshold:
            options = [
                {"value": "analyst_agent", "label": "analyst_agent：需求/口径澄清"},
                {"value": "architect_agent", "label": "architect_agent：架构/依赖/写入设计"},
                {"value": "developer_agent", "label": "developer_agent：SQL/字段映射/性能修复"},
            ]

            message = (
                f"{stage_cn} review 已连续 {next_count} 次未通过，已暂停自动回炉。\n"
                "请选择下一步由谁继续处理。"
            )

            blackboard.pending_requests.append(
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
                        "reset_fields": [stage_counter_field],
                    },
                )
            )
            return Command(update={"blackboard": blackboard, "handover": handover})

        # 未超过阈值：直接打回
        blackboard.pending_requests.append(
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

        return Command(update={"blackboard": blackboard, "handover": handover})

    def _create_delegation_request(
        self,
        *,
        state: WorkerState,
        from_agent: str,
        to_agent: str,
        reason: str,
        message: str,
    ) -> Command:
        """创建委派请求"""
        blackboard = state.blackboard

        req = BlackboardRequest(
            request_id=f"req_{uuid.uuid4().hex}",
            kind="delegate",
            created_by=from_agent,
            target_agent=to_agent,
            resume_to=from_agent,
            payload={"type": reason, "message": message},
        )
        blackboard.pending_requests.append(req)

        report = AgentReport(
            status="waiting",
            summary=message,
            updated_at_ms=int(time.time() * 1000),
        )
        blackboard.reports[from_agent] = report

        return Command(update={"blackboard": blackboard})

    def _create_human_request(
        self,
        *,
        state: WorkerState,
        agent_id: str,
        request_type: str,
        message: str,
        options: list[dict],
    ) -> Command:
        """创建人机交互请求"""
        blackboard = state.blackboard

        req = BlackboardRequest(
            request_id=f"req_{uuid.uuid4().hex}",
            kind="human",
            created_by=agent_id,
            resume_to="boss",
            payload={
                "type": request_type,
                "message": message,
                "options": options,
            },
        )
        blackboard.pending_requests.append(req)

        report = AgentReport(
            status="waiting",
            summary=message,
            updated_at_ms=int(time.time() * 1000),
        )
        blackboard.reports[agent_id] = report

        return Command(update={"blackboard": blackboard})

    def _create_error_result(
        self,
        state: WorkerState,
        agent_id: str,
        error: str,
    ) -> Command:
        """创建错误结果"""
        blackboard = state.blackboard

        report = AgentReport(
            status="failed",
            summary=error,
            updated_at_ms=int(time.time() * 1000),
        )
        blackboard.reports[agent_id] = report
        blackboard.error = error

        return Command(update={"blackboard": blackboard})

    def _pop_completed_request(self, blackboard: Blackboard, completed_by: str) -> None:
        """弹出已完成的 delegate 请求"""
        if not blackboard.pending_requests:
            return

        req = blackboard.pending_requests[0]
        if req.kind != "delegate" or req.target_agent != completed_by:
            return

        blackboard.pending_requests = blackboard.pending_requests[1:]
        blackboard.request_results[req.request_id] = {
            "status": "completed",
            "completed_by": completed_by,
            "completed_at_ms": int(time.time() * 1000),
        }

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

    @staticmethod
    def _check_missing_sql(workflow: Workflow) -> list[str]:
        """检查工作流中缺少 SQL 的 Job"""
        missing: list[str] = []
        for job in workflow.jobs:
            sql = job.config.get("content") if job.config else None
            if not (isinstance(sql, str) and sql.strip()):
                missing.append(job.id)
        return missing
