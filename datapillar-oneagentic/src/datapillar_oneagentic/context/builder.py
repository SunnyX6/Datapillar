"""
ContextBuilder - 统一的上下文管理器

负责：
- messages 管理（添加、压缩）
- Timeline 记录
- 为 nodes.py 提供统一的 API

设计原则：
- 所有上下文操作都通过 ContextBuilder
- nodes.py 不直接操作 messages 或 Timeline
- 压缩逻辑封装在这里
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import BaseMessage

from datapillar_oneagentic.context.compaction import get_compactor, CompactResult
from datapillar_oneagentic.context.timeline import Timeline

logger = logging.getLogger(__name__)


class ContextBuilder:
    """
    统一的上下文管理器

    管理：
    - messages: LangGraph 的消息列表
    - timeline: 执行时间线
    - 压缩: 自动检查并压缩超长上下文
    """

    def __init__(
        self,
        *,
        session_id: str,
        messages: list[BaseMessage] | None = None,
        timeline: Timeline | None = None,
    ):
        """
        初始化

        Args:
            session_id: 会话 ID
            messages: 初始消息列表
            timeline: 初始时间线
        """
        self.session_id = session_id
        self._messages = list(messages) if messages else []
        self._timeline = timeline or Timeline()
        self._compactor = get_compactor()

    @classmethod
    def from_state(cls, state: dict) -> "ContextBuilder":
        """从 state 创建 ContextBuilder"""
        session_id = state.get("session_id", "")
        messages = list(state.get("messages", []))

        timeline_data = state.get("timeline")
        timeline = Timeline.from_dict(timeline_data) if timeline_data else Timeline()

        return cls(
            session_id=session_id,
            messages=messages,
            timeline=timeline,
        )

    # ========== Messages 操作 ==========

    def add_messages(self, messages: list[BaseMessage]) -> None:
        """添加消息"""
        self._messages.extend(messages)

    def get_messages(self) -> list[BaseMessage]:
        """获取所有消息"""
        return self._messages

    # ========== Timeline 操作 ==========

    def record_event(self, event_data: dict) -> None:
        """记录事件到 Timeline"""
        self._timeline.add_entry_from_dict(event_data)

    def record_events(self, events: list[dict]) -> None:
        """批量记录事件"""
        for event_data in events:
            self._timeline.add_entry_from_dict(event_data)

    # ========== 压缩 ==========

    def needs_compact(self) -> bool:
        """判断是否需要压缩"""
        return self._compactor.needs_compact(self._messages)

    async def compact_if_needed(self) -> CompactResult | None:
        """
        如果需要则执行压缩

        Returns:
            CompactResult 如果执行了压缩，否则 None
        """
        if not self.needs_compact():
            return None

        try:
            compressed_messages, result = await self._compactor.compact(self._messages)
            if result.success and result.tokens_saved > 0:
                self._messages = compressed_messages
                logger.info(
                    f"📦 上下文压缩: {result.removed_count} 条消息 → 摘要，"
                    f"节省 {result.tokens_saved} tokens"
                )
                return result
        except Exception as e:
            logger.warning(f"上下文压缩失败: {e}")

        return None

    # ========== 状态更新 ==========

    def to_state_update(self) -> dict:
        """
        生成 state 更新字典

        Returns:
            包含 messages 和 timeline 的更新字典
        """
        return {
            "messages": self._messages,
            "timeline": self._timeline.to_dict(),
        }

    def get_timeline_update(self) -> dict | None:
        """获取 Timeline 更新（如果有变化）"""
        if self._timeline.entries:
            return self._timeline.to_dict()
        return None
