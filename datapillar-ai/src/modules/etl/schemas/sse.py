# @author Sunny
# @date 2026-01-27

"""
ETL SSE event model
"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class ActivityEvent(str, Enum):
    """Active event type"""

    LLM = "llm"
    TOOL = "tool"
    INTERRUPT = "interrupt"


class ActivityStatus(str, Enum):
    """active status"""

    RUNNING = "running"
    DONE = "done"
    ERROR = "error"
    WAITING = "waiting"
    ABORTED = "aborted"


class RunStatus(str, Enum):
    """Overall running status"""

    RUNNING = "running"
    DONE = "done"
    ERROR = "error"
    ABORTED = "aborted"


class Activity(BaseModel):
    """single Agent activity status"""

    agent_cn: str = ""
    agent_en: str = ""
    summary: str = ""
    event: ActivityEvent
    event_name: str = ""
    status: ActivityStatus
    interrupt: dict[str, Any] = Field(default_factory=lambda: {"options": []})
    recommendations: list[str] = Field(default_factory=list)


class EtlSseEvent(BaseModel):
    """ETL SSE event"""

    run_id: str
    ts: int
    status: RunStatus
    activity: Activity
    workflow: dict[str, Any] | None = None
