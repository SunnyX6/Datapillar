"""
Timeline - 时间线模块

提供完整的任务执行时间线记录和时间旅行能力。

核心概念：
- Timeline: 一次会话的完整事件序列
- TimelineEntry: 单个事件记录
- TimeTravel: 时间旅行 API（回退、分支）

设计原则：
- 每个事件关联 checkpoint_id，支持时间旅行
- session_id + team_id 隔离不同会话和团队
- 与 Checkpointer 深度集成
"""

from __future__ import annotations

import time
import uuid
from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field


def _now_ms() -> int:
    return int(time.time() * 1000)


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class EventType(str, Enum):
    """事件类型"""

    # 会话事件
    SESSION_START = "session_start"
    SESSION_END = "session_end"
    SESSION_RESUME = "session_resume"

    # 用户事件
    USER_MESSAGE = "user_message"
    USER_INTERRUPT = "user_interrupt"

    # Agent 事件
    AGENT_START = "agent_start"
    AGENT_END = "agent_end"
    AGENT_HANDOVER = "agent_handover"

    # 工具事件
    TOOL_CALL = "tool_call"
    TOOL_RESULT = "tool_result"
    TOOL_ERROR = "tool_error"

    # 决策事件
    DECISION = "decision"
    CLARIFICATION = "clarification"

    # 记忆事件
    MEMORY_COMPACT = "memory_compact"
    CHECKPOINT_CREATED = "checkpoint_created"

    # 错误事件
    ERROR = "error"
    RETRY = "retry"


class TimelineEntry(BaseModel):
    """
    时间线条目

    记录单个事件，关联 checkpoint 支持时间旅行。
    """

    # 标识
    id: str = Field(default_factory=_generate_id, description="事件 ID")
    seq: int = Field(default=0, description="序号（在 Timeline 中的位置）")

    # 会话和团队标识
    session_id: str = Field(..., description="会话 ID")
    team_id: str = Field(..., description="团队 ID")
    user_id: str = Field(default="", description="用户 ID")

    # 事件信息
    event_type: EventType = Field(..., description="事件类型")
    agent_id: str | None = Field(default=None, description="相关 Agent ID")
    content: str = Field(default="", description="事件描述")
    metadata: dict[str, Any] = Field(default_factory=dict, description="额外数据")

    # 时间
    timestamp_ms: int = Field(default_factory=_now_ms, description="事件时间")
    duration_ms: int | None = Field(default=None, description="事件耗时")

    # 时间旅行支持
    checkpoint_id: str | None = Field(
        default=None,
        description="关联的检查点 ID（用于时间旅行）",
    )
    parent_checkpoint_id: str | None = Field(
        default=None,
        description="父检查点 ID（用于分支）",
    )
    is_checkpoint: bool = Field(
        default=False,
        description="是否为检查点事件（可回退）",
    )

    def to_display(self) -> str:
        """转换为显示格式"""
        agent_part = f"[{self.agent_id}] " if self.agent_id else ""
        duration_part = f" ({self.duration_ms}ms)" if self.duration_ms else ""
        return f"{agent_part}{self.event_type.value}: {self.content}{duration_part}"


class Timeline(BaseModel):
    """
    时间线

    记录一次会话的完整事件序列，支持时间旅行。
    """

    # 标识
    id: str = Field(default_factory=_generate_id, description="时间线 ID")
    session_id: str = Field(..., description="会话 ID")
    team_id: str = Field(..., description="团队 ID")
    user_id: str = Field(default="", description="用户 ID")

    # 事件序列
    entries: list[TimelineEntry] = Field(default_factory=list, description="事件列表")
    next_seq: int = Field(default=1, description="下一个序号")

    # 检查点索引（用于快速查找）
    checkpoints: list[str] = Field(
        default_factory=list,
        description="检查点事件 ID 列表（按时间顺序）",
    )

    # 时间
    created_at_ms: int = Field(default_factory=_now_ms, description="创建时间")
    updated_at_ms: int = Field(default_factory=_now_ms, description="更新时间")

    # 统计
    total_duration_ms: int = Field(default=0, description="总耗时")
    agent_count: int = Field(default=0, description="参与的 Agent 数量")
    tool_call_count: int = Field(default=0, description="工具调用次数")
    checkpoint_count: int = Field(default=0, description="检查点数量")

    # 状态
    current_checkpoint_id: str | None = Field(
        default=None,
        description="当前检查点 ID（用于时间旅行）",
    )

    def add_entry(
        self,
        event_type: EventType,
        content: str,
        *,
        agent_id: str | None = None,
        metadata: dict | None = None,
        duration_ms: int | None = None,
        checkpoint_id: str | None = None,
        is_checkpoint: bool = False,
    ) -> TimelineEntry:
        """添加事件"""
        entry = TimelineEntry(
            seq=self.next_seq,
            session_id=self.session_id,
            team_id=self.team_id,
            user_id=self.user_id,
            event_type=event_type,
            agent_id=agent_id,
            content=content,
            metadata=metadata or {},
            duration_ms=duration_ms,
            checkpoint_id=checkpoint_id,
            parent_checkpoint_id=self.current_checkpoint_id,
            is_checkpoint=is_checkpoint,
        )

        self.entries.append(entry)
        self.next_seq += 1
        self.updated_at_ms = _now_ms()

        # 更新统计
        if duration_ms:
            self.total_duration_ms += duration_ms
        if event_type == EventType.TOOL_CALL:
            self.tool_call_count += 1

        # 记录检查点
        if is_checkpoint:
            self.checkpoints.append(entry.id)
            self.checkpoint_count += 1
            self.current_checkpoint_id = checkpoint_id

        return entry

    def add_checkpoint(
        self,
        checkpoint_id: str,
        content: str = "检查点",
        *,
        agent_id: str | None = None,
        metadata: dict | None = None,
    ) -> TimelineEntry:
        """添加检查点事件"""
        return self.add_entry(
            event_type=EventType.CHECKPOINT_CREATED,
            content=content,
            agent_id=agent_id,
            metadata=metadata,
            checkpoint_id=checkpoint_id,
            is_checkpoint=True,
        )

    def get_entry(self, entry_id: str) -> TimelineEntry | None:
        """获取指定事件"""
        for entry in self.entries:
            if entry.id == entry_id:
                return entry
        return None

    def get_entries_since(self, timestamp_ms: int) -> list[TimelineEntry]:
        """获取指定时间之后的事件"""
        return [e for e in self.entries if e.timestamp_ms >= timestamp_ms]

    def get_entries_by_agent(self, agent_id: str) -> list[TimelineEntry]:
        """获取指定 Agent 的事件"""
        return [e for e in self.entries if e.agent_id == agent_id]

    def get_entries_by_type(self, event_type: EventType) -> list[TimelineEntry]:
        """获取指定类型的事件"""
        return [e for e in self.entries if e.event_type == event_type]

    def get_checkpoint_entries(self) -> list[TimelineEntry]:
        """获取所有检查点事件"""
        return [e for e in self.entries if e.is_checkpoint]

    def get_latest_checkpoint(self) -> TimelineEntry | None:
        """获取最新的检查点"""
        checkpoint_entries = self.get_checkpoint_entries()
        return checkpoint_entries[-1] if checkpoint_entries else None

    def find_checkpoint_before(self, timestamp_ms: int) -> TimelineEntry | None:
        """查找指定时间之前最近的检查点"""
        checkpoints = [e for e in self.entries if e.is_checkpoint and e.timestamp_ms < timestamp_ms]
        return checkpoints[-1] if checkpoints else None

    def truncate_to_checkpoint(self, checkpoint_id: str) -> int:
        """截断到指定检查点（删除之后的事件）"""
        # 找到检查点位置
        checkpoint_idx = None
        for i, entry in enumerate(self.entries):
            if entry.checkpoint_id == checkpoint_id:
                checkpoint_idx = i
                break

        if checkpoint_idx is None:
            return 0

        # 删除之后的事件
        removed_count = len(self.entries) - checkpoint_idx - 1
        self.entries = self.entries[: checkpoint_idx + 1]

        # 更新检查点列表
        self.checkpoints = [
            eid for eid in self.checkpoints
            if any(e.id == eid for e in self.entries)
        ]

        # 更新状态
        self.next_seq = self.entries[-1].seq + 1 if self.entries else 1
        self.current_checkpoint_id = checkpoint_id
        self.updated_at_ms = _now_ms()

        return removed_count

    def to_prompt(self, max_entries: int = 20) -> str:
        """转换为 prompt 格式"""
        if not self.entries:
            return ""

        lines = ["## 执行时间线"]

        # 取最近的 N 条
        recent_entries = self.entries[-max_entries:]

        for entry in recent_entries:
            lines.append(f"- {entry.to_display()}")

        if len(self.entries) > max_entries:
            lines.insert(1, f"(显示最近 {max_entries} 条，共 {len(self.entries)} 条)")

        return "\n".join(lines)

    def get_stats(self) -> dict:
        """获取统计信息"""
        # 统计各类型事件数量
        type_counts = {}
        for entry in self.entries:
            event_type = entry.event_type.value
            type_counts[event_type] = type_counts.get(event_type, 0) + 1

        # 统计 Agent 参与情况
        agents = set()
        for entry in self.entries:
            if entry.agent_id:
                agents.add(entry.agent_id)

        return {
            "total_entries": len(self.entries),
            "checkpoint_count": self.checkpoint_count,
            "tool_call_count": self.tool_call_count,
            "agent_count": len(agents),
            "agents": list(agents),
            "total_duration_ms": self.total_duration_ms,
            "type_counts": type_counts,
        }


class TimeTravelRequest(BaseModel):
    """时间旅行请求"""

    session_id: str = Field(..., description="会话 ID")
    team_id: str = Field(..., description="团队 ID")
    target_checkpoint_id: str = Field(..., description="目标检查点 ID")
    create_branch: bool = Field(
        default=False,
        description="是否创建分支（而不是覆盖）",
    )
    branch_name: str | None = Field(
        default=None,
        description="分支名称",
    )


class TimeTravelResult(BaseModel):
    """时间旅行结果"""

    success: bool = Field(..., description="是否成功")
    session_id: str = Field(..., description="会话 ID（分支时为新 ID）")
    checkpoint_id: str = Field(..., description="当前检查点 ID")
    removed_entries: int = Field(default=0, description="移除的事件数")
    message: str = Field(default="", description="结果消息")
    is_branch: bool = Field(default=False, description="是否为分支")
    branch_name: str | None = Field(default=None, description="分支名称")


def create_timeline(
    *,
    session_id: str,
    team_id: str,
    user_id: str = "",
) -> Timeline:
    """创建新的时间线"""
    return Timeline(
        session_id=session_id,
        team_id=team_id,
        user_id=user_id,
    )
