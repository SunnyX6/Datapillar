# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
A2A configuration models.

Defines remote agent connection config and authentication.

Security:
    URLs are validated for SSRF protection; private IPs are blocked by default.
    Reference: https://modelcontextprotocol.io/specification/draft/basic/security_best_practices
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from datapillar_oneagentic.security import validate_url


class AuthType(str, Enum):
    """Authentication type."""

    NONE = "none"
    API_KEY = "api_key"
    BEARER = "bearer"
    OAUTH2 = "oauth2"


@dataclass
class AuthScheme:
    """Base authentication scheme."""

    type: AuthType = AuthType.NONE

    def to_headers(self) -> dict[str, str]:
        """Convert to HTTP headers."""
        return {}


@dataclass
class APIKeyAuth(AuthScheme):
    """API key authentication."""

    type: AuthType = field(default=AuthType.API_KEY, init=False)
    api_key: str = ""
    header_name: str = "X-API-Key"

    def to_headers(self) -> dict[str, str]:
        if not self.api_key:
            return {}
        return {self.header_name: self.api_key}


@dataclass
class BearerAuth(AuthScheme):
    """Bearer token authentication."""

    type: AuthType = field(default=AuthType.BEARER, init=False)
    token: str = ""

    def to_headers(self) -> dict[str, str]:
        if not self.token:
            return {}
        return {"Authorization": f"Bearer {self.token}"}


@dataclass
class A2AConfig:
    """
    A2A remote agent configuration.

    Defines how to connect and call remote A2A agents.

    Attributes:
    - endpoint: Agent endpoint URL (AgentCard URL)
    - auth: Authentication scheme
    - timeout: Request timeout in seconds
    - max_turns: Max conversation turns
    - fail_fast: Fail immediately on connection errors
    - trust_remote_completion: Trust remote completion status

    Security:
        URLs are validated for SSRF protection; private IPs are blocked by default.

    Example:
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent-card.json",
        auth=APIKeyAuth(api_key="sk-xxx"),
        timeout=120,
        max_turns=10,
    )
    ```
    """

    endpoint: str
    """Agent endpoint URL."""

    auth: AuthScheme = field(default_factory=AuthScheme)
    """Authentication scheme."""

    timeout: int = 120
    """Request timeout in seconds."""

    fail_fast: bool = True
    """Fail immediately on connection error; False skips the agent."""

    trust_remote_completion: bool = False
    """Trust remote agent completion status; True returns remote result directly."""

    require_confirmation: bool = True
    """Require user confirmation before calling (external agent behavior is unpredictable)."""

    metadata: dict[str, Any] = field(default_factory=dict)
    """Additional metadata."""

    skip_security_check: bool = False
    """Skip security checks (testing only; forbidden in production)."""

    def __post_init__(self):
        """Validate configuration."""
        if not self.endpoint:
            raise ValueError("endpoint cannot be empty")

        if not self.endpoint.startswith(("http://", "https://")):
            raise ValueError(f"endpoint must be an HTTP(S) URL: {self.endpoint}")

        if self.timeout <= 0:
            raise ValueError(f"timeout must be greater than 0: {self.timeout}")

        # SSRF protection check.
        if not self.skip_security_check:
            validate_url(self.endpoint)
