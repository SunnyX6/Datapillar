"""
Multi-Agent工作流编排器
 Supervisor 模式
"""

import json
from typing import AsyncGenerator, Optional, List
import logging

logger = logging.getLogger(__name__)
from datetime import datetime

from langgraph.graph import StateGraph, END
from langgraph.checkpoint.redis import AsyncRedisSaver
from langgraph.types import Command
from langchain_core.messages import HumanMessage, AIMessage, BaseMessage

from src.agent.state import OrchestratorState
from src.agent.planner_agent import build_planner_subgraph
from src.agent.coder_agent import CoderAgent
from src.agent.context_manager import ContextManager
from src.config import settings
from src.config.connection import RedisClient
from src.agent.schemas import (
    AgentEventPayload,
    AgentResponse,
)


class Orchestrator:
   

    def __init__(
        self,
        redis_client: RedisClient,
        checkpointer: AsyncRedisSaver,
    ):
        """
        初始化编排器

        Args:
            redis_client: Redis客户端
            checkpointer: AsyncRedisSaver实例
        """
        self.redis_client = redis_client
        self.checkpointer = checkpointer

        # 创建所有 Agent 实例
        self.planner_subgraph = build_planner_subgraph()
        self.coder_agent = CoderAgent()
        self.context_manager = ContextManager()

        # 构建 StateGraph
        self.graph = self._build_graph()

        logger.info("✅ Orchestrator初始化完成（仅 ETL 工作流模式）")

    def _build_graph(self):
        """
        架构（极简版）：
        - 只处理 ETL 工作流
        - 无需意图识别和路由

        流程：
        START → planner_agent → coder_agent → END

        Returns:
            编译后的 StateGraph
        """
        # 创建 StateGraph
        builder = StateGraph(OrchestratorState)

        # ===== 添加节点 =====
        builder.add_node("planner_agent", self.planner_subgraph)
        builder.add_node("coder_agent", self.coder_agent)

        # ===== 添加边 =====
        # START → PlannerAgent
        builder.set_entry_point("planner_agent")

        # PlannerAgent → CoderAgent → END
        builder.add_edge("planner_agent", "coder_agent")
        builder.add_edge("coder_agent", END)

        # 编译图
        graph = builder.compile(checkpointer=self.checkpointer)

        logger.info("LangGraph StateGraph 编译完成（仅 ETL 工作流模式）")
        return graph


    async def as_stream(
        self,
        user_input: Optional[str],
        session_id: Optional[str],
        user_id: str,
        resume_value: Optional[any] = None,
    ) -> AsyncGenerator[str, None]:
        """
        流式响应
        Args:
            user_input: 用户输入（首次执行必传，恢复执行可为 None）
            session_id: 会话ID（用于checkpoint）
            user_id: 用户ID
            resume_value: 用户确认数据（interrupt 恢复时使用）

        Yields:
            事件流
        """
        if not user_id:
            raise ValueError("Orchestrator.as_stream 需要提供 user_id 用于上下文隔离")

        logger.info(
            f"流式生成开始: user_input='{user_input}', session_id={session_id}, "
            f"user_id={user_id}, resume_value={resume_value is not None}"
        )

        if not session_id:
            raise ValueError("Orchestrator.as_stream 需要 session_id")

        thread_id = f"user:{user_id}:session:{session_id}"
        config = {
            "configurable": {
                "thread_id": thread_id,
                "session_id": session_id,
                "user_id": str(user_id),
            }
        }

        # 读取历史消息，进行上下文压缩
        previous_messages: List[BaseMessage] = []
        try:
            previous_snapshot = await self.graph.aget_state(config)
            if previous_snapshot:
                snapshot_values = getattr(previous_snapshot, "values", None)
                if snapshot_values:
                    prev_msgs = snapshot_values.get("messages")
                    if prev_msgs:
                        previous_messages = list(prev_msgs)
        except Exception as exc:  # noqa: BLE001
            logger.debug(f"加载历史状态失败，使用空上下文: {exc}")

        # 构建当前完整消息列表（包含新输入）
        current_messages = list(previous_messages)
        if user_input is not None:
            # 🔥 关键：添加时间戳
            user_msg = HumanMessage(
                content=user_input,
                additional_kwargs={"timestamp": datetime.utcnow().timestamp()}
            )
            current_messages.append(user_msg)

        # 压缩上下文：返回 RemoveMessage + SystemMessage 操作列表
        compress_ops = await self.context_manager.compress_if_needed(current_messages)

        emitted_keys: set[str] = set()

        # start event
        yield self._build_event(
            event_id="Session",
            title="Session",
            event_type="session_started",
            description="会话开始",
            status="running",
        )

        try:
            # 判断是首次执行还是恢复执行
            if resume_value is not None:
                # ========== 恢复执行：使用 Command(resume=...) ==========
                logger.info(f"🔄 恢复执行: resume_value={resume_value}")
                input_or_command = Command(resume=resume_value)
            else:
                # ========== 首次执行：准备输入数据 ==========
                if not user_input:
                    raise ValueError("首次执行时 user_input 不能为空")

                logger.info(f"🆕 首次执行: user_input={user_input}")

                # 准备输入消息列表
                messages_to_send = []

                # 1. 如果需要压缩，先添加 RemoveMessage + SystemMessage
                if compress_ops:
                    messages_to_send.extend(compress_ops)
                    logger.info(f"📦 添加压缩操作: {len(compress_ops)} 条")

                # 2. 添加用户输入
                messages_to_send.append(user_msg)

                # 准备输入
                input_or_command = {
                    "messages": messages_to_send,
                    "user_input": user_input,
                    "user_id": str(user_id),
                    "session_id": session_id,
                }

            # ==========================================================
            # 阶段 1: 流式输出过程事件（Thinking, Tool, Status）
            # ==========================================================
            final_state = None

            AGENTS = {"planner_agent", "coder_agent"}
            # 🔥 子图内的工具节点（需要识别所属 agent）
            TOOL_NODES = {
                "planner_tools": "planner_agent",
                "planner_select_tools": "planner_agent",
            }

            async for event in self.graph.astream_events(
                input_or_command,
                config=config,
                version="v2",
                include_types=["chain", "tool"]
            ):
                kind = event.get("event")
                name = event.get("name")
                # 🔥 从 metadata 里拿节点名
                meta = event.get("metadata", {})
                raw_node = meta.get("langgraph_node")

                # 🔥 如果是工具节点，映射到所属 agent
                if raw_node in TOOL_NODES:
                    current_agent = TOOL_NODES[raw_node]
                else:
                    current_agent = raw_node

                if current_agent not in AGENTS:
                    continue

                # ========== 情况 A: Agent 进场（思考开始）==========
                if kind == "on_chain_start":
                    if name == current_agent:
                        logger.info(f"🤔 Agent 开始思考: {name}")
                        yield self._build_event(
                            event_id=self._agent_id(name),
                            title=self._agent_role(name),
                            event_type="agent_thinking",
                            description=f"{self._agent_role(name)} 正在思考",
                            status="running",
                        )

                # ========== 情况 B: 工具调用开始 ==========
                elif kind == "on_tool_start":
                    logger.info(f"🔧 工具调用: {name}, agent={current_agent}")
                    yield self._build_event(
                        event_id=self._agent_id(current_agent),
                        title=self._agent_role(current_agent),
                        event_type="call_tool",
                        description=f"{self._agent_role(current_agent)} 调用工具 {name}",
                        status="running",
                        tool_name=name,
                        data={"input": event.get("data", {}).get("input", {})}
                    )

                # ========== 情况 C: 工具结束 ==========
                elif kind == "on_tool_end":
                    logger.debug(f"✅ 工具完成: {name}")

                # ========== 情况 D: Agent 退场 ==========
                elif kind == "on_chain_end":
                    # 只处理节点级别的事件，跳过内部 chain（如 RunnableSequence）
                    if name != current_agent:
                        continue

                    event_data = event.get("data", {})
                    response = event_data.get("output")  # agent 返回的可能是 Command 对象或 dict

                    if response is None:
                        continue

                    logger.info(f"✅ Agent 执行完成: {name}")

                    # 🔥 处理 Command 对象（LangGraph 返回）
                    if isinstance(response, Command):
                        # 从 Command.update 中提取实际数据
                        response_data = response.update if hasattr(response, 'update') else {}
                    else:
                        response_data = response

                    # 保存 final_state 用于最后的 completed 事件
                    final_state = response_data

                    # 根据不同 agent 发送特定事件
                    if current_agent == "planner_agent":
                        plan = response_data.get("plan")
                        if plan and "plan" not in emitted_keys:
                            is_found = response_data.get("is_found", False)
                            yield self._build_event(
                                event_id=self._agent_id("planner_agent"),
                                title=self._agent_role("planner_agent"),
                                event_type="plan",
                                description="PlannerAgent 生成执行计划",
                                status="completed",
                                is_found=is_found,
                                tool_name=None,
                                data=plan,  # PlanOutput schema
                            )
                            emitted_keys.add("plan")

                    elif current_agent == "coder_agent":
                        workflow = response_data.get("workflow")
                        if workflow and "workflow" not in emitted_keys:
                            is_found = response_data.get("is_found", False)
                            yield self._build_event(
                                event_id=self._agent_id("coder_agent"),
                                title=self._agent_role("coder_agent"),
                                event_type="code",
                                description="CoderAgent 生成工作流",
                                status="completed",
                                is_found=is_found,
                                tool_name=None,
                                data=workflow,  # WorkflowOutput schema
                            )
                            emitted_keys.add("workflow")

            # ==========================================================
            # 阶段 2: 流式结束后，检查图的状态（核心: 捕获 Interrupt）
            # ==========================================================

            # 🔥 获取当前最新的 Checkpoint 快照
            snapshot = await self.graph.aget_state(config)

            # 🔍 检查是否有挂起的中断任务
            if snapshot.tasks and snapshot.tasks[0].interrupts:
                # 提取 interrupt() 函数抛出的数据
                interrupt_obj = snapshot.tasks[0].interrupts[0]
                interrupt_value = interrupt_obj.value

                logger.info(f"⏸️ [API] 捕获中断，推送给前端: {interrupt_value}")

                # 🔥 推送中断事件
                # 前端收到这个后，应弹出确认框，用户填完后再次调用本接口传 resume_value
                yield self._build_event(
                    event_id="Session",
                    title="Session",
                    event_type="session_interrupted",
                    description="等待用户确认",
                    status="waiting",
                    data={
                        "recommendedData": interrupt_value,
                        "message": "请确认推荐的数据表和字段映射",
                    },
                )
                return

            # ==========================================================
            # 阶段 3: 没有中断，说明图已经运行到 END，发送完成事件
            # ==========================================================

            # 从快照中获取最终状态
            final_state = snapshot.values if snapshot else final_state

            # 检查错误
            if final_state and final_state.get("error"):
                yield self._build_event(
                    event_id="Session",
                    title="Session",
                    event_type="session_error",
                    description="执行失败",
                    status="error",
                    is_found=False,
                    data={"error": final_state.get("error")},
                )
                return

            # 发送 session_completed 事件
            yield self._build_event(
                event_id="Session",
                title="Session",
                event_type="session_completed",
                description="会话完成",
                status="completed",
            )

        except Exception as e:
            error_msg = str(e)
            # 打印完整的错误堆栈
            import traceback
            full_traceback = traceback.format_exc()
            logger.error(f"流式生成失败: {error_msg}")
            logger.error(f"完整堆栈:\n{full_traceback}")

            yield self._build_event(
                event_id="Orchestrator",
                title="Orchestrator",
                event_type="session_error",
                description="执行失败",
                status="error",
                is_found=False,
                data={"error": error_msg},
            )




    def _build_event(
        self,
        *,
        event_id: str,
        title: Optional[str],
        event_type: str,
        description: str,
        status: str,
        is_found: bool = False,
        tool_name: Optional[str] = None,
        data: Optional[any] = None,  # 接受 Schema 对象或字典，Pydantic 会自动序列化
    ) -> str:
        """构建 SSE 事件

        统一结构：所有事件都使用 response 字段

        Args:
            event_id: 事件ID
            title: Agent 展示名称
            event_type: 事件类型
            description: 事件说明
            status: 事件状态（running/completed/error）
            is_found: 是否找到答案（默认 False）
            tool_name: 调用的工具名称（可选）
            data: agent schema 输出数据（Schema 对象或字典，可选）
        """
        if event_type in {"session_started", "session_completed"}:
            event_id = f"{event_id}-{int(datetime.utcnow().timestamp() * 1000)}"

        title_value = title or "System"

        # 构建统一的 response 字段
        response = AgentResponse(
            tool=tool_name,
            data=data,
        )

        payload = AgentEventPayload(
            eventId=event_id,
            title=title_value,
            eventType=event_type,
            description=description,
            status=status,
            is_found=is_found,
            response=response,
        )
        return json.dumps(payload.model_dump(exclude_none=True), ensure_ascii=False)

    @staticmethod
    def _agent_role(node: Optional[str]) -> str:
        if not node:
            return "System"
        return "".join(part.capitalize() for part in node.split("_"))

    @classmethod
    def _agent_id(cls, node: Optional[str]) -> str:
        role = cls._agent_role(node)
        return f"{role}-{int(datetime.utcnow().timestamp() * 1000)}"

    async def clear_session(self, user_id: str, session_id: str) -> int:
        """
        清除会话历史

        Args:
            user_id: 用户ID
            session_id: 会话ID

        Returns:
            删除的键数量
        """
        thread_id = f"user:{user_id}:session:{session_id}"
        pattern = f"*{thread_id}*"
        cursor = 0
        deleted_count = 0

        while True:
            cursor, keys = await self.redis_client.client.scan(
                cursor, match=pattern, count=100
            )
            if keys:
                await self.redis_client.client.delete(*keys)
                deleted_count += len(keys)
            if cursor == 0:
                break

        logger.info(f"[Clear] 已清除 {deleted_count} 个 checkpoint 键")
        return deleted_count


async def create_orchestrator(redis_client: RedisClient) -> Orchestrator:
    """基于注入的 Redis 客户端创建 Orchestrator"""
    ttl_minutes = max(settings.redis_checkpoint_ttl_seconds / 60, 1)
    ttl_config = {
        "default_ttl": ttl_minutes,
        "refresh_on_read": True,
    }

    checkpointer = AsyncRedisSaver(
        redis_client=redis_client.client,
        ttl=ttl_config,
    )
    await checkpointer.setup()
    logger.info(
        "使用 AsyncRedisSaver 作为 LangGraph checkpoint，TTL={} min".format(
            round(ttl_minutes, 2)
        )
    )
    return Orchestrator(redis_client, checkpointer)
