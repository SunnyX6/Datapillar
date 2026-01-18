"""
LLM 异常定义
"""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.categories import LLMErrorCategory


class RetryableError(Exception):
    """可重试错误（包装原始异常）"""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class NonRetryableError(Exception):
    """不可重试错误（包装原始异常）"""

    def __init__(self, message: str, original: Exception | None = None):
        super().__init__(message)
        self.original = original


class LLMError(Exception):
    """LLM 异常（带分类与恢复动作）"""

    def __init__(
        self,
        message: str,
        *,
        category: LLMErrorCategory,
        action: RecoveryAction,
        provider: str | None = None,
        model: str | None = None,
        agent_id: str | None = None,
        original: Exception | None = None,
    ) -> None:
        super().__init__(message)
        self.category = category
        self.action = action
        self.provider = provider
        self.model = model
        self.agent_id = agent_id
        self.original = original

    def attach_agent_id(self, agent_id: str) -> None:
        """补充 Agent ID（LLM 层默认未知）"""
        self.agent_id = agent_id

    def to_dict(self) -> dict[str, Any]:
        """转换为结构化字典（用于日志/SSE）"""
        return {
            "category": self.category.value,
            "action": self.action.value,
            "provider": self.provider,
            "model": self.model,
            "agent_id": self.agent_id,
            "message": str(self),
        }
