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
from dataclasses import dataclass
from typing import Any

from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph
from langgraph.types import Command

from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import SessionCompletedEvent, SessionStartedEvent, event_bus
from datapillar_oneagentic.sse.event import (
    SseAgent,
    SseEvent,
    SseEventType,
    SseLevel,
    SseMessage,
    SseState,
)
from datapillar_oneagentic.state.blackboard import create_blackboard

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


@dataclass
class _SessionState:
    """会话状态检测结果"""

    existing_state: dict | None
    is_interrupted: bool
    experience_context: str | None


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
        agent_name_map: dict[str, str] | None = None,
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
        self._agent_name_map = agent_name_map or {}
        self.process = process

        # 存储实例
        self._checkpointer = checkpointer
        self._store = store

        # 经验学习
        self._experience_learner = experience_learner
        self._experience_retriever = experience_retriever

        # 编译图（延迟编译）
        self._compiled_graph = None

    def _make_key(self, session_id: str) -> SessionKey:
        """
        构建 SessionKey

        使用 namespace + session_id 组合，确保：
        - 不同 namespace 的数据隔离
        - 同一 namespace 内不同 session 的数据隔离
        """
        return SessionKey(namespace=self.namespace, session_id=session_id)

    def _get_agent_name(self, agent_id: str) -> str:
        """获取 Agent 展示名（无映射时回退为 ID）"""
        return self._agent_name_map.get(agent_id, agent_id)

    def _extract_agent_id_from_interrupt(self, interrupt_obj: Any) -> str | None:
        """从 interrupt 对象中解析节点名"""
        namespaces = getattr(interrupt_obj, "ns", None)
        if isinstance(namespaces, list) and namespaces:
            first = namespaces[0]
            if isinstance(first, str):
                return first.split(":", 1)[0]
        return None

    def _to_sse_dict(self, event: SseEvent, key: SessionKey) -> dict:
        """补充会话信息并转换为 dict"""
        return event.with_session(namespace=key.namespace, session_id=key.session_id).to_dict()

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

    async def _detect_session_state(
        self, compiled, config: dict, query: str | None, key: SessionKey
    ) -> _SessionState:
        """检测会话状态：是否存在、是否中断、经验上下文"""
        existing_state = None
        is_interrupted = False

        try:
            state_snapshot = await compiled.aget_state(config)
            if state_snapshot and state_snapshot.values:
                existing_state = state_snapshot.values
                if hasattr(state_snapshot, "tasks") and state_snapshot.tasks:
                    for task in state_snapshot.tasks:
                        if hasattr(task, "interrupts") and task.interrupts:
                            is_interrupted = True
                            logger.info(f"⏸️ 检测到中断状态: key={key}")
                            break
                if not is_interrupted and existing_state:
                    logger.info(f"🔄 恢复会话状态: key={key}")
        except Exception as e:
            logger.error(f"获取会话状态失败: {e}")

        # 检索经验上下文（仅在新会话且有 query 时）
        experience_context = None
        if self._experience_retriever and not existing_state and not is_interrupted and query:
            try:
                experience_context = await self._experience_retriever.build_context(query)
                if experience_context:
                    logger.info("📚 检索到相似经验，已注入上下文")
            except Exception as e:
                logger.warning(f"检索经验失败: {e}")

        return _SessionState(
            existing_state=existing_state,
            is_interrupted=is_interrupted,
            experience_context=experience_context,
        )

    def _build_stream_input(
        self,
        *,
        query: str | None,
        resume_value: Any | None,
        session_state: _SessionState,
        key: SessionKey,
    ) -> dict | Command | None:
        """根据场景构建 stream 输入"""
        if session_state.is_interrupted and resume_value is not None:
            logger.info(f"▶️ 使用 Command(resume) 恢复中断: key={key}")
            return Command(resume=resume_value)

        if session_state.is_interrupted and query:
            logger.warning(f"⚠️ 中断恢复使用 query 作为 resume_value: key={key}")
            return Command(resume=query)

        if session_state.existing_state and query:
            logger.info(f"💬 续聊模式: key={key}")
            return {
                "messages": [HumanMessage(content=query)],
                "active_agent": self.entry_agent_id,
            }

        if query:
            logger.info(f"🆕 新会话: key={key}")
            input_data = create_blackboard(
                namespace=self.namespace,
                session_id=key.session_id,
                experience_context=session_state.experience_context,
            )
            input_data["messages"] = [HumanMessage(content=query)]
            input_data["active_agent"] = self.entry_agent_id
            return input_data

        return None

    def _process_node_output(
        self, node_name: str, node_output: Any, key: SessionKey
    ) -> tuple[list[dict], int]:
        """处理节点输出，返回 SSE 事件列表和工具调用数"""
        events: list[dict] = []
        tool_count = 0

        if not isinstance(node_output, dict):
            return events, tool_count

        messages = node_output.get("messages", [])
        for msg in messages:
            # 提取思考内容
            thinking_content = self._extract_thinking_from_message(msg)
            if thinking_content:
                events.append(
                    self._to_sse_dict(
                        SseEvent.agent_thinking(
                            agent_id=node_name,
                            agent_name=self._get_agent_name(node_name),
                            content=thinking_content,
                        ),
                        key,
                    )
                )

            # 收集工具调用
            if hasattr(msg, "tool_calls") and msg.tool_calls:
                tool_count += len(msg.tool_calls)
                for tc in msg.tool_calls:
                    tool_name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
                    if tool_name and self._experience_learner:
                        self._experience_learner.record_tool(key.session_id, tool_name)

        return events, tool_count

    async def _build_final_result(
        self, compiled, config: dict, key: SessionKey, start_time: int
    ) -> dict:
        """构建最终结果或中断信息"""
        final_state = await compiled.aget_state(config)

        # 检测中断
        if hasattr(final_state, "tasks") and final_state.tasks:
            for task in final_state.tasks:
                if hasattr(task, "interrupts") and task.interrupts:
                    logger.info(f"⏸️ 执行被中断: key={key}")
                    payloads = [getattr(i, "value", None) for i in task.interrupts]
                    payload = payloads[0] if len(payloads) == 1 else payloads
                    interrupt_obj = task.interrupts[0]
                    agent_id = (
                        getattr(task, "name", None)
                        or getattr(task, "node", None)
                        or self._extract_agent_id_from_interrupt(interrupt_obj)
                        or "unknown"
                    )
                    agent_name = self._get_agent_name(agent_id)
                    event = SseEvent.agent_interrupt(
                        agent_id=agent_id,
                        agent_name=agent_name,
                        payload=payload,
                    ).model_copy(update={"duration_ms": _now_ms() - start_time})
                    return self._to_sse_dict(event, key)

        # 读取 deliverables
        deliverables = {}
        deliverable_keys = []
        if final_state and final_state.values:
            deliverable_keys = final_state.values.get("deliverable_keys", [])

        if self._store and deliverable_keys:
            store_namespaces = [
                ("deliverables", self.namespace, key.session_id, "latest"),
                ("deliverables", self.namespace, key.session_id),
            ]
            for dk in deliverable_keys:
                try:
                    for store_namespace in store_namespaces:
                        item = await self._store.aget(store_namespace, dk)
                        if item:
                            deliverables[dk] = item.value
                            break
                except Exception as e:
                    logger.error(f"读取 deliverable {dk} 失败: {e}")

        timeline_data = None
        if final_state and final_state.values:
            timeline_data = final_state.values.get("timeline")

        event = SseEvent.result_event(
            deliverable=deliverables,
            deliverable_type=None,
        ).model_copy(
            update={
                "duration_ms": _now_ms() - start_time,
                "timeline": timeline_data,
            }
        )
        return self._to_sse_dict(event, key)

    async def stream(
        self,
        *,
        query: str | None = None,
        key: SessionKey,
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
        - key: SessionKey（namespace + session_id 组合）
        - resume_value: interrupt 恢复值（用户对 interrupt 的回答）

        返回：
        - SSE 事件流
        """
        start_time = _now_ms()
        agent_count = 0
        tool_count = 0

        await event_bus.emit(self, SessionStartedEvent(key=key, query=query or ""))

        config = {"configurable": {"thread_id": str(key)}}
        compiled = await self._ensure_compiled()

        if self._experience_learner and query:
            self._experience_learner.start_recording(key.session_id, query)

        # Phase 1: 检测会话状态
        session_state = await self._detect_session_state(compiled, config, query, key)

        # Phase 2: 构建输入
        input_for_stream = self._build_stream_input(
            query=query,
            resume_value=resume_value,
            session_state=session_state,
            key=key,
        )

        if input_for_stream is None:
            logger.error(f"无效调用：query 和 resume_value 都为空: key={key}")
            error_event = SseEvent.error_event(
                message="无效调用：必须提供 query 或 resume_value",
                detail="query 和 resume_value 均为空",
            ).model_copy(update={"duration_ms": 0})
            yield self._to_sse_dict(error_event, key)
            return

        try:
            # Phase 3: 执行流
            async for event in compiled.astream(input_for_stream, config):
                for node_name, node_output in event.items():
                    if node_name == "__end__":
                        continue

                    agent_count += 1
                    if self._experience_learner:
                        self._experience_learner.record_agent(key.session_id, node_name)

                    agent_name = self._get_agent_name(node_name)
                    yield self._to_sse_dict(
                        SseEvent.agent_start(agent_id=node_name, agent_name=agent_name),
                        key,
                    )

                    # 处理节点输出
                    node_events, node_tool_count = self._process_node_output(node_name, node_output, key)
                    for evt in node_events:
                        yield evt
                    tool_count += node_tool_count

                    # 构建 agent.end 事件
                    agent_status = "completed"
                    agent_error = None
                    if isinstance(node_output, dict):
                        agent_status = node_output.get("last_agent_status", "completed")
                        agent_error = node_output.get("last_agent_error")

                    if agent_status in {"failed", "error"}:
                        event = SseEvent(
                            event=SseEventType.AGENT_END,
                            state=SseState.ERROR,
                            level=SseLevel.ERROR,
                            agent=SseAgent(id=node_name, name=agent_name),
                            message=SseMessage(
                                role="assistant",
                                content=agent_error or "执行失败",
                            ),
                        )
                    else:
                        event = SseEvent.agent_end(
                            agent_id=node_name,
                            agent_name=agent_name,
                        )
                    yield self._to_sse_dict(event, key)

            # Phase 5: 构建最终结果
            final_event = await self._build_final_result(compiled, config, key, start_time)
            yield final_event

            # 完成事件和经验记录
            if final_event["event"] == "result":
                deliverables = {}
                result_data = final_event.get("result")
                if isinstance(result_data, dict):
                    deliverables = result_data.get("deliverable") or {}
                await event_bus.emit(
                    self,
                    SessionCompletedEvent(
                        key=key,
                        result=deliverables,
                        duration_ms=_now_ms() - start_time,
                        agent_count=agent_count,
                        tool_count=tool_count,
                    ),
                )
                if self._experience_learner:
                    self._experience_learner.complete_recording(session_id=key.session_id, outcome="success")

        except Exception as e:
            logger.error(f"执行失败: {e}", exc_info=True)
            error_event = SseEvent.error_event(
                message="执行失败",
                detail=str(e),
            ).model_copy(update={"duration_ms": _now_ms() - start_time})
            yield self._to_sse_dict(error_event, key)
            if self._experience_learner:
                self._experience_learner.complete_recording(
                    session_id=key.session_id, outcome="failure", result_summary=str(e)
                )

    async def compact_session(self, session_id: str) -> dict:
        """手动压缩会话（暂不可用，待实现基于 messages 的压缩）"""
        return {"status": "not_implemented", "message": "压缩功能待重构"}

    async def delete_session(self, session_id: str) -> None:
        """删除会话"""
        key = self._make_key(session_id)
        thread_id = str(key)

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
        key = self._make_key(session_id)
        thread_id = str(key)
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
