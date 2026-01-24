"""
Context Timeline 子模块 - 时间线管理

记录执行事件序列，支持时间旅行。
注意：namespace, session_id 由 Blackboard 管理，不在此存储。
"""

from __future__ import annotations

import uuid

from pydantic import BaseModel, Field

from datapillar_oneagentic.context.timeline.entry import TimelineEntry
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.context.checkpoint.types import CheckpointType
from datapillar_oneagentic.events.constants import EventType


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class Timeline(BaseModel):
    """
    时间线

    记录一次会话的完整事件序列，支持时间旅行。
    注意：namespace, session_id 由 Blackboard 管理。
    """

    # 标识
    id: str = Field(default_factory=_generate_id, description="时间线 ID")

    # 事件序列
    entries: list[TimelineEntry] = Field(default_factory=list, description="事件列表")
    next_seq: int = Field(default=1, description="下一个序号")

    # 检查点索引
    checkpoint_ids: list[str] = Field(
        default_factory=list,
        description="检查点 ID 列表（按时间顺序）",
    )

    # 当前状态
    current_checkpoint_id: str | None = Field(
        default=None,
        description="当前检查点 ID",
    )

    # 统计
    total_duration_ms: int = Field(default=0, description="总耗时")

    def add_entry(
        self,
        event_type: EventType,
        content: str,
        *,
        agent_id: str | None = None,
        metadata: dict | None = None,
        duration_ms: int | None = None,
        checkpoint_id: str | None = None,
        checkpoint_type: CheckpointType | None = None,
        is_checkpoint: bool = False,
    ) -> TimelineEntry:
        """添加事件"""
        entry = TimelineEntry(
            seq=self.next_seq,
            event_type=event_type,
            agent_id=agent_id,
            content=content,
            metadata=metadata or {},
            duration_ms=duration_ms,
            checkpoint_id=checkpoint_id,
            checkpoint_type=checkpoint_type,
            parent_checkpoint_id=self.current_checkpoint_id,
            is_checkpoint=is_checkpoint,
        )

        self.entries.append(entry)
        self.next_seq += 1

        if duration_ms:
            self.total_duration_ms += duration_ms

        if is_checkpoint and checkpoint_id:
            self.checkpoint_ids.append(checkpoint_id)
            self.current_checkpoint_id = checkpoint_id

        return entry

    def add_checkpoint(
        self,
        checkpoint_id: str,
        content: str = "检查点",
        *,
        checkpoint_type: CheckpointType = CheckpointType.AUTO,
        agent_id: str | None = None,
        metadata: dict | None = None,
    ) -> TimelineEntry:
        """添加检查点事件"""
        return self.add_entry(
            event_type=EventType.CHECKPOINT_CREATE,
            content=content,
            agent_id=agent_id,
            metadata=metadata,
            checkpoint_id=checkpoint_id,
            checkpoint_type=checkpoint_type,
            is_checkpoint=True,
        )

    def get_entry(self, entry_id: str) -> TimelineEntry | None:
        """获取指定事件"""
        for entry in self.entries:
            if entry.id == entry_id:
                return entry
        return None

    def get_entry_by_checkpoint(self, checkpoint_id: str) -> TimelineEntry | None:
        """获取指定检查点的事件"""
        for entry in self.entries:
            if entry.checkpoint_id == checkpoint_id:
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
        checkpoints = [
            e for e in self.entries
            if e.is_checkpoint and e.timestamp_ms < timestamp_ms
        ]
        return checkpoints[-1] if checkpoints else None

    def truncate_to_checkpoint(self, checkpoint_id: str) -> int:
        """截断到指定检查点（删除之后的事件）"""
        checkpoint_idx = None
        for i, entry in enumerate(self.entries):
            if entry.checkpoint_id == checkpoint_id:
                checkpoint_idx = i
                break

        if checkpoint_idx is None:
            return 0

        removed_count = len(self.entries) - checkpoint_idx - 1
        self.entries = self.entries[: checkpoint_idx + 1]

        # 更新检查点列表
        self.checkpoint_ids = [
            cid for cid in self.checkpoint_ids
            if any(e.checkpoint_id == cid for e in self.entries)
        ]

        self.next_seq = self.entries[-1].seq + 1 if self.entries else 1
        self.current_checkpoint_id = checkpoint_id

        return removed_count

    def to_prompt(self, max_entries: int = 20) -> str:
        """转换为 prompt 格式"""
        if not self.entries:
            return ""

        recent_entries = self.entries[-max_entries:]
        lines: list[str] = []
        if len(self.entries) > max_entries:
            lines.append(f"(showing last {max_entries} of {len(self.entries)})")
        for entry in recent_entries:
            lines.append(f"- {entry.to_display()}")

        body = "\n".join(lines).strip()
        return format_markdown(
            title="Execution Timeline",
            sections=[("Timeline", body)],
        )

    def get_stats(self) -> dict:
        """获取统计信息"""
        type_counts: dict[str, int] = {}
        agents: set[str] = set()

        for entry in self.entries:
            event_type = entry.event_type.value
            type_counts[event_type] = type_counts.get(event_type, 0) + 1
            if entry.agent_id:
                agents.add(entry.agent_id)

        return {
            "total_entries": len(self.entries),
            "checkpoint_count": len(self.checkpoint_ids),
            "agent_count": len(agents),
            "agents": list(agents),
            "total_duration_ms": self.total_duration_ms,
            "type_counts": type_counts,
        }

    def add_entry_from_dict(self, data: dict) -> TimelineEntry:
        """从字典添加事件（用于 TimelineRecorder 刷新）"""
        event_type = data.get("event_type")
        if isinstance(event_type, str):
            event_type = EventType.from_string(event_type)

        return self.add_entry(
            event_type=event_type,
            content=data.get("content", ""),
            agent_id=data.get("agent_id"),
            metadata=data.get("metadata"),
            duration_ms=data.get("duration_ms"),
            checkpoint_id=data.get("checkpoint_id"),
            checkpoint_type=data.get("checkpoint_type"),
            is_checkpoint=data.get("is_checkpoint", False),
        )

    def to_dict(self) -> dict:
        """序列化为字典"""
        return self.model_dump(mode="json")

    @classmethod
    def from_dict(cls, data: dict) -> Timeline:
        """从字典恢复"""
        return cls.model_validate(data)
