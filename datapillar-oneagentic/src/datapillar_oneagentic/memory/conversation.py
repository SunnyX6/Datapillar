"""
对话记忆

记录会话中的对话历史，提供给 Agent 作为上下文。

设计原则：
- 简单：只记录关键事件
- 可序列化：存储在 Blackboard 中

注意：
- 压缩功能已移至 SessionMemory
- session_id, user_id 由 Blackboard 管理，不在此存储
"""

from __future__ import annotations

import time
from typing import Literal

from pydantic import BaseModel, Field

from datapillar_oneagentic.providers.token_counter import get_token_counter


EntryType = Literal[
    "user_message",      # 用户消息
    "agent_response",    # Agent 响应
    "agent_handover",    # Agent 交接
    "clarification",     # 澄清对话
    "system_event",      # 系统事件
    "tool_result",       # 工具结果
]


class ConversationEntry(BaseModel):
    """对话记录条目"""

    seq: int = Field(..., description="序号")
    speaker: str = Field(..., description="发言者")
    listener: str = Field(..., description="接收者")
    entry_type: EntryType = Field(..., description="条目类型")
    content: str = Field(..., description="内容")
    timestamp_ms: int = Field(default_factory=lambda: int(time.time() * 1000))

    def to_display(self) -> str:
        """转换为显示格式"""
        type_icons = {
            "user_message": "👤",
            "agent_response": "🤖",
            "agent_handover": "🔄",
            "clarification": "❓",
            "system_event": "⚙️",
            "tool_result": "🔧",
        }
        icon = type_icons.get(self.entry_type, "📝")
        return f"[{self.seq}] {icon} {self.speaker} → {self.listener}: {self.content}"


class ConversationMemory(BaseModel):
    """
    对话记忆

    记录会话中的对话历史。
    压缩功能由 SessionMemory 统一管理。
    注意：session_id, user_id 由 Blackboard 管理。
    """

    # 对话记录
    entries: list[ConversationEntry] = Field(default_factory=list)
    next_seq: int = Field(default=1)

    # Agent 摘要
    agent_summaries: dict[str, str] = Field(default_factory=dict)

    def append(
        self,
        speaker: str,
        listener: str,
        entry_type: EntryType,
        content: str,
        max_length: int = 500,
    ) -> ConversationEntry:
        """添加对话记录"""
        entry = ConversationEntry(
            seq=self.next_seq,
            speaker=speaker,
            listener=listener,
            entry_type=entry_type,
            content=content[:max_length],
        )
        self.entries.append(entry)
        self.next_seq += 1
        return entry

    def update_agent_summary(self, agent_id: str, summary: str) -> None:
        """更新 Agent 摘要"""
        self.agent_summaries[agent_id] = summary[:200]

    def get_recent(self, limit: int = 20) -> list[ConversationEntry]:
        """获取最近的对话记录"""
        return self.entries[-limit:]

    def estimate_tokens(self) -> int:
        """估算当前记忆的 token 数量"""
        text = self.to_prompt()
        if not text:
            return 0
        return get_token_counter().count(text)

    def to_prompt(self, recent_limit: int = 15) -> str:
        """
        转换为 prompt 文本

        格式：
        ## 对话历史
        [1] 👤 user → agent: 内容
        [2] 🤖 agent → user: 内容
        """
        lines = []

        for entry in self.get_recent(recent_limit):
            lines.append(entry.to_display())

        if self.agent_summaries:
            lines.append("")
            lines.append("[Agent 工作摘要]")
            for agent_id, summary in self.agent_summaries.items():
                lines.append(f"  - {agent_id}: {summary}")

        if not lines:
            return ""

        return "## 对话历史\n" + "\n".join(lines)

    def clear(self) -> int:
        """清空对话记录，返回清除的条目数"""
        count = len(self.entries)
        self.entries.clear()
        self.agent_summaries.clear()
        self.next_seq = 1
        return count
