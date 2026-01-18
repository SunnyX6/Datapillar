"""
Agent 异常定义
"""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.core.status import FailureKind
from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.agent.categories import AgentErrorCategory


class AgentError(Exception):
    """Agent 异常（带分类与恢复动作）"""

    def __init__(
        self,
        message: str,
        *,
        agent_id: str,
        category: AgentErrorCategory,
        action: RecoveryAction,
        failure_kind: FailureKind,
        original: Exception | None = None,
    ) -> None:
        super().__init__(message)
        self.agent_id = agent_id
        self.category = category
        self.action = action
        self.failure_kind = failure_kind
        self.original = original

    def to_dict(self) -> dict[str, Any]:
        """转换为结构化字典（用于日志/SSE）"""
        return {
            "agent_id": self.agent_id,
            "category": self.category.value,
            "action": self.action.value,
            "failure_kind": self.failure_kind.value,
            "message": str(self),
        }
