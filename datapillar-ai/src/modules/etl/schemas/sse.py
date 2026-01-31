# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
ETL SSE 事件模型
"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class ActivityEvent(str, Enum):
    """活动事件类型"""

    LLM = "llm"
    TOOL = "tool"
    INTERRUPT = "interrupt"


class ActivityStatus(str, Enum):
    """活动状态"""

    RUNNING = "running"
    DONE = "done"
    ERROR = "error"
    WAITING = "waiting"
    ABORTED = "aborted"


class RunStatus(str, Enum):
    """运行总状态"""

    RUNNING = "running"
    DONE = "done"
    ERROR = "error"
    ABORTED = "aborted"


class Activity(BaseModel):
    """单个 Agent 的活动状态"""

    agent_cn: str = ""
    agent_en: str = ""
    summary: str = ""
    event: ActivityEvent
    event_name: str = ""
    status: ActivityStatus
    interrupt: dict[str, Any] = Field(default_factory=lambda: {"options": []})
    recommendations: list[str] = Field(default_factory=list)


class EtlSseEvent(BaseModel):
    """ETL SSE 事件"""

    run_id: str
    ts: int
    status: RunStatus
    activity: Activity
    workflow: dict[str, Any] | None = None
