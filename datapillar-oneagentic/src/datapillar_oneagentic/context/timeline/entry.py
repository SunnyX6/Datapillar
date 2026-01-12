"""
Context Timeline 子模块 - 时间线条目

记录单个执行事件。
注意：session_id, team_id, user_id 由 Blackboard 管理，不在此存储。
"""

from __future__ import annotations

import time
import uuid
from typing import Any

from pydantic import BaseModel, Field

from datapillar_oneagentic.context.types import EventType, CheckpointType


def _now_ms() -> int:
    return int(time.time() * 1000)


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class TimelineEntry(BaseModel):
    """
    时间线条目

    记录单个事件，关联 checkpoint 支持时间旅行。
    注意：session_id, team_id, user_id 由 Blackboard 管理。
    """

    # 标识
    id: str = Field(default_factory=_generate_id, description="事件 ID")
    seq: int = Field(default=0, description="序号")

    # 事件信息
    event_type: EventType = Field(..., description="事件类型")
    agent_id: str | None = Field(default=None, description="相关 Agent ID")
    content: str = Field(default="", description="事件描述")
    metadata: dict[str, Any] = Field(default_factory=dict, description="额外数据")

    # 时间
    timestamp_ms: int = Field(default_factory=_now_ms, description="事件时间")
    duration_ms: int | None = Field(default=None, description="事件耗时")

    # 检查点支持
    checkpoint_id: str | None = Field(
        default=None,
        description="关联的检查点 ID",
    )
    checkpoint_type: CheckpointType | None = Field(
        default=None,
        description="检查点类型",
    )
    parent_checkpoint_id: str | None = Field(
        default=None,
        description="父检查点 ID（用于分支）",
    )
    is_checkpoint: bool = Field(
        default=False,
        description="是否为检查点事件",
    )

    def to_display(self) -> str:
        """转换为显示格式"""
        agent_part = f"[{self.agent_id}] " if self.agent_id else ""
        duration_part = f" ({self.duration_ms}ms)" if self.duration_ms else ""
        checkpoint_part = " 📌" if self.is_checkpoint else ""
        return f"{agent_part}{self.event_type.value}: {self.content}{duration_part}{checkpoint_part}"

    def to_dict(self) -> dict:
        """转换为字典"""
        return self.model_dump(mode="json")

    @classmethod
    def from_dict(cls, data: dict) -> "TimelineEntry":
        """从字典创建"""
        return cls.model_validate(data)
