"""
安全模块

提供 MCP 官方安全规范的实现：
- 基于 Tool Annotations 的危险工具识别
- 危险操作前的用户确认（Human-in-the-loop）
- URL 安全校验（SSRF 防护，含 DNS 解析）

参考：https://modelcontextprotocol.io/specification
"""

from datapillar_oneagentic.security.validator import (
    ConfirmationRequest,
    NoConfirmationCallbackError,
    SecurityConfig,
    SecurityError,
    URLNotAllowedError,
    UserRejectedError,
    configure_security,
    get_security_config,
    is_private_ip,
    reset_security_config,
    validate_url,
)

__all__ = [
    "SecurityConfig",
    "ConfirmationRequest",
    "get_security_config",
    "configure_security",
    "reset_security_config",
    "validate_url",
    "is_private_ip",
    "SecurityError",
    "URLNotAllowedError",
    "UserRejectedError",
    "NoConfirmationCallbackError",
]
