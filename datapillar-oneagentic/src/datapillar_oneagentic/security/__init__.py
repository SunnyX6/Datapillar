"""
Security module.

Implements MCP security specifications:
- Risky tool detection based on Tool Annotations
- User confirmation before risky actions (human-in-the-loop)
- URL safety validation (SSRF protection with DNS resolution)

Reference: https://modelcontextprotocol.io/specification
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
