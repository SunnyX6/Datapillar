"""
事件基类
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from uuid import uuid4


@dataclass
class BaseEvent:
    """
    事件基类

    所有事件类型都应继承此类。

    属性：
    - event_id: 事件唯一标识
    - timestamp: 事件时间戳
    - metadata: 额外元数据
    """

    event_id: str = field(default_factory=lambda: str(uuid4()))
    """事件唯一标识"""

    timestamp: datetime = field(default_factory=datetime.now)
    """事件时间戳"""

    metadata: dict[str, Any] = field(default_factory=dict)
    """额外元数据"""

    @property
    def event_type(self) -> str:
        """事件类型名称"""
        return self.__class__.__name__

    def to_dict(self) -> dict[str, Any]:
        """转换为字典"""
        return {
            "event_id": self.event_id,
            "event_type": self.event_type,
            "timestamp": self.timestamp.isoformat(),
            "metadata": self.metadata,
        }
