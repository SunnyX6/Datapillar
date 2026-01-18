"""
Agent 异常分类器
"""

from __future__ import annotations

import asyncio

from datapillar_oneagentic.core.status import FailureKind
from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.agent.categories import AgentErrorCategory
from datapillar_oneagentic.exception.agent.errors import AgentError


class AgentErrorClassifier:
    """Agent 异常分类器"""

    @classmethod
    def from_failure(
        cls,
        *,
        agent_id: str,
        error: str,
        failure_kind: FailureKind,
    ) -> AgentError:
        if failure_kind == FailureKind.BUSINESS:
            category = AgentErrorCategory.BUSINESS
            action = RecoveryAction.FAIL_FAST
        else:
            category = AgentErrorCategory.SYSTEM
            action = RecoveryAction.FAIL_FAST

        return AgentError(
            error,
            agent_id=agent_id,
            category=category,
            action=action,
            failure_kind=failure_kind,
        )

    @classmethod
    def from_exception(cls, *, agent_id: str, error: Exception) -> AgentError:
        message = str(error) or "Agent 执行异常"

        if isinstance(error, (asyncio.TimeoutError, TimeoutError)):
            category = AgentErrorCategory.SYSTEM
            action = RecoveryAction.RETRY
            message = "Agent 执行超时"
        elif isinstance(error, (ConnectionError, OSError)):
            category = AgentErrorCategory.DEPENDENCY
            action = RecoveryAction.RETRY
        elif isinstance(error, (ValueError, TypeError)):
            category = AgentErrorCategory.PROTOCOL
            action = RecoveryAction.FAIL_FAST
        else:
            category = AgentErrorCategory.SYSTEM
            action = RecoveryAction.FAIL_FAST

        return AgentError(
            message,
            agent_id=agent_id,
            category=category,
            action=action,
            failure_kind=FailureKind.SYSTEM,
            original=error,
        )
