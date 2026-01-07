"""
ETL 多智能体编排器 v2（父子图架构）

架构设计：
- 父图：包含 Boss 节点和 human_in_the_loop 节点
- 子图：WorkerGraph，包含所有员工节点
- Boss 通过 worker_graph.invoke() 调用员工子图

设计原则：
- Boss 独立于员工图，主动查看 Blackboard 和调用员工
- 员工图是独立子图，有自己的状态和路由逻辑
- Blackboard 以 dict 形式存储在状态中，保证序列化/反序列化一致性
- 保持 checkpoint 和 human-in-the-loop 能力
"""

from __future__ import annotations

import logging
import time
import uuid
from collections.abc import AsyncGenerator
from typing import TYPE_CHECKING, Any

from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt
from pydantic import BaseModel, Field

from src.infrastructure.llm.client import call_llm
from src.infrastructure.repository.checkpoint import Checkpoint
from src.modules.etl.boss import BossAgent
from src.modules.etl.context.compress.budget import ContextBudget, get_default_budget
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.state import Blackboard
from src.modules.etl.worker_graph import WorkerGraph
from src.shared.config.settings import settings

if TYPE_CHECKING:
    from src.modules.etl.schemas.sse_msg import SseEvent

logger = logging.getLogger(__name__)


class OrchestratorState(BaseModel):
    """
    编排器状态（父图状态）

    设计说明：
    - blackboard 以 dict 形式存储，保证 checkpoint 序列化/反序列化一致性
    - 节点内部使用 Blackboard.model_validate() 转换为对象操作
    - 节点返回时使用 model_dump() 转换为 dict 存储
    """

    blackboard: dict = Field(default_factory=dict)
    need_human_input: bool = Field(default=False)
    boss_message: str | None = Field(default=None)


class EtlOrchestratorV2:
    """
    ETL 多智能体编排器 v2（父子图架构）

    架构：
    - 父图：boss_node → human_in_the_loop → boss_node（循环）
    - 子图：WorkerGraph（员工图）

    Boss 在父图中，通过 worker_graph.invoke() 调用员工子图。
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

        # Boss Agent
        self.boss = BossAgent()

        # 员工子图
        self.worker_graph = WorkerGraph(
            review_retry_threshold=self.review_retry_threshold,
        )

        # 压缩配置
        self.compress_llm = call_llm(temperature=0.0)
        self.context_budget = context_budget or get_default_budget()

        # 构建父图
        self._graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建父图"""
        graph = StateGraph(OrchestratorState)

        # 添加节点
        graph.add_node("boss", self._boss_node)
        graph.add_node("human_in_the_loop", self._human_node)
        graph.add_node("finalize", self._finalize_node)

        # 设置入口
        graph.set_entry_point("boss")

        # Boss 节点后的路由
        graph.add_conditional_edges(
            "boss",
            self._route_from_boss,
            {
                "human_in_the_loop": "human_in_the_loop",
                "finalize": "finalize",
                "boss": "boss",  # 继续循环
            },
        )

        # human_in_the_loop 后回到 Boss
        graph.add_edge("human_in_the_loop", "boss")

        # finalize 结束
        graph.add_edge("finalize", END)

        return graph

    def _route_from_boss(self, state: OrchestratorState) -> str:
        """Boss 节点后的路由"""
        # 从 dict 恢复 Blackboard 对象
        blackboard = Blackboard.model_validate(state.blackboard)

        # 任务完成
        if blackboard.is_completed:
            return "finalize"

        # 需要人机交互
        if state.need_human_input or blackboard.has_human_request():
            return "human_in_the_loop"

        # 继续循环（Boss 会继续推进任务）
        return "boss"

    async def _boss_node(self, state: OrchestratorState) -> Command:
        """
        Boss 节点

        Boss 在这里：
        1. 查看 Blackboard（主动）
        2. 做决策（LLM 或确定性规则）
        3. 调用员工子图执行任务
        4. 返回更新后的状态
        """
        # 从 dict 恢复 Blackboard 对象
        blackboard = Blackboard.model_validate(state.blackboard)

        # 调用 BossAgent.run()
        result = await self.boss.run(
            user_input=blackboard.task or "",
            blackboard=blackboard,
            worker_graph=self.worker_graph,
        )

        # 更新状态
        updated_blackboard = result.get("blackboard", blackboard)
        need_human = result.get("need_human_input", False)
        boss_message = result.get("boss_message")

        return Command(
            update={
                # 转换为 dict 存储
                "blackboard": updated_blackboard.model_dump(),
                "need_human_input": need_human,
                "boss_message": boss_message,
            }
        )

    async def _human_node(self, state: OrchestratorState) -> Command:
        """人机交互节点"""
        # 从 dict 恢复 Blackboard 对象
        blackboard = Blackboard.model_validate(state.blackboard)
        pending = [r for r in blackboard.pending_requests if r.kind == "human"]

        if not pending:
            return Command(
                update={
                    "blackboard": blackboard.model_dump(),
                    "need_human_input": False,
                }
            )

        req = pending[0]
        payload = req.payload or {}

        message = payload.get("message", "请输入")
        questions = payload.get("questions", [])
        if isinstance(questions, list) and questions:
            question_lines = "\n".join([f"- {str(q)}" for q in questions if str(q).strip()])
            if question_lines.strip():
                message = f"{message}\n\n需要确认：\n{question_lines}"

        # 使用 interrupt 暂停等待用户输入
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

        # 如果是 Boss 发起的对话请求，更新 task 为用户的新输入
        if (
            req.created_by == "boss"
            and payload.get("type") == "boss_conversation"
            and isinstance(user_input, str)
            and user_input.strip()
        ):
            blackboard.task = user_input.strip()

        # 如果是 Agent 发起的澄清请求，更新 task 并创建 delegate 请求
        # 这样用户回复后可以直接回到对应的 agent，不需要 Boss 二次决策
        valid_agents = {
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "reviewer_agent",
        }
        if (
            payload.get("type") == "clarification"
            and req.resume_to in valid_agents
            and isinstance(user_input, str)
            and user_input.strip()
        ):
            # 更新 task 为用户回复
            blackboard.task = user_input.strip()
            # 创建 delegate 请求，让 Boss 直接派发给对应的 agent
            blackboard.pending_requests.append(
                BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="delegate",
                    created_by="human_in_the_loop",
                    target_agent=req.resume_to,
                    resume_to="boss",
                    payload={
                        "type": "clarification_response",
                        "original_request_id": req.request_id,
                    },
                )
            )

        # 处理 post_action（用于 review 超阈值后用户选择下一步）
        post_action = payload.get("post_action")
        if post_action == "delegate" and isinstance(user_input, str):
            target_agent = user_input.strip()
            valid_agents = {
                "analyst_agent",
                "architect_agent",
                "developer_agent",
                "reviewer_agent",
            }
            if target_agent in valid_agents:
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

        return Command(
            update={
                # 转换为 dict 存储
                "blackboard": blackboard.model_dump(),
                "need_human_input": False,
            }
        )

    async def _finalize_node(self, state: OrchestratorState) -> Command:
        """完成节点"""
        # 从 dict 恢复 Blackboard 对象
        blackboard = Blackboard.model_validate(state.blackboard)
        blackboard.is_completed = True
        return Command(update={"blackboard": blackboard.model_dump()})

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
            SseEvent 格式的事件
        """
        from src.modules.etl.schemas.sse_msg import SseEvent

        thread_id = self._get_thread_id(session_id, user_id)
        config = {"configurable": {"thread_id": thread_id}}
        app = await self.compile()

        if resume_value is not None:
            # interrupt 恢复场景
            async for event in app.astream(Command(resume=resume_value), config):
                if "__interrupt__" in event:
                    continue
                sse_event = self._to_sse_event(event)
                if sse_event:
                    yield sse_event.to_dict()
        else:
            # 用户消息场景：检查是否有已存在的 checkpoint
            existing_state = await app.aget_state(config)

            if existing_state and existing_state.values:
                # 已有会话，更新 task 并继续
                blackboard_data = existing_state.values.get("blackboard")
                if blackboard_data and isinstance(blackboard_data, dict):
                    # 从 dict 恢复 Blackboard 对象
                    blackboard = Blackboard.model_validate(blackboard_data)

                    # 更新用户输入（boss.run 会负责记录到对话历史）
                    blackboard.task = user_input
                    # 清除之前的 human 请求（用户已响应）
                    blackboard.pending_requests = [
                        r for r in blackboard.pending_requests if r.kind != "human"
                    ]

                    # 使用 dict 格式的状态继续执行
                    updated_state = OrchestratorState(
                        blackboard=blackboard.model_dump(),
                        need_human_input=False,
                        boss_message=None,
                    )
                    async for event in app.astream(updated_state, config):
                        if "__interrupt__" in event:
                            continue
                        sse_event = self._to_sse_event(event)
                        if sse_event:
                            yield sse_event.to_dict()
                else:
                    # checkpoint 存在但 blackboard 为空或格式不对，创建新会话
                    async for event in self._start_new_session(
                        app, config, session_id, user_id, user_input
                    ):
                        yield event
            else:
                # 全新会话
                async for event in self._start_new_session(
                    app, config, session_id, user_id, user_input
                ):
                    yield event

        # 流程结束，发送 result 事件
        yield SseEvent.result_event(workflow=None, message="流程结束").to_dict()

    async def _start_new_session(
        self,
        app,
        config: dict,
        session_id: str,
        user_id: str,
        user_input: str,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """启动全新会话"""
        initial_blackboard = Blackboard(
            session_id=session_id,
            user_id=user_id,
            task=user_input,
            review_retry_threshold=self.review_retry_threshold,
        )
        # 使用 dict 格式的状态
        initial_state = OrchestratorState(blackboard=initial_blackboard.model_dump())

        async for event in app.astream(initial_state, config):
            if "__interrupt__" in event:
                continue
            sse_event = self._to_sse_event(event)
            if sse_event:
                yield sse_event.to_dict()

    def _to_sse_event(self, event: dict[str, Any]) -> SseEvent | None:
        """将 LangGraph 事件转换为 SseEvent 格式"""
        from src.modules.etl.schemas.sse_msg import SseEvent

        # Agent 名称映射
        agent_names = {
            "boss": "老板",
            "analyst_agent": "需求分析师",
            "architect_agent": "数据架构师",
            "developer_agent": "数据开发",
            "reviewer_agent": "代码审核员",
            "knowledge_agent": "知识检索专家",
            "human_in_the_loop": "人机交互",
        }

        for node_name, node_output in event.items():
            if not isinstance(node_output, dict):
                continue

            # 获取 blackboard（现在是 dict 格式）
            blackboard_data = node_output.get("blackboard")
            if not blackboard_data:
                continue

            # 从 dict 恢复 Blackboard 对象
            if isinstance(blackboard_data, dict):
                blackboard = Blackboard.model_validate(blackboard_data)
            else:
                continue

            # 检查是否有 human 类型的 pending_requests
            pending_requests = blackboard.pending_requests or []
            for req in pending_requests:
                if hasattr(req, "kind") and req.kind == "human":
                    payload = req.payload or {}
                    return SseEvent.interrupt_event(
                        agent_id=req.created_by or "boss",
                        agent_name=agent_names.get(req.created_by or "boss", "老板"),
                        kind=payload.get("type", "question"),
                        message=payload.get("message", "请输入"),
                        questions=payload.get("questions"),
                        options=payload.get("options"),
                    )

            # 检查是否完成
            if blackboard.is_completed:
                deliverable = blackboard.deliverable
                workflow_dict = None
                if deliverable:
                    workflow_dict = (
                        deliverable.model_dump()
                        if hasattr(deliverable, "model_dump")
                        else deliverable
                    )
                return SseEvent.result_event(
                    workflow=workflow_dict,
                    message="ETL 工作流生成完成",
                )

            # Boss 消息
            boss_message = node_output.get("boss_message")
            if boss_message and node_name == "boss":
                return SseEvent.interrupt_event(
                    agent_id="boss",
                    agent_name="老板",
                    kind="boss_conversation",
                    message=boss_message,
                )

            # Agent 状态更新（从 reports 中获取）
            reports = blackboard.reports or {}
            for agent_id, report in reports.items():
                if report and report.status == "in_progress":
                    return SseEvent.agent_start(
                        agent_id=agent_id,
                        agent_name=agent_names.get(agent_id, agent_id),
                    )

        return None

    async def clear_session(self, session_id: str, user_id: str) -> None:
        """清除会话"""
        thread_id = self._get_thread_id(session_id, user_id)
        await Checkpoint.delete_thread(thread_id)
        logger.info(f"Session cleared: {thread_id}")
