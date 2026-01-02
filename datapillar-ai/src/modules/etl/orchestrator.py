"""
ETL 多智能体编排器

使用 LangGraph 实现智能体协作：
- 黑板模式（Blackboard）：所有产物写入共享 state，由编排器统一路由
- 动态委派（Delegation）：任意 Agent 可创建请求，编排器抢占处理
- 全局可抢占人机交互（HITL）：请求队列不为空时优先中断等待用户输入
"""

import logging
import json
import time
import uuid
from typing import Any, AsyncGenerator, Literal

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt

from src.modules.etl.agents import (
    AnalystAgent,
    ArchitectAgent,
    DeveloperAgent,
    KnowledgeAgent,
    TesterAgent,
)
from src.modules.etl.memory import MemoryManager
from src.modules.etl.schemas.dag import WorkflowResponse
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType
from src.modules.etl.schemas.plan import Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.sse_msg import SseEvent
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class EtlOrchestrator:
    """
    ETL 多智能体编排器

    黑板协作（重要）：
    - 编排器不再依赖固定流水线边
    - 统一入口 blackboard_router：根据 state 产物与 pending_requests 动态选择下一步
    - 任意 Agent 都可以通过 state.pending_requests 发起：
      - 人机交互（interrupt）
      - 委派给其他 Agent 的子任务（delegate）
    """

    def __init__(
        self,
        checkpointer: BaseCheckpointSaver | None = None,
        max_iterations: int | None = None,
        agent_max_retries: int | None = None,
        max_human_requests: int | None = None,
    ):
        """
        初始化编排器

        Args:
            checkpointer: LangGraph checkpoint 存储
            max_iterations: 最大迭代次数（测试循环）
            agent_max_retries: Agent 执行失败时的最大重试次数
        """
        self.checkpointer = checkpointer or InMemorySaver()
        self.max_iterations = int(
            max_iterations
            if max_iterations is not None
            else settings.get("etl_orchestrator_max_iterations", 3)
        )
        self.agent_max_retries = int(
            agent_max_retries
            if agent_max_retries is not None
            else settings.get("etl_orchestrator_agent_max_retries", 2)
        )
        self.max_human_requests = int(
            max_human_requests
            if max_human_requests is not None
            else settings.get("etl_orchestrator_max_human_requests", 6)
        )

        # 初始化 Memory
        self.memory = MemoryManager()

        # 初始化所有 Agent
        self.knowledge_agent = KnowledgeAgent()
        self.analyst_agent = AnalystAgent()
        self.architect_agent = ArchitectAgent()
        self.developer_agent = DeveloperAgent()
        self.tester_agent = TesterAgent()

        # 构建 LangGraph
        self.graph = self._build_graph()

        logger.info("✅ EtlOrchestrator 初始化完成")

    def _wrap_agent_with_retry(self, agent, agent_name: str):
        """
        包装 Agent，添加重试机制

        Args:
            agent: Agent 实例
            agent_name: Agent 名称（用于日志和错误提示）

        Returns:
            包装后的 Agent 函数
        """
        # 不应重试的错误（前置依赖缺失）
        non_retryable_errors = [
            "缺少需求分析结果",
            "缺少架构方案",
            "缺少",  # 通用前缀
        ]

        def is_retryable_error(error_str: str) -> bool:
            """判断错误是否可重试"""
            for pattern in non_retryable_errors:
                if pattern in error_str:
                    return False
            return True

        async def wrapped(state: AgentState) -> Command:
            last_error = None

            for attempt in range(self.agent_max_retries + 1):
                try:
                    if attempt > 0:
                        logger.warning(f"🔄 {agent_name} 第 {attempt} 次重试...")

                    result = await agent(state)

                    # 检查返回结果中是否有 error 字段
                    if isinstance(result, Command) and result.update:
                        error = result.update.get("error")
                        if error:
                            # 检查是否可重试
                            if not is_retryable_error(str(error)):
                                logger.info(f"ℹ️ {agent_name} 错误不可重试: {error}")
                                return result

                            if attempt < self.agent_max_retries:
                                logger.warning(f"⚠️ {agent_name} 返回错误: {error}，准备重试...")
                                last_error = Exception(error)
                                continue

                    return result

                except Exception as e:
                    # 检查是否是 LangGraph interrupt 异常，如果是则直接传播
                    # interrupt 异常不应被重试机制捕获
                    if "Interrupt" in type(e).__name__ or "interrupt" in str(type(e)).lower():
                        raise

                    last_error = e
                    logger.warning(
                        f"⚠️ {agent_name} 执行异常 (尝试 {attempt + 1}/{self.agent_max_retries + 1}): {e}"
                    )

                    if attempt < self.agent_max_retries:
                        continue

            # 所有重试都失败了，返回友好错误
            error_msg = self._format_agent_error(agent_name, last_error)
            logger.error(f"❌ {agent_name} 最终失败: {last_error}")

            return Command(
                update={
                    "messages": [AIMessage(content=error_msg)],
                    "current_agent": agent_name,
                    "error": str(last_error),
                }
            )

        return wrapped

    def _format_agent_error(self, agent_name: str, error: Exception) -> str:
        """格式化 Agent 错误为用户友好提示"""
        error_str = str(error)

        # JSON 解析错误
        if "JSON" in error_str or "json" in error_str:
            return f"{agent_name} 处理失败：AI 响应格式异常，已重试多次仍无法解析。请简化需求描述后重试。"

        # 超时错误
        if "timeout" in error_str.lower():
            return f"{agent_name} 处理失败：请求超时，请稍后重试。"

        # API 限流
        if "rate" in error_str.lower() or "limit" in error_str.lower():
            return f"{agent_name} 处理失败：AI 服务繁忙，请稍后重试。"

        # 通用错误
        return f"{agent_name} 处理失败：{error_str[:100]}。请重试或简化需求。"

    def _build_graph(self):
        """
        构建 LangGraph 状态图

        结构：
        START → blackboard_router → (动态路由到任意节点) → ... → blackboard_router → finalize → END

        说明：
        - 所有节点执行结束后回到 blackboard_router
        - blackboard_router 优先处理 pending_requests（全局可抢占）
        - 不再使用固定的 clarification_handler/feedback_handler
        """
        builder = StateGraph(AgentState)

        # ===== 添加节点 =====
        builder.add_node("blackboard_router", self._blackboard_router)
        builder.add_node("human_in_the_loop", self._handle_human_in_the_loop)

        builder.add_node(
            "knowledge_agent",
            self._wrap_agent_with_retry(self.knowledge_agent, "知识检索专家")
        )
        builder.add_node(
            "analyst_agent",
            self._wrap_agent_with_retry(self.analyst_agent, "需求分析师")
        )
        builder.add_node(
            "architect_agent",
            self._wrap_agent_with_retry(self.architect_agent, "数据架构师")
        )
        builder.add_node(
            "developer_agent",
            self._wrap_agent_with_retry(self.developer_agent, "数据开发")
        )
        builder.add_node(
            "tester_agent",
            self._wrap_agent_with_retry(self.tester_agent, "测试验证")
        )
        builder.add_node("finalize", self._finalize)

        # ===== 设置入口 =====
        builder.set_entry_point("blackboard_router")

        # ===== router 动态路由 =====
        builder.add_conditional_edges(
            "blackboard_router",
            self._route_from_blackboard,
            {
                "human_in_the_loop": "human_in_the_loop",
                "knowledge_agent": "knowledge_agent",
                "analyst_agent": "analyst_agent",
                "architect_agent": "architect_agent",
                "developer_agent": "developer_agent",
                "tester_agent": "tester_agent",
                "finalize": "finalize",
            },
        )

        # 所有节点执行结束回到 router（黑板驱动）
        builder.add_edge("human_in_the_loop", "blackboard_router")
        builder.add_edge("knowledge_agent", "blackboard_router")
        builder.add_edge("analyst_agent", "blackboard_router")
        builder.add_edge("architect_agent", "blackboard_router")
        builder.add_edge("developer_agent", "blackboard_router")
        builder.add_edge("tester_agent", "blackboard_router")

        # finalize → END
        builder.add_edge("finalize", END)

        # 编译图
        if self.checkpointer:
            graph = builder.compile(checkpointer=self.checkpointer)
        else:
            graph = builder.compile()

        logger.info("LangGraph 状态图编译完成")
        return graph

    # ===== 黑板路由与人机交互 =====

    @staticmethod
    def _now_ms() -> int:
        return int(time.time() * 1000)

    @staticmethod
    def _normalize_node_id(value: str | None) -> str | None:
        if not value:
            return value
        allowed = {
            "blackboard_router",
            "human_in_the_loop",
            "knowledge_agent",
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "tester_agent",
            "finalize",
        }
        if value in allowed:
            return value

        name_to_node = {
            "黑板路由": "blackboard_router",
            "人机交互": "human_in_the_loop",
            "知识检索专家": "knowledge_agent",
            "需求分析师": "analyst_agent",
            "数据架构师": "architect_agent",
            "数据开发": "developer_agent",
            "测试验证": "tester_agent",
            "完成处理": "finalize",
        }
        return name_to_node.get(value, value)

    async def _blackboard_router(self, state: AgentState) -> Command:
        """
        黑板路由器：只负责决定 next_agent（以及必要的抢占请求创建）
        """
        last_node = state.current_agent
        last_node_id = self._normalize_node_id(last_node)
        next_agent = "finalize"
        metadata = dict(state.metadata or {})
        request_results_update: dict[str, Any] | None = None
        counters = dict(state.delegation_counters or {})
        should_clear_error = False

        pending_requests: list[Any] = list(state.pending_requests or [])

        completed_delegate_resume_to: str | None = None

        # 如果队首是 delegate 且目标节点已执行完成，则出队（并按 resume_to 回跳）
        if pending_requests:
            req0_raw = pending_requests[0]
            req0 = BlackboardRequest(**req0_raw) if isinstance(req0_raw, dict) else req0_raw
            if req0.kind == "delegate" and req0.target_agent:
                target_id = self._normalize_node_id(req0.target_agent) or req0.target_agent
                if last_node_id == target_id:
                    completed_delegate_resume_to = req0.resume_to
                    request_results = dict((state.request_results or {}))
                    request_results[req0.request_id] = {
                        "kind": "delegate",
                        "created_by": req0.created_by,
                        "target_agent": target_id,
                        "resume_to": req0.resume_to,
                        "completed_by": target_id,
                        "completed_at_ms": self._now_ms(),
                    }
                    request_results_update = request_results
                    pending_requests = pending_requests[1:]

        def has_pending_human(reqs: list[Any]) -> bool:
            for raw in reqs or []:
                req = BlackboardRequest(**raw) if isinstance(raw, dict) else raw
                if getattr(req, "kind", None) == "human" and getattr(req, "status", "pending") == "pending":
                    return True
            return False

        # 1) 全局可抢占：human 请求优先（避免 delegate 阻塞用户交互）
        if pending_requests and has_pending_human(pending_requests):
            next_agent = "human_in_the_loop"
        elif pending_requests:
            # 1.1) 非 human：仅处理队首（保持 delegate 完成语义依赖“队首→执行→回跳”）
            req0 = pending_requests[0]
            if isinstance(req0, dict):
                req0 = BlackboardRequest(**req0)
            if req0.kind == "delegate":
                target = req0.target_agent or ""
                target_id = self._normalize_node_id(target) if target else None
                next_agent = target_id if target_id else "finalize"
            else:
                next_agent = "finalize"
        else:
            # 1.2) 刚完成 delegate：优先按 resume_to 回跳（保证“委派→返回”闭环语义）
            resume_to = self._normalize_node_id(completed_delegate_resume_to) if completed_delegate_resume_to else None
            if resume_to and resume_to not in {"blackboard_router"} and resume_to in {
                "human_in_the_loop",
                "knowledge_agent",
                "analyst_agent",
                "architect_agent",
                "developer_agent",
                "tester_agent",
                "finalize",
            }:
                next_agent = resume_to
            else:
                # 2) 无 pending_requests：优先处理可恢复错误（避免直接 finalize 造成“假完成”）
                if state.error:
                    recover_count = int(counters.get("orchestrator:error_recovery") or 0)
                    if state.human_request_count < state.max_human_requests and recover_count < 1:
                        req = BlackboardRequest(
                            request_id=f"req_{uuid.uuid4().hex}",
                            kind="human",
                            created_by="blackboard_router",
                            resume_to="blackboard_router",
                            payload={
                                "type": "error_recovery",
                                "message": "系统在生成工作流时遇到异常，需要你补充信息或简化描述后继续。",
                                "questions": [
                                    "请用更具体的一句话重述需求（尽量明确源数据范围与目标产物）。",
                                    "如果方便：请提供任意一项可验证线索（现有 SQL/字段清单/上游表名/目标表名）。",
                                ],
                                "error": str(state.error)[:500],
                            },
                        )
                        pending_requests.append(req)
                        counters["orchestrator:error_recovery"] = recover_count + 1
                        metadata["last_error"] = {
                            "error": str(state.error),
                            "at_ms": self._now_ms(),
                            "recovered_by": "human",
                        }
                        should_clear_error = True
                        next_agent = "human_in_the_loop"
                    else:
                        next_agent = "finalize"
                else:
                    # 3) 动态选择下一步（基于黑板产物）
                    if not state.analysis_result:
                        next_agent = "analyst_agent"
                    elif not state.architecture_plan:
                        next_agent = "architect_agent"
                    else:
                        # plan 已存在：如果 Job 还没生成 SQL，则走 developer
                        plan = state.architecture_plan
                        jobs = plan.get("jobs", []) if isinstance(plan, dict) else getattr(plan, "jobs", [])
                        has_unbuilt_sql = any(
                            not (
                                j.get("config_generated")
                                if isinstance(j, dict)
                                else getattr(j, "config_generated", False)
                            )
                            for j in jobs
                        )
                        if has_unbuilt_sql:
                            next_agent = "developer_agent"
                        else:
                            test_result = state.test_result
                            passed = False
                            if test_result:
                                passed = (
                                    test_result.get("passed", False)
                                    if isinstance(test_result, dict)
                                    else getattr(test_result, "passed", False)
                                )
                            if not test_result:
                                next_agent = "tester_agent"
                            elif not passed and state.iteration_count < state.max_iterations:
                                next_agent = "developer_agent"
                            else:
                                next_agent = "finalize"

        update: dict[str, Any] = {
            "current_agent": "blackboard_router",
            "next_agent": next_agent,
            "last_node": last_node,
            "metadata": metadata,
        }
        update["pending_requests"] = [r.model_dump() if hasattr(r, "model_dump") else r for r in pending_requests]
        if request_results_update is not None:
            update["request_results"] = request_results_update
        if counters != (state.delegation_counters or {}):
            update["delegation_counters"] = counters
        if should_clear_error:
            update["error"] = None
        return Command(update=update)

    @staticmethod
    def _route_from_blackboard(state: AgentState) -> Literal[
        "human_in_the_loop",
        "knowledge_agent",
        "analyst_agent",
        "architect_agent",
        "developer_agent",
        "tester_agent",
        "finalize",
    ]:
        next_agent = state.next_agent or "finalize"
        allowed = {
            "human_in_the_loop",
            "knowledge_agent",
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "tester_agent",
            "finalize",
        }
        return next_agent if next_agent in allowed else "finalize"

    async def _handle_human_in_the_loop(self, state: AgentState) -> Command:
        """
        统一的人机交互处理节点（全局可抢占）
        - 优先处理队列中最早的 human request（允许 human 抢占）
        - interrupt 等待用户输入
        - 完成后从队列移除该请求并回到 blackboard_router
        """
        if not state.pending_requests:
            return Command(update={"current_agent": "human_in_the_loop"})

        if state.human_request_count >= state.max_human_requests:
            return Command(
                update={
                    "current_agent": "human_in_the_loop",
                    "error": f"已达到最大人机交互次数限制: {state.max_human_requests}",
                }
            )

        req_index: int | None = None
        req: BlackboardRequest | None = None
        for idx, raw in enumerate(state.pending_requests):
            cand = BlackboardRequest(**raw) if isinstance(raw, dict) else raw
            if cand.kind == "human" and cand.status == "pending":
                req_index = idx
                req = cand
                break
        if req_index is None or req is None:
            return Command(
                update={
                    "current_agent": "human_in_the_loop",
                    "error": "human_in_the_loop 未找到可处理的 human 请求（可能已完成或队列异常）",
                }
            )

        interrupt_payload = dict(req.payload or {})
        if "type" not in interrupt_payload:
            interrupt_payload["type"] = "human_input"
        if "message" not in interrupt_payload:
            interrupt_payload["message"] = "请补充信息以便继续"

        logger.info("⏸️ 等待用户输入: request_id=%s, type=%s", req.request_id, interrupt_payload.get("type"))
        user_response = interrupt(interrupt_payload)

        request_results = dict((state.request_results or {}))
        request_results[req.request_id] = {
            "kind": "human",
            "created_by": req.created_by,
            "resume_to": req.resume_to,
            "completed_by": "human_in_the_loop",
            "completed_at_ms": self._now_ms(),
            "payload_type": interrupt_payload.get("type"),
            "writeback_key": interrupt_payload.get("writeback_key"),
            "response_preview": str(user_response)[:200],
        }

        # 完成出队（仅移除本次处理的 human request）
        remaining = list(state.pending_requests)
        remaining.pop(req_index)
        responses = dict((state.human_responses or {}))
        responses[req.request_id] = user_response
        writeback_key = interrupt_payload.get("writeback_key")
        writebacks = dict((state.human_writebacks or {}))

        update_selected_component: dict[str, Any] = {}
        if isinstance(writeback_key, str) and writeback_key.strip():
            writebacks[writeback_key] = user_response
            # 关键字段显式化：selected_component 由 human writeback 统一写入 state 字段
            if writeback_key == "selected_component":
                normalized_code: str | None = None
                normalized_id: int | None = None
                if isinstance(user_response, str):
                    normalized_code = user_response.strip()
                elif isinstance(user_response, dict):
                    raw_code = user_response.get("component") or user_response.get("value") or user_response.get("code")
                    if isinstance(raw_code, str) and raw_code.strip():
                        normalized_code = raw_code.strip()
                    raw_id = user_response.get("id") or user_response.get("component_id")
                    if isinstance(raw_id, int):
                        normalized_id = raw_id
                    elif isinstance(raw_id, str) and raw_id.isdigit():
                        normalized_id = int(raw_id)

                if normalized_code:
                    update_selected_component["selected_component"] = normalized_code
                if normalized_id is not None:
                    update_selected_component["selected_component_id"] = normalized_id

        # 对 clarifications：把用户补充并入 user_input（保持与现有 Agent 读取方式一致）
        merged_user_input = state.user_input
        if interrupt_payload.get("type") in {"clarification", "error_recovery"}:
            merged_user_input = f"{state.user_input}\n用户补充: {user_response}"

        return Command(
            update={
                "messages": [HumanMessage(content=f"用户输入: {user_response}")],
                "user_input": merged_user_input,
                "pending_requests": [r.model_dump() if hasattr(r, "model_dump") else r for r in remaining],
                "human_request_count": state.human_request_count + 1,
                "current_agent": "human_in_the_loop",
                "human_responses": responses,
                "human_writebacks": writebacks,
                "request_results": request_results,
                **update_selected_component,
            }
        )

    async def _finalize(self, state: AgentState) -> Command:
        """最终处理 - 生成可渲染的 DAG"""
        logger.info("🎉 工作流完成，生成 DAG 输出")

        # 获取 plan
        plan = state.architecture_plan
        dag_output = None

        if state.error and not plan:
            summary = f"工作流生成失败：{str(state.error)[:200]}"
            return Command(
                update={
                    "messages": [AIMessage(content=summary)],
                    "dag_output": None,
                    "current_agent": "finalize",
                    "is_completed": True,
                }
            )

        if plan:
            # 转换为 Workflow 对象
            if isinstance(plan, dict):
                plan_obj = Workflow(**plan)
            else:
                plan_obj = plan

            # 转换为工作流响应格式
            workflow_response = WorkflowResponse.from_workflow(plan_obj)
            dag_output = workflow_response.model_dump()

            # 生成摘要
            job_count = len(workflow_response.jobs)
            dep_count = len(workflow_response.dependencies)
            summary = f"生成完成：{workflow_response.workflowName}，共 {job_count} 个任务、{dep_count} 条依赖"
            logger.info(f"📊 {summary}")
        else:
            summary = "工作流生成完成，但缺少架构方案"

        return Command(
            update={
                "messages": [AIMessage(content=summary)],
                "dag_output": dag_output,
                "current_agent": "finalize",
                "is_completed": True,
            }
        )

    # ===== 公共接口 =====

    async def run(
        self,
        user_input: str,
        session_id: str,
        user_id: str,
    ) -> dict[str, Any]:
        """
        同步执行（等待完成）

        Args:
            user_input: 用户输入
            session_id: 会话ID
            user_id: 用户ID

        Returns:
            最终状态
        """
        config = self._build_config(session_id, user_id)

        initial_state = {
            "session_id": session_id,
            "user_id": user_id,
            "user_input": user_input,
            "messages": [HumanMessage(content=user_input)],
            "max_iterations": self.max_iterations,
            "max_human_requests": self.max_human_requests,
            "referenced_sql_ids": [],  # 显式清空，避免跨 session 污染
            "pending_requests": [],
            "human_request_count": 0,
            "delegation_counters": {},
            "request_results": {},
            "human_responses": {},
            "human_writebacks": {},
            "selected_component": None,
            "selected_component_id": None,
            "last_node": None,
        }

        final_state = await self.graph.ainvoke(initial_state, config=config)
        return final_state

    async def stream(
        self,
        user_input: str,
        session_id: str,
        user_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行（统一消息流）

        事件类型（见 src/modules/etl/schemas/sse_msg.py）：
        - agent.start / agent.end
        - llm.start / llm.end
        - tool.start / tool.end
        - interrupt / result / error

        Args:
            user_input: 用户输入
            session_id: 会话ID
            user_id: 用户ID
            resume_value: 恢复值（用于中断恢复）

        Yields:
            SseEvent.to_dict() 格式的事件
        """
        config = self._build_config(session_id, user_id)

        if resume_value is not None:
            input_data = Command(resume=resume_value)
        else:
            input_data = {
                "session_id": session_id,
                "user_id": user_id,
                "user_input": user_input,
                "messages": [HumanMessage(content=user_input)],
                "max_iterations": self.max_iterations,
                "max_human_requests": self.max_human_requests,
                "referenced_sql_ids": [],
                "pending_requests": [],
                "human_request_count": 0,
                "delegation_counters": {},
                "request_results": {},
                "human_responses": {},
                "human_writebacks": {},
                "selected_component": None,
                "selected_component_id": None,
                "last_node": None,
            }

        # 当前正在执行的顶层节点（用于绑定 tool/llm 事件）
        active_node: str | None = None
        active_agent_id: str | None = None
        active_agent_name: str | None = None

        # 事件去噪：连续重复的 tool.start/tool.end/llm.start 直接跳过
        last_event_fingerprint: str | None = None

        def _compact(value: Any) -> str | None:
            if value is None:
                return None
            text = str(value)
            return text[:2000] if len(text) > 2000 else text

        def _fingerprint(payload: dict[str, Any]) -> str:
            tool = payload.get("tool") or {}
            llm = payload.get("llm") or {}
            agent = payload.get("agent") or {}
            span = payload.get("span") or {}
            return json.dumps(
                {
                    "event": payload.get("event"),
                    "agent": agent.get("id"),
                    "run_id": span.get("run_id"),
                    "tool": tool.get("name"),
                    "tool_in": _compact(tool.get("input")),
                    "tool_out": _compact(tool.get("output")),
                    "llm": llm.get("name"),
                },
                ensure_ascii=False,
                sort_keys=True,
                default=str,
            )

        def _emit(evt: SseEvent) -> dict[str, Any] | None:
            nonlocal last_event_fingerprint
            payload = evt.to_dict()
            fp = _fingerprint(payload)
            if fp == last_event_fingerprint:
                return None
            last_event_fingerprint = fp
            return payload

        try:
            async for event in self.graph.astream_events(
                input_data,
                config=config,
                version="v2",
            ):
                kind = event.get("event")
                meta = event.get("metadata", {})
                node = meta.get("langgraph_node")
                run_id = event.get("run_id")
                parent_run_id = event.get("parent_run_id")

                def _bind_agent_from_node(node_id: str) -> tuple[str, str]:
                    return node_id, self._agent_display_name(node_id)

                # 只关心顶层编排节点（避免内部 chain 事件刷屏）
                is_top_node = node in {
                    "blackboard_router",
                    "human_in_the_loop",
                    "knowledge_agent",
                    "analyst_agent",
                    "architect_agent",
                    "developer_agent",
                    "tester_agent",
                    "finalize",
                }

                # blackboard_router/human_in_the_loop 都是“系统节点”：
                # - blackboard_router：内部调度噪声
                # - human_in_the_loop：前端只需要展示 interrupt 事件（waiting），不需要 agent.start/end
                is_hidden_agent_node = node in {"blackboard_router", "human_in_the_loop"}

                # 顶层 Agent 开始
                if kind == "on_chain_start" and node and is_top_node:
                    # 防止同一节点的重复 on_chain_start 导致前端出现“重复开始”
                    if active_node == node and active_agent_id:
                        continue
                    active_node = node
                    active_agent_id, active_agent_name = _bind_agent_from_node(node)
                    if is_hidden_agent_node:
                        continue
                    maybe = _emit(
                        SseEvent.agent_start(
                            agent_id=active_agent_id,
                            agent_name=active_agent_name,
                            run_id=run_id,
                            parent_run_id=parent_run_id,
                        )
                    )
                    if maybe:
                        yield maybe
                    continue

                # 顶层 Agent 结束
                if kind == "on_chain_end" and node and is_top_node:
                    # 只对当前 active_node 发 end，避免重复结束事件刷屏
                    if active_node != node:
                        continue
                    agent_id, agent_name = _bind_agent_from_node(node)
                    summary = None
                    data = event.get("data") or {}
                    output = data.get("output")
                    if isinstance(output, dict):
                        msgs = output.get("messages")
                        if isinstance(msgs, list) and msgs:
                            last_msg = msgs[-1]
                            if hasattr(last_msg, "content"):
                                summary = getattr(last_msg, "content", None)
                    if not is_hidden_agent_node:
                        maybe = _emit(
                            SseEvent.agent_end(
                                agent_id=agent_id,
                                agent_name=agent_name,
                                summary=summary,
                                run_id=run_id,
                                parent_run_id=parent_run_id,
                            )
                        )
                        if maybe:
                            yield maybe
                    if active_node == node:
                        active_node = None
                        active_agent_id = None
                        active_agent_name = None
                    continue

                # LLM start/end 对前端用户是噪声：不再下发
                if kind in {"on_chat_model_start", "on_llm_start", "on_chat_model_end", "on_llm_end"}:
                    continue

                # 工具调用
                if kind == "on_tool_start":
                    if active_agent_id and active_agent_name:
                        if not self._should_emit_tool_start_sse(active_agent_id):
                            continue
                        tool_name = event.get("name", "unknown")
                        maybe = _emit(
                            SseEvent.tool_start(
                                agent_id=active_agent_id,
                                agent_name=active_agent_name,
                                tool_name=tool_name,
                                tool_input=None,
                                run_id=run_id,
                                parent_run_id=parent_run_id,
                            )
                        )
                        if maybe:
                            yield maybe
                    continue

                # tool.end 对用户是噪声：不再下发（只保留 tool.start）
                if kind == "on_tool_end":
                    continue

            # 检查中断
            snapshot = await self.graph.aget_state(config)
            if snapshot.tasks and snapshot.tasks[0].interrupts:
                interrupt_data = snapshot.tasks[0].interrupts[0].value
                # interrupt 发生时 current_agent 可能是 handler，这里优先用 interrupt.type 映射
                kind = interrupt_data.get("type") or "interrupt"
                agent_id = active_agent_id or "human_in_the_loop"
                agent_name = active_agent_name or self._agent_display_name(agent_id)
                yield SseEvent.interrupt_event(
                    agent_id=agent_id,
                    agent_name=agent_name,
                    kind=kind,
                    message=interrupt_data.get("message", "请回答以下问题"),
                    questions=interrupt_data.get("questions"),
                    options=interrupt_data.get("options"),
                ).to_dict()
                return

            # 完成
            workflow = None
            if snapshot and snapshot.values:
                workflow = snapshot.values.get("dag_output")
                error = snapshot.values.get("error")
                if error:
                    current_agent = snapshot.values.get("current_agent")
                    node_to_name = {
                        "blackboard_router": "黑板路由",
                        "human_in_the_loop": "人机交互",
                        "knowledge_agent": "知识检索专家",
                        "analyst_agent": "需求分析师",
                        "architect_agent": "数据架构师",
                        "developer_agent": "数据开发",
                        "tester_agent": "测试验证",
                        "finalize": "完成处理",
                    }
                    name_to_node = {v: k for k, v in node_to_name.items()}
                    agent_id = None
                    agent_name = None
                    if isinstance(current_agent, str):
                        if current_agent in node_to_name:
                            agent_id = current_agent
                            agent_name = node_to_name[current_agent]
                        elif current_agent in name_to_node:
                            agent_id = name_to_node[current_agent]
                            agent_name = current_agent
                    yield SseEvent.error_event(
                        message="执行失败",
                        detail=str(error),
                        agent_id=agent_id,
                        agent_name=agent_name,
                    ).to_dict()
                    return

            if workflow:
                yield SseEvent.result_event(workflow=workflow, message="生成完成").to_dict()
            else:
                yield SseEvent.result_event(workflow=None, message="处理完成，但未生成工作流").to_dict()

        except Exception as e:
            logger.error(f"流式执行失败: {e}", exc_info=True)
            yield SseEvent.error_event(message="执行失败", detail=str(e)).to_dict()

    @staticmethod
    def _should_emit_tool_start_sse(agent_id: str) -> bool:
        return agent_id != "knowledge_agent"

    def _build_config(self, session_id: str, user_id: str) -> dict[str, Any]:
        """构建配置"""
        thread_id = f"etl:user:{user_id}:session:{session_id}"
        return {
            "configurable": {
                "thread_id": thread_id,
                "session_id": session_id,
                "user_id": user_id,
            }
        }

    @staticmethod
    def _agent_display_name(node: str) -> str:
        """获取 Agent 展示名称"""
        names = {
            "blackboard_router": "黑板路由",
            "human_in_the_loop": "人机交互",
            "knowledge_agent": "知识检索专家",
            "analyst_agent": "需求分析师",
            "architect_agent": "数据架构师",
            "developer_agent": "数据开发",
            "tester_agent": "测试验证",
            "learning_handler": "学习沉淀",
            "finalize": "完成处理",
        }
        return names.get(node, node)


async def create_etl_orchestrator(
    checkpointer: BaseCheckpointSaver | None = None,
    max_iterations: int = 3,
    agent_max_retries: int = 2,
) -> EtlOrchestrator:
    """
    创建 ETL 编排器

    Args:
        checkpointer: LangGraph checkpoint 存储
        max_iterations: 最大迭代次数
        agent_max_retries: Agent 执行失败时的最大重试次数

    Returns:
        EtlOrchestrator 实例
    """
    return EtlOrchestrator(
        checkpointer=checkpointer,
        max_iterations=max_iterations,
        agent_max_retries=agent_max_retries,
    )
