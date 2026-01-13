"""
Orchestrator - 编排器

负责：
1. 流式执行
2. 断点恢复
3. SSE 事件流
"""

from __future__ import annotations

import logging
import time
from collections.abc import AsyncGenerator
from typing import Any

from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph
from langgraph.types import Command

from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.events import event_bus, SessionStartedEvent, SessionCompletedEvent
from datapillar_oneagentic.state.blackboard import create_blackboard

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


class Orchestrator:
    """
    编排器

    负责执行团队的工作流程。
    """

    def __init__(
        self,
        *,
        namespace: str,
        name: str,
        graph: StateGraph,
        entry_agent_id: str,
        agent_ids: list[str],
        checkpointer,
        store,
        experience_learner=None,
        experience_retriever=None,
        process: Process = Process.SEQUENTIAL,
    ):
        """
        创建编排器

        参数：
        - namespace: 命名空间（用于数据隔离）
        - name: 名称
        - graph: LangGraph 状态图
        - entry_agent_id: 入口 Agent ID
        - agent_ids: 所有 Agent ID 列表
        - checkpointer: Checkpointer 实例
        - store: Store 实例
        - experience_learner: ExperienceLearner 实例（可选）
        - experience_retriever: ExperienceRetriever 实例（可选）
        - process: 执行模式
        """
        self.namespace = namespace
        self.name = name
        self.graph = graph
        self.entry_agent_id = entry_agent_id
        self.agent_ids = agent_ids
        self.process = process

        # 存储实例
        self._checkpointer = checkpointer
        self._store = store

        # 经验学习
        self._experience_learner = experience_learner
        self._experience_retriever = experience_retriever

        # 编译图（延迟编译）
        self._compiled_graph = None

    def _make_thread_id(self, session_id: str) -> str:
        """
        构建 thread_id

        使用 namespace:session_id 格式，确保：
        - 不同 namespace 的数据隔离
        - 同一 namespace 内不同 session 的数据隔离
        """
        return f"{self.namespace}:{session_id}"

    def _extract_thinking_from_message(self, msg: Any) -> str | None:
        """
        从消息中提取思考内容

        支持多种模型的思考格式：
        - GLM: additional_kwargs.reasoning_content
        - Claude: content 中的 thinking blocks
        - DeepSeek: additional_kwargs.reasoning_content
        """
        if not hasattr(msg, "additional_kwargs"):
            return None

        # 1. GLM / DeepSeek 格式（reasoning_content）
        reasoning = msg.additional_kwargs.get("reasoning_content")
        if reasoning:
            return reasoning

        # 2. Claude 格式（content 是 list，包含 thinking blocks）
        content = getattr(msg, "content", None)
        if isinstance(content, list):
            thinking_parts = []
            for block in content:
                if isinstance(block, dict) and block.get("type") == "thinking":
                    thinking_parts.append(block.get("thinking", ""))
            if thinking_parts:
                return "\n".join(thinking_parts)

        return None

    async def _ensure_compiled(self):
        """确保图已编译"""
        if self._compiled_graph is None:
            self._compiled_graph = self.graph.compile(
                checkpointer=self._checkpointer,
                store=self._store,
            )

        return self._compiled_graph

    async def stream(
        self,
        *,
        query: str | None = None,
        session_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        支持三种场景：
        1. 新会话/续聊：query 不为空，resume_value 为空
        2. interrupt 恢复：resume_value 不为空（query 可选，作为上下文）
        3. 纯续聊：query 不为空，已有会话状态

        参数：
        - query: 用户输入（新问题或续聊内容）
        - session_id: 会话 ID（由调用方控制）
        - resume_value: interrupt 恢复值（用户对 interrupt 的回答）

        返回：
        - SSE 事件流
        """
        start_time = _now_ms()
        agent_count = 0
        tool_count = 0

        # 发送会话开始事件
        await event_bus.emit(
            self,
            SessionStartedEvent(
                session_id=session_id,
                query=query or "",
            ),
        )

        # 构建 thread_id（包含 namespace 前缀）
        thread_id = self._make_thread_id(session_id)
        config = {
            "configurable": {
                "thread_id": thread_id,
            }
        }

        # 编译图
        compiled = await self._ensure_compiled()

        # 开始记录经验
        if self._experience_learner and query:
            self._experience_learner.start_recording(session_id, query)

        # 尝试获取现有状态（支持断点恢复）
        state_snapshot = None
        existing_state = None
        is_interrupted = False

        try:
            state_snapshot = await compiled.aget_state(config)
            if state_snapshot and state_snapshot.values:
                existing_state = state_snapshot.values
                # 检查是否处于中断状态
                if hasattr(state_snapshot, "tasks") and state_snapshot.tasks:
                    for task in state_snapshot.tasks:
                        if hasattr(task, "interrupts") and task.interrupts:
                            is_interrupted = True
                            logger.info(f"⏸️ 检测到中断状态: session_id={session_id}")
                            break
                if not is_interrupted and existing_state:
                    logger.info(f"🔄 恢复会话状态: session_id={session_id}")
        except Exception as e:
            logger.error(f"获取会话状态失败: {e}")

        # 检索经验上下文（仅在新会话且有 query 时）
        experience_context = None
        if self._experience_retriever and not existing_state and not is_interrupted and query:
            try:
                experience_context = await self._experience_retriever.build_context(query)
                if experience_context:
                    logger.info(f"📚 检索到相似经验，已注入上下文")
            except Exception as e:
                logger.warning(f"检索经验失败: {e}")

        # 根据状态类型决定执行方式
        input_for_stream: dict | Command | None = None

        if is_interrupted and resume_value is not None:
            # 场景 1: interrupt 恢复 - 使用 resume_value
            input_for_stream = Command(resume=resume_value)
            logger.info(f"▶️ 使用 Command(resume) 恢复中断: session_id={session_id}, resume_value={resume_value}")
        elif is_interrupted and query:
            # 场景 2: 中断状态但没有 resume_value，用 query 作为恢复值（兼容旧行为）
            input_for_stream = Command(resume=query)
            logger.warning(f"⚠️ 中断恢复使用 query 作为 resume_value（建议使用 resume_value 参数）: session_id={session_id}")
        elif existing_state and query:
            # 场景 3: 续聊模式
            input_for_stream = {
                "messages": [HumanMessage(content=query)],
                "active_agent": self.entry_agent_id,
            }
            logger.info(f"💬 续聊模式: session_id={session_id}")
        elif query:
            # 场景 4: 新会话
            input_for_stream = create_blackboard(
                namespace=self.namespace,
                session_id=session_id,
                experience_context=experience_context,
            )
            input_for_stream["messages"] = [HumanMessage(content=query)]
            input_for_stream["active_agent"] = self.entry_agent_id
            logger.info(f"🆕 新会话: session_id={session_id}")
        else:
            # 无效调用：既没有 query 也没有 resume_value
            logger.error(f"无效调用：query 和 resume_value 都为空: session_id={session_id}")
            yield {
                "event": "error",
                "data": {
                    "detail": "无效调用：必须提供 query 或 resume_value",
                    "duration_ms": 0,
                },
            }
            return

        try:
            yield {
                "event": "start",
                "data": {
                    "session_id": session_id,
                    "team": self.name,
                    "entry_agent": self.entry_agent_id,
                    "resumed": existing_state is not None,
                    "from_interrupt": is_interrupted,
                },
            }

            async for event in compiled.astream(input_for_stream, config):
                for node_name, node_output in event.items():
                    if node_name == "__end__":
                        continue

                    agent_count += 1

                    # 记录 Agent 参与
                    if self._experience_learner:
                        self._experience_learner.record_agent(session_id, node_name)

                    # 收集工具调用信息
                    step_tools: list[str] = []
                    step_tool_count = 0
                    if isinstance(node_output, dict):
                        messages = node_output.get("messages", [])
                        for msg in messages:
                            # 提取思考内容
                            thinking_content = self._extract_thinking_from_message(msg)
                            if thinking_content:
                                yield {
                                    "event": "thinking",
                                    "data": {
                                        "agent_id": node_name,
                                        "thinking_content": thinking_content,
                                    },
                                }

                            if hasattr(msg, "tool_calls") and msg.tool_calls:
                                step_tool_count += len(msg.tool_calls)
                                for tc in msg.tool_calls:
                                    tool_name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
                                    if tool_name:
                                        if tool_name not in step_tools:
                                            step_tools.append(tool_name)
                                        # 记录工具使用
                                        if self._experience_learner:
                                            self._experience_learner.record_tool(session_id, tool_name)
                        tool_count += step_tool_count

                    agent_status = "completed"
                    agent_error = None
                    if isinstance(node_output, dict):
                        agent_status = node_output.get("last_agent_status", "completed")
                        agent_error = node_output.get("last_agent_error")

                    event_data = {
                        "agent_id": node_name,
                        "status": agent_status,
                    }
                    if agent_error:
                        event_data["error"] = agent_error

                    yield {
                        "event": "agent",
                        "data": event_data,
                    }

            # 获取最终状态
            final_state = await compiled.aget_state(config)
            final_interrupted = False
            interrupt_info = None

            if hasattr(final_state, "tasks") and final_state.tasks:
                for task in final_state.tasks:
                    if hasattr(task, "interrupts") and task.interrupts:
                        final_interrupted = True
                        interrupt_info = {
                            "interrupts": [
                                {
                                    "value": getattr(i, "value", None),
                                    "resumable": getattr(i, "resumable", True),
                                }
                                for i in task.interrupts
                            ]
                        }
                        logger.info(f"⏸️ 执行被中断: session_id={session_id}")
                        break

            if final_interrupted and interrupt_info:
                yield {
                    "event": "interrupt",
                    "data": {
                        "session_id": session_id,
                        "interrupt_info": interrupt_info,
                        "message": "需要用户输入以继续",
                        "duration_ms": _now_ms() - start_time,
                    },
                }
            else:
                # 读取 deliverables
                deliverables = {}
                deliverable_keys = final_state.values.get("deliverable_keys", [])
                if self._store and deliverable_keys:
                    store_namespaces = [
                        ("deliverables", self.namespace, session_id, "latest"),
                        ("deliverables", self.namespace, session_id),
                    ]
                    for key in deliverable_keys:
                        try:
                            item = None
                            for store_namespace in store_namespaces:
                                item = await self._store.aget(store_namespace, key)
                                if item:
                                    break
                            if item:
                                deliverables[key] = item.value
                        except Exception as e:
                            logger.error(f"读取 deliverable {key} 失败: {e}")

                timeline_data = final_state.values.get("timeline")

                yield {
                    "event": "result",
                    "data": {
                        "deliverables": deliverables,
                        "timeline": timeline_data,
                        "duration_ms": _now_ms() - start_time,
                    },
                }

                await event_bus.emit(
                    self,
                    SessionCompletedEvent(
                        session_id=session_id,
                        result=deliverables,
                        duration_ms=_now_ms() - start_time,
                        agent_count=agent_count,
                        tool_count=tool_count,
                    ),
                )

                # 完成经验记录
                if self._experience_learner:
                    self._experience_learner.complete_recording(
                        session_id=session_id,
                        outcome="success",
                    )

        except Exception as e:
            logger.error(f"执行失败: {e}", exc_info=True)
            yield {
                "event": "error",
                "data": {
                    "detail": str(e),
                    "duration_ms": _now_ms() - start_time,
                },
            }

            # 完成经验记录（失败）
            if self._experience_learner:
                self._experience_learner.complete_recording(
                    session_id=session_id,
                    outcome="failure",
                    result_summary=str(e),
                )

    async def compact_session(self, session_id: str) -> dict:
        """手动压缩会话（暂不可用，待实现基于 messages 的压缩）"""
        return {"status": "not_implemented", "message": "压缩功能待重构"}

    async def delete_session(self, session_id: str) -> None:
        """删除会话"""
        thread_id = self._make_thread_id(session_id)

        # 删除 checkpointer 状态
        if hasattr(self._checkpointer, "adelete_thread"):
            await self._checkpointer.adelete_thread(thread_id)
        elif hasattr(self._checkpointer, "delete_thread"):
            self._checkpointer.delete_thread(thread_id)

        # 删除 deliverables
        store_namespaces = [
            ("deliverables", self.namespace, session_id, "latest"),
            ("deliverables", self.namespace, session_id, "versions"),
            ("deliverables", self.namespace, session_id),
        ]
        for store_namespace in store_namespaces:
            try:
                items = await self._store.asearch(store_namespace)
                for item in items:
                    await self._store.adelete(store_namespace, item.key)
            except Exception as e:
                logger.error(f"删除 deliverables 失败: {e}")


    async def get_session_stats(self, session_id: str) -> dict:
        """获取会话统计"""
        thread_id = self._make_thread_id(session_id)
        config = {"configurable": {"thread_id": thread_id}}

        compiled = await self._ensure_compiled()

        try:
            state_snapshot = await compiled.aget_state(config)
            if not state_snapshot or not state_snapshot.values:
                return {
                    "session_id": session_id,
                    "namespace": self.namespace,
                    "exists": False,
                }

            state = state_snapshot.values

            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "exists": True,
                "message_count": len(state.get("messages", [])),
                "deliverables_count": len(state.get("deliverable_keys", [])),
                "active_agent": state.get("active_agent"),
            }

        except Exception as e:
            logger.error(f"获取会话统计失败: {e}")
            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "error": str(e),
            }
