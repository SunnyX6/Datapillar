"""
异常基础定义

统一 RecoveryAction 等基础概念，供 Agent/LLM 异常共享。
"""

from __future__ import annotations

from enum import Enum


class RecoveryAction(str, Enum):
    """恢复动作"""

    RETRY = "retry"  # 自动重试
    FAIL_FAST = "fail_fast"  # 快速失败（不重试）
    CIRCUIT_BREAK = "circuit_break"  # 熔断
