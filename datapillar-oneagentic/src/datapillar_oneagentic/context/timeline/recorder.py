"""
Timeline Recorder

将 EventBus 事件记录到 Timeline。

使用 SessionKey 作为 key 存储事件，在 agent_node 执行完成后刷新到 Timeline。
"""

from __future__ import annotations

import logging
import threading
from collections import defaultdict
from typing import Any

from datapillar_oneagentic.events.constants import EventType
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.events.types import (
    AgentCompletedEvent,
    AgentFailedEvent,
    AgentStartedEvent,
    DelegationCompletedEvent,
    DelegationStartedEvent,
    SessionCompletedEvent,
    SessionStartedEvent,
)

logger = logging.getLogger(__name__)


class TimelineRecorder:
    """
    Timeline 记录器

    将 EventBus 事件记录到内存缓冲区，按 SessionKey 分组。
    在 agent_node 执行完成后，调用 flush() 将事件刷新到 Timeline。

    使用示例：
    ```python
    recorder = TimelineRecorder(event_bus)
    recorder.register()  # 注册 EventBus 处理器

    # 事件自动记录到缓冲区...

    # 获取并清空指定 session 的事件
    key = SessionKey(namespace="etl", session_id="abc123")
    entries = recorder.flush(key)
    for entry in entries:
        timeline.add_entry_from_dict(entry)
    ```
    """

    def __init__(self, event_bus: EventBus) -> None:
        """初始化"""
        self._event_bus = event_bus
        self._buffer: dict[str, list[dict]] = defaultdict(list)
        self._buffer_lock = threading.Lock()
        self._registered = False

    def register(self) -> None:
        """注册 EventBus 处理器"""
        if self._registered:
            return

        # Agent 事件
        self._event_bus.register(AgentStartedEvent, self._on_agent_started)
        self._event_bus.register(AgentCompletedEvent, self._on_agent_completed)
        self._event_bus.register(AgentFailedEvent, self._on_agent_failed)

        # 会话事件
        self._event_bus.register(SessionStartedEvent, self._on_session_started)
        self._event_bus.register(SessionCompletedEvent, self._on_session_completed)

        # 委派事件
        self._event_bus.register(DelegationStartedEvent, self._on_delegation_started)
        self._event_bus.register(DelegationCompletedEvent, self._on_delegation_completed)

        self._registered = True

    def _record(self, key: SessionKey | None, entry_data: dict) -> None:
        """记录事件到缓冲区"""
        if not key:
            return
        with self._buffer_lock:
            self._buffer[str(key)].append(entry_data)

    def flush(self, key: SessionKey) -> list[dict]:
        """获取并清空指定 session 的事件"""
        with self._buffer_lock:
            entries = self._buffer.pop(str(key), [])
        return entries

    def peek(self, key: SessionKey) -> list[dict]:
        """查看指定 session 的事件（不清空）"""
        with self._buffer_lock:
            return list(self._buffer.get(str(key), []))

    def clear(self, key: SessionKey | None = None) -> None:
        """清空缓冲区"""
        with self._buffer_lock:
            if key:
                self._buffer.pop(str(key), None)
            else:
                self._buffer.clear()

    # === EventBus 处理器 ===

    def _on_agent_started(self, source: Any, event: AgentStartedEvent) -> None:
        """Agent 开始"""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_START.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] 开始执行",
                "metadata": {"query": event.query[:200] if event.query else ""},
            },
        )

    def _on_agent_completed(self, source: Any, event: AgentCompletedEvent) -> None:
        """Agent 完成"""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_END.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] 执行完成",
                "duration_ms": int(event.duration_ms),
                "metadata": {"result": str(event.result)[:200] if event.result else ""},
            },
        )

    def _on_agent_failed(self, source: Any, event: AgentFailedEvent) -> None:
        """Agent 失败"""
        self._record(
            event.key,
            {
                "event_type": EventType.AGENT_FAILED.value,
                "agent_id": event.agent_id,
                "content": f"Agent [{event.agent_name}] 执行失败: {event.error}",
                "metadata": {"error_type": event.error_type, "error": event.error},
            },
        )

    def _on_session_started(self, source: Any, event: SessionStartedEvent) -> None:
        """会话开始"""
        self._record(
            event.key,
            {
                "event_type": EventType.SESSION_START.value,
                "content": f"会话开始: {event.query[:100] if event.query else ''}",
                "metadata": {"query": event.query},
            },
        )

    def _on_session_completed(self, source: Any, event: SessionCompletedEvent) -> None:
        """会话完成"""
        self._record(
            event.key,
            {
                "event_type": EventType.SESSION_END.value,
                "content": "会话完成",
                "duration_ms": int(event.duration_ms),
                "metadata": {
                    "agent_count": event.agent_count,
                    "tool_count": event.tool_count,
                },
            },
        )

    def _on_delegation_started(self, source: Any, event: DelegationStartedEvent) -> None:
        """委派开始"""
        self._record(
            event.key,
            {
                "event_type": EventType.DELEGATION_START.value,
                "agent_id": event.from_agent_id,
                "content": f"委派任务给 [{event.to_agent_id}]: {event.task[:100] if event.task else ''}",
                "metadata": {
                    "from_agent_id": event.from_agent_id,
                    "to_agent_id": event.to_agent_id,
                    "task": event.task,
                    "is_a2a": event.is_a2a,
                },
            },
        )

    def _on_delegation_completed(self, source: Any, event: DelegationCompletedEvent) -> None:
        """委派完成"""
        self._record(
            event.key,
            {
                "event_type": EventType.DELEGATION_END.value,
                "agent_id": event.from_agent_id,
                "content": f"委派完成: [{event.to_agent_id}]",
                "duration_ms": int(event.duration_ms),
                "metadata": {
                    "from_agent_id": event.from_agent_id,
                    "to_agent_id": event.to_agent_id,
                },
            },
        )
