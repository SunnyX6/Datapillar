"""
Orchestrator - 编排器

负责：
1. 流式执行
2. 断点恢复
3. SSE 事件流
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncGenerator
from dataclasses import dataclass
from typing import Any

from langgraph.graph import StateGraph
from langgraph.types import Command

from datapillar_oneagentic.exception import AgentError
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind, is_failed
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import (
    AgentStartedEvent,
    EventBus,
    EventType,
    LLMCallCompletedEvent,
    LLMCallFailedEvent,
    LLMCallStartedEvent,
    LLMStreamChunkEvent,
    SessionCompletedEvent,
    SessionStartedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
    build_event_payload,
)
from datapillar_oneagentic.providers.llm.llm import extract_thinking
from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.utils.time import now_ms
from datapillar_oneagentic.exception import LLMError

logger = logging.getLogger(__name__)


@dataclass
class _SessionState:
    """会话状态检测结果"""

    state: dict | None
    is_interrupted: bool


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
        event_bus: EventBus,
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
        - event_bus: EventBus 实例
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
        self._event_bus = event_bus

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

    async def _clear_store_artifacts(
        self,
        session_id: str,
        deliverable_keys: list[str],
    ) -> None:
        """清理 deliverables 的 Store 数据（不清理 checkpointer）"""
        if not self._store or not deliverable_keys:
            return

        deliverable_namespace = ("deliverables", self.namespace, session_id)
        for deliverable_key in set(deliverable_keys):
            if not deliverable_key:
                continue
            try:
                await self._store.adelete(deliverable_namespace, deliverable_key)
            except Exception as e:
                logger.error(f"清理 Store 失败: key={deliverable_key}, error={e}")

    async def _load_deliverable(self, session_id: str, agent_id: str) -> Any | None:
        """读取指定 Agent 的交付物"""
        if not self._store:
            return None
        deliverable_namespace = ("deliverables", self.namespace, session_id)
        try:
            item = await self._store.aget(deliverable_namespace, agent_id)
        except Exception as e:
            logger.error(f"读取 deliverable 失败: agent={agent_id}, error={e}")
            return None
        if not item:
            return None
        return item.value

    async def _load_deliverable_keys(self, compiled, checkpoint_manager) -> list[str]:
        try:
            state = await checkpoint_manager.get_state(compiled)
        except Exception as e:
            logger.warning(f"读取会话状态失败: {e}")
            return []
        if not state:
            return []
        sb = StateBuilder(state)
        return sb.deliverables.snapshot().keys

    async def _load_deliverables_map(
        self,
        *,
        session_id: str,
        deliverable_keys: list[str],
    ) -> dict[str, Any]:
        if not self._store or not deliverable_keys:
            return {}
        deliverables: dict[str, Any] = {}
        deliverable_namespace = ("deliverables", self.namespace, session_id)
        for deliverable_key in deliverable_keys:
            if not deliverable_key:
                continue
            try:
                item = await self._store.aget(deliverable_namespace, deliverable_key)
            except Exception as e:
                logger.error(f"读取 deliverable 失败: key={deliverable_key}, error={e}")
                continue
            if item:
                deliverables[deliverable_key] = item.value
        return deliverables

    async def _clear_deliverable_refs(self, compiled, checkpoint_manager) -> None:
        try:
            state = await checkpoint_manager.get_state(compiled)
        except Exception as e:
            logger.warning(f"读取会话状态失败: {e}")
            return
        if not state:
            return
        sb = StateBuilder(state)
        sb.deliverables.clear()
        try:
            await checkpoint_manager.update_state(compiled, sb.patch())
        except Exception as e:
            logger.warning(f"清理交付物引用失败: {e}")

    async def _clear_state_artifacts(self, compiled, checkpoint_manager) -> None:
        """清理 blackboard 中的 todo/deliverables 引用"""
        try:
            state = await checkpoint_manager.get_state(compiled)
        except Exception as e:
            logger.warning(f"读取会话状态失败: {e}")
            return
        if not state:
            return
        sb = StateBuilder(state)
        sb.todo.clear()
        sb.deliverables.clear()
        try:
            await checkpoint_manager.update_state(compiled, sb.patch())
        except Exception as e:
            logger.warning(f"清理状态失败: {e}")

    async def _cleanup_session_artifacts(
        self,
        *,
        session_id: str,
        compiled,
        checkpoint_manager,
    ) -> None:
        deliverable_keys = await self._load_deliverable_keys(compiled, checkpoint_manager)
        await self._clear_store_artifacts(session_id, deliverable_keys)
        await self._clear_state_artifacts(compiled, checkpoint_manager)

    def _extract_thinking_from_message(self, msg: Any) -> str | None:
        """
        从消息中提取思考内容

        支持多种模型的思考格式：
        - GLM: additional_kwargs.reasoning_content
        - Claude: content 中的 thinking blocks
        - DeepSeek: additional_kwargs.reasoning_content
        """
        return extract_thinking(msg)

    def _build_error_event(self, error: Exception, *, key: SessionKey, start_time: int) -> dict:
        """构建错误 SSE 事件（用于 stream 场景返回错误给调用方）"""
        if isinstance(error, LLMError):
            agent_id = error.agent_id
            agent_name = self._get_agent_name(agent_id) if agent_id else None
            detail_parts = [
                f"category={error.category.value}",
                f"action={error.action.value}",
            ]
            if error.provider:
                detail_parts.append(f"provider={error.provider}")
            if error.model:
                detail_parts.append(f"model={error.model}")
            if agent_id:
                detail_parts.append(f"agent_id={agent_id}")
            detail_parts.append(f"error={str(error)}")
            return build_event_payload(
                event=EventType.AGENT_FAILED,
                key=key,
                agent_id=agent_id or "system",
                agent_name=agent_name or "系统",
                duration_ms=now_ms() - start_time,
                data={
                    "error": {
                        "message": "LLM 执行失败",
                        "detail": "; ".join(detail_parts),
                        "error_type": "llm",
                    }
                },
            )

        if isinstance(error, AgentError):
            agent_id = error.agent_id
            detail_parts = [
                f"category={error.category.value}",
                f"action={error.action.value}",
                f"failure_kind={error.failure_kind.value}",
                f"agent_id={agent_id}",
                f"error={str(error)}",
            ]
            return build_event_payload(
                event=EventType.AGENT_FAILED,
                key=key,
                agent_id=agent_id or "system",
                agent_name=self._get_agent_name(agent_id) if agent_id else "系统",
                duration_ms=now_ms() - start_time,
                data={
                    "error": {
                        "message": "Agent 执行失败",
                        "detail": "; ".join(detail_parts),
                        "error_type": "agent",
                    }
                },
            )

        return build_event_payload(
            event=EventType.AGENT_FAILED,
            key=key,
            agent_id="system",
            agent_name="系统",
            duration_ms=now_ms() - start_time,
            data={
                "error": {
                    "message": "执行失败",
                    "detail": str(error),
                    "error_type": "system",
                }
            },
        )

    async def _ensure_compiled(self):
        """确保图已编译"""
        if self._compiled_graph is None:
            self._compiled_graph = self.graph.compile(
                checkpointer=self._checkpointer,
                store=self._store,
            )

        return self._compiled_graph

    async def _detect_session_state(
        self,
        *,
        compiled,
        query: str | None,
        key: SessionKey,
        checkpoint_manager,
    ) -> _SessionState:
        """检测会话状态：是否存在、是否中断、经验上下文"""
        state = None
        is_interrupted = False

        try:
            snapshot = await checkpoint_manager.get_snapshot(compiled)
            interrupts = ContextBuilder.extract_interrupts(snapshot)
            if interrupts:
                is_interrupted = True
                logger.info(f"⏸️ 检测到中断状态: key={key}")
            values = getattr(snapshot, "values", None) if snapshot else None
            if values and not is_interrupted:
                state = dict(values)
                logger.info(f"🔄 恢复会话状态: key={key}")
        except Exception as e:
            logger.error(f"获取会话状态失败: {e}")

        return _SessionState(
            state=state,
            is_interrupted=is_interrupted,
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
            logger.info(f"使用 Command(resume) 恢复中断: key={key}")
            return Command(resume=resume_value)

        if session_state.is_interrupted and query:
            logger.warning(f"中断恢复使用 query 作为 resume_value: key={key}")
            return Command(resume=query)

        if session_state.state and query:
            logger.info(f"续聊模式: key={key}")
            return StateBuilder.build_resume_update(
                state=session_state.state,
                query=query,
                entry_agent_id=self.entry_agent_id,
            )

        if query:
            logger.info(f"新会话: key={key}")
            return StateBuilder.build_initial_state(
                namespace=self.namespace,
                session_id=key.session_id,
                query=query,
                entry_agent_id=self.entry_agent_id,
            )

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
                    build_event_payload(
                        event=EventType.AGENT_THINKING,
                        key=key,
                        agent_id=node_name,
                        agent_name=self._get_agent_name(node_name),
                        data={
                            "message": {
                                "role": "assistant",
                                "content": thinking_content,
                            }
                        },
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

    async def _build_interrupt_event(
        self,
        *,
        compiled,
        checkpoint_manager,
        key: SessionKey,
        start_time: int,
    ) -> dict | None:
        """检测中断并构建事件（未中断返回 None）"""
        try:
            snapshot = await checkpoint_manager.get_snapshot(compiled)
            interrupts = ContextBuilder.extract_interrupts(snapshot)
        except Exception as e:
            logger.error(f"检测中断失败: {e}")
            return None

        if not interrupts:
            return None

        first_interrupt = interrupts[0]
        agent_id = first_interrupt.get("agent_id", "unknown")
        agent_name = self._get_agent_name(agent_id)
        payload = first_interrupt.get("payload")
        logger.info(f"执行被中断: key={key}")
        return build_event_payload(
            event=EventType.AGENT_INTERRUPT,
            key=key,
            agent_id=agent_id,
            agent_name=agent_name,
            duration_ms=now_ms() - start_time,
            data={
                "interrupt": {
                    "payload": payload,
                }
            },
        )

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
        start_time = now_ms()
        agent_count = 0
        tool_count = 0

        await self._event_bus.emit(self, SessionStartedEvent(key=key, query=query or ""))

        config = {"configurable": {"thread_id": str(key)}}
        compiled = await self._ensure_compiled()
        checkpoint_manager = ContextBuilder.create_checkpoint_manager(
            key=key,
            checkpointer=self._checkpointer,
        )

        if self._experience_learner and query:
            self._experience_learner.start_recording(key.session_id, query)

        # Phase 1: 检测会话状态
        session_state = await self._detect_session_state(
            compiled=compiled,
            query=query,
            key=key,
            checkpoint_manager=checkpoint_manager,
        )

        # Phase 2: 构建输入
        input_for_stream = self._build_stream_input(
            query=query,
            resume_value=resume_value,
            session_state=session_state,
            key=key,
        )

        if input_for_stream is None:
            logger.error(f"无效调用：query 和 resume_value 都为空: key={key}")
            yield build_event_payload(
                event=EventType.AGENT_FAILED,
                key=key,
                agent_id="system",
                agent_name="系统",
                duration_ms=0,
                data={
                    "error": {
                        "message": "无效调用：必须提供 query 或 resume_value",
                        "detail": "query 和 resume_value 均为空",
                        "error_type": "system",
                    }
                },
            )
            return

        event_queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        started_agents: set[str] = set()

        async def _handle_agent_started(_source, event: AgentStartedEvent) -> None:
            if event.key is None or event.key != key:
                return
            agent_id = event.agent_id or ""
            if agent_id in started_agents:
                return
            started_agents.add(agent_id)
            await event_queue.put(
                build_event_payload(
                    event=EventType.AGENT_START,
                    key=event.key,
                    agent_id=agent_id,
                    agent_name=event.agent_name or self._get_agent_name(agent_id),
                )
            )

        async def _handle_tool_called(_source, event: ToolCalledEvent) -> None:
            if event.key is None or event.key != key:
                return
            data = {
                "tool": {
                    "name": event.tool_name,
                    "input": event.tool_input,
                }
            }
            if event.tool_call_id:
                data["tool"]["call_id"] = event.tool_call_id
            await event_queue.put(
                build_event_payload(
                    event=EventType.TOOL_CALL,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    data=data,
                )
            )

        async def _handle_tool_completed(_source, event: ToolCompletedEvent) -> None:
            if event.key is None or event.key != key:
                return
            data = {
                "tool": {
                    "name": event.tool_name,
                    "output": event.tool_output,
                }
            }
            if event.tool_call_id:
                data["tool"]["call_id"] = event.tool_call_id
            await event_queue.put(
                build_event_payload(
                    event=EventType.TOOL_RESULT,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    duration_ms=event.duration_ms if event.duration_ms else None,
                    data=data,
                )
            )

        async def _handle_tool_failed(_source, event: ToolFailedEvent) -> None:
            if event.key is None or event.key != key:
                return
            data = {
                "tool": {
                    "name": event.tool_name,
                    "error": event.error,
                }
            }
            if event.tool_call_id:
                data["tool"]["call_id"] = event.tool_call_id
            await event_queue.put(
                build_event_payload(
                    event=EventType.TOOL_ERROR,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    data=data,
                )
            )

        async def _handle_llm_started(_source, event: LLMCallStartedEvent) -> None:
            if event.key is None or event.key != key:
                return
            await event_queue.put(
                build_event_payload(
                    event=EventType.LLM_START,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    data={
                        "model": event.model,
                        "message_count": event.message_count,
                    },
                )
            )

        async def _handle_llm_completed(_source, event: LLMCallCompletedEvent) -> None:
            if event.key is None or event.key != key:
                return
            await event_queue.put(
                build_event_payload(
                    event=EventType.LLM_END,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    duration_ms=event.duration_ms if event.duration_ms else None,
                    data={
                        "model": event.model,
                        "usage": {
                            "input_tokens": event.input_tokens,
                            "output_tokens": event.output_tokens,
                            "cached_tokens": event.cached_tokens,
                        },
                    },
                )
            )

        async def _handle_llm_failed(_source, event: LLMCallFailedEvent) -> None:
            if event.key is None or event.key != key:
                return
            await event_queue.put(
                build_event_payload(
                    event=EventType.LLM_END,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    duration_ms=event.duration_ms if event.duration_ms else None,
                    data={
                        "model": event.model,
                        "error": {
                            "message": event.error,
                        },
                    },
                )
            )

        async def _handle_llm_chunk(_source, event: LLMStreamChunkEvent) -> None:
            if event.key is None or event.key != key:
                return
            await event_queue.put(
                build_event_payload(
                    event=EventType.LLM_CHUNK,
                    key=event.key,
                    agent_id=event.agent_id,
                    agent_name=self._get_agent_name(event.agent_id),
                    data={
                        "chunk": event.chunk,
                        "is_final": event.is_final,
                    },
                )
            )

        handlers = [
            (AgentStartedEvent, _handle_agent_started),
            (ToolCalledEvent, _handle_tool_called),
            (ToolCompletedEvent, _handle_tool_completed),
            (ToolFailedEvent, _handle_tool_failed),
            (LLMCallStartedEvent, _handle_llm_started),
            (LLMCallCompletedEvent, _handle_llm_completed),
            (LLMCallFailedEvent, _handle_llm_failed),
            (LLMStreamChunkEvent, _handle_llm_chunk),
        ]
        for event_type, handler in handlers:
            self._event_bus.register(event_type, handler)

        try:
            # Phase 3: 执行流
            async for event in compiled.astream(input_for_stream, config):
                while not event_queue.empty():
                    try:
                        queued = event_queue.get_nowait()
                    except asyncio.QueueEmpty:
                        break
                    yield queued

                for node_name, node_output in event.items():
                    if node_name == "__end__":
                        continue

                    agent_count += 1
                    if self._experience_learner:
                        self._experience_learner.record_agent(key.session_id, node_name)

                    agent_name = self._get_agent_name(node_name)
                    if node_name not in started_agents:
                        started_agents.add(node_name)
                        yield build_event_payload(
                            event=EventType.AGENT_START,
                            key=key,
                            agent_id=node_name,
                            agent_name=agent_name,
                        )

                    # 处理节点输出
                    node_events, node_tool_count = self._process_node_output(node_name, node_output, key)
                    for evt in node_events:
                        yield evt
                    tool_count += node_tool_count

                    # 构建 agent 结束事件
                    agent_status = ExecutionStatus.COMPLETED
                    agent_error = None
                    agent_failure_kind = None
                    if isinstance(node_output, dict):
                        agent_status = node_output.get("last_agent_status", ExecutionStatus.COMPLETED)
                        agent_error = node_output.get("last_agent_error")
                        agent_failure_kind = node_output.get("last_agent_failure_kind")

                    if is_failed(agent_status):
                        error_type = (
                            agent_failure_kind.value
                            if isinstance(agent_failure_kind, FailureKind)
                            else "agent"
                        )
                        yield build_event_payload(
                            event=EventType.AGENT_FAILED,
                            key=key,
                            agent_id=node_name,
                            agent_name=agent_name,
                            data={
                                "error": {
                                    "message": "Agent 执行失败",
                                    "detail": agent_error or "执行失败",
                                    "error_type": error_type,
                                }
                            },
                        )
                    else:
                        deliverable = await self._load_deliverable(key.session_id, node_name)
                        data: dict[str, Any] | None = None
                        if deliverable is not None:
                            data = {
                                "deliverable": deliverable,
                            }
                        yield build_event_payload(
                            event=EventType.AGENT_END,
                            key=key,
                            agent_id=node_name,
                            agent_name=agent_name,
                            data=data,
                        )

            while not event_queue.empty():
                try:
                    queued = event_queue.get_nowait()
                except asyncio.QueueEmpty:
                    break
                yield queued

            # Phase 5: 检测中断
            interrupt_event = await self._build_interrupt_event(
                compiled=compiled,
                checkpoint_manager=checkpoint_manager,
                key=key,
                start_time=start_time,
            )
            if interrupt_event is not None:
                yield interrupt_event
                return

            # 完成事件和经验记录
            deliverable_keys = await self._load_deliverable_keys(compiled, checkpoint_manager)
            deliverables = await self._load_deliverables_map(
                session_id=key.session_id,
                deliverable_keys=deliverable_keys,
            )
            await self._event_bus.emit(
                self,
                SessionCompletedEvent(
                    key=key,
                    result=deliverables,
                    duration_ms=now_ms() - start_time,
                    agent_count=agent_count,
                    tool_count=tool_count,
                ),
            )
            if self._experience_learner:
                self._experience_learner.complete_recording(session_id=key.session_id, outcome="success")
            await self._cleanup_session_artifacts(
                session_id=key.session_id,
                compiled=compiled,
                checkpoint_manager=checkpoint_manager,
            )

        except asyncio.CancelledError:
            raise
        except Exception as e:
            # stream 场景下错误通过 SSE 事件返回给调用方；默认不额外刷一遍堆栈，避免重复输出。
            logger.debug("执行失败: %s", e, exc_info=True)
            # 如果异常发生在 compiled.astream() 迭代过程中，event_queue 里可能已经积攒了事件（start/tool 等）。
            # 先把队列里的事件尽量吐出去，避免调用方只能看到最后一条 failed 事件。
            while not event_queue.empty():
                try:
                    queued = event_queue.get_nowait()
                except asyncio.QueueEmpty:
                    break
                yield queued
            error_event = self._build_error_event(e, key=key, start_time=start_time)
            if self._experience_learner:
                self._experience_learner.complete_recording(
                    session_id=key.session_id, outcome="failure", result_summary=str(e)
                )
            await self._cleanup_session_artifacts(
                session_id=key.session_id,
                compiled=compiled,
                checkpoint_manager=checkpoint_manager,
            )
            yield error_event
            return
        finally:
            for event_type, handler in handlers:
                self._event_bus.unregister(event_type, handler)

    async def compact_session(self, session_id: str) -> dict:
        """手动压缩会话（暂不可用，待实现基于 messages 的压缩）"""
        return {"status": "not_implemented", "message": "压缩功能待重构"}

    async def clear_session(self, session_id: str) -> None:
        """清理会话记忆（删除 checkpointer 状态）"""
        key = self._make_key(session_id)
        checkpoint_manager = ContextBuilder.create_checkpoint_manager(
            key=key,
            checkpointer=self._checkpointer,
        )
        await ContextBuilder.delete_checkpoints(checkpoint_manager=checkpoint_manager)

    async def clear_session_store(self, session_id: str) -> None:
        """清理会话交付物（Store）"""
        if not self._store:
            return

        key = self._make_key(session_id)
        compiled = await self._ensure_compiled()
        checkpoint_manager = ContextBuilder.create_checkpoint_manager(
            key=key,
            checkpointer=self._checkpointer,
        )
        deliverable_keys = await self._load_deliverable_keys(compiled, checkpoint_manager)
        await self._clear_store_artifacts(session_id, deliverable_keys)
        await self._clear_deliverable_refs(compiled, checkpoint_manager)


    async def get_session_stats(self, session_id: str) -> dict:
        """获取会话统计"""
        key = self._make_key(session_id)
        compiled = await self._ensure_compiled()
        checkpoint_manager = ContextBuilder.create_checkpoint_manager(
            key=key,
            checkpointer=self._checkpointer,
        )

        try:
            state = await checkpoint_manager.get_state(compiled)
            if not state:
                return {
                    "session_id": session_id,
                    "namespace": self.namespace,
                    "exists": False,
                }

            sb = StateBuilder(state)
            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "exists": True,
                "message_count": len(sb.memory.snapshot()),
                "deliverables_count": len(sb.deliverables.snapshot().keys),
                "active_agent": sb.routing.snapshot().active_agent,
            }

        except Exception as e:
            logger.error(f"获取会话统计失败: {e}")
            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "error": str(e),
            }

    async def get_session_todo(self, session_id: str) -> dict:
        """获取会话 Todo（快照）"""
        key = self._make_key(session_id)
        compiled = await self._ensure_compiled()
        checkpoint_manager = ContextBuilder.create_checkpoint_manager(
            key=key,
            checkpointer=self._checkpointer,
        )

        try:
            state = await checkpoint_manager.get_state(compiled)
            if not state:
                return {
                    "session_id": session_id,
                    "namespace": self.namespace,
                    "exists": False,
                }

            sb = StateBuilder(state)
            todo_data = sb.todo.snapshot().todo
            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "exists": True,
                "todo": todo_data,
            }
        except Exception as e:
            logger.error(f"获取会话 Todo 失败: {e}")
            return {
                "session_id": session_id,
                "namespace": self.namespace,
                "error": str(e),
            }
