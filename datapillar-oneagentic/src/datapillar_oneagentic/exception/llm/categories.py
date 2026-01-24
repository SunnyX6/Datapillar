"""
LLM 错误分类
"""

from __future__ import annotations

from enum import Enum


class LLMErrorCategory(str, Enum):
    """LLM 错误分类"""

    # 可重试错误（系统自动处理）
    TRANSIENT = "transient"  # 瞬态错误：网络抖动、临时不可用
    TIMEOUT = "timeout"  # 超时错误：LLM/API 响应超时
    RATE_LIMIT = "rate_limit"  # 限流错误：429、配额耗尽

    # 不可重试错误（需要干预）
    CONTEXT = "context"  # 上下文超限
    INVALID_INPUT = "invalid_input"  # 输入错误：参数格式错误
    STRUCTURED_OUTPUT = "structured_output"  # 结构化输出解析失败
    AUTH_FAILURE = "auth_failure"  # 认证错误：API Key 无效
    NOT_FOUND = "not_found"  # 资源不存在

    # 系统错误
    INTERNAL = "internal"  # 内部错误：代码 bug
    CIRCUIT_OPEN = "circuit_open"  # 熔断中
