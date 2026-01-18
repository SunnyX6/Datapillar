"""
统一状态定义

目标：让所有执行状态在一个地方集中管理，避免散落字符串。
"""

from __future__ import annotations

from enum import StrEnum
from typing import Final


class ExecutionStatus(StrEnum):
    """执行状态（统一使用）"""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


class AgentStatus(StrEnum):
    """Agent 状态"""

    IDLE = "idle"
    THINKING = "thinking"
    INVOKING = "invoking"  # 调用工具
    WAITING = "waiting"  # 等待用户
    DONE = "done"
    FAILED = "failed"
    ABORTED = "aborted"


class FailureKind(StrEnum):
    """失败类型（仅在 FAILED 时有意义）"""

    BUSINESS = "business"
    SYSTEM = "system"


class ProcessStage(StrEnum):
    """流程阶段（用于 ReAct 等内部阶段，不作为状态）"""

    PLANNING = "planning"
    EXECUTING = "executing"
    REFLECTING = "reflecting"


TERMINAL_STATUSES: Final[set[ExecutionStatus]] = {
    ExecutionStatus.COMPLETED,
    ExecutionStatus.FAILED,
    ExecutionStatus.SKIPPED,
}

def _normalize_status(status: ExecutionStatus | str | None) -> ExecutionStatus | None:
    if status is None:
        return None
    if isinstance(status, ExecutionStatus):
        return status
    if isinstance(status, str):
        try:
            return ExecutionStatus(status)
        except ValueError:
            return None
    return None


def is_terminal(status: ExecutionStatus | str | None) -> bool:
    normalized = _normalize_status(status)
    if normalized is None:
        return False
    return normalized in TERMINAL_STATUSES


def is_failed(status: ExecutionStatus | str | None) -> bool:
    normalized = _normalize_status(status)
    return normalized == ExecutionStatus.FAILED
