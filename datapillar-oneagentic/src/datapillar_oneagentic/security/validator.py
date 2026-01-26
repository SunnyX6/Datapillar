"""
Security validator.

Implements MCP security requirements:
1. Determine tool risk from Tool Annotations
2. Require user confirmation for dangerous tools (human-in-the-loop)
3. URL safety validation (SSRF protection with DNS checks)

Reference: https://modelcontextprotocol.io/specification
"""

from __future__ import annotations

import ipaddress
import logging
import socket
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlparse

logger = logging.getLogger(__name__)


# ==================== Error types ====================


class SecurityError(Exception):
    """Base security error."""

    pass


class UserRejectedError(SecurityError):
    """User rejected execution."""

    pass


class NoConfirmationCallbackError(SecurityError):
    """Confirmation callback is missing but required."""

    pass


class URLNotAllowedError(SecurityError):
    """URL is not allowed."""

    pass


# ==================== Private IP ranges (SSRF protection) ====================

PRIVATE_IP_RANGES = [
    ipaddress.ip_network("127.0.0.0/8"),  # localhost
    ipaddress.ip_network("10.0.0.0/8"),  # Class A private
    ipaddress.ip_network("172.16.0.0/12"),  # Class B private
    ipaddress.ip_network("192.168.0.0/16"),  # Class C private
    ipaddress.ip_network("169.254.0.0/16"),  # Link-local
    ipaddress.ip_network("::1/128"),  # IPv6 localhost
    ipaddress.ip_network("fc00::/7"),  # IPv6 private
    ipaddress.ip_network("fe80::/10"),  # IPv6 link-local
]


# ==================== Confirmation request ====================


@dataclass
class ConfirmationRequest:
    """
    Dangerous operation confirmation request.

    Includes all information needed for a decision.
    """

    # Operation type.
    operation_type: str
    """Operation type: 'mcp_tool' | 'a2a_delegate'."""

    # Basic info.
    name: str
    """Tool/agent name."""

    description: str
    """Tool/agent description."""

    # Call details.
    parameters: dict[str, Any]
    """Full call parameters."""

    # Risk metadata.
    risk_level: str
    """Risk level: 'low' | 'medium' | 'high' | 'critical'."""

    warnings: list[str]
    """Risk warning list."""

    # Source info.
    source: str
    """Source: MCP server address / A2A endpoint."""

    # Extra metadata.
    metadata: dict[str, Any] = field(default_factory=dict)
    """
    Extra metadata, may include:
    - mcp_server: MCP server config
    - tool_annotations: MCP tool annotations
    - a2a_config: A2A config
    - agent_card: A2A Agent Card
    """

    def to_display_string(self) -> str:
        """Generate a human-readable confirmation message."""
        lines = [
            f"{'=' * 50}",
            "DANGEROUS OPERATION CONFIRMATION REQUEST",
            f"{'=' * 50}",
            "",
            f"Operation type: {self.operation_type}",
            f"Name: {self.name}",
            f"Description: {self.description}",
            f"Source: {self.source}",
            f"Risk level: {self.risk_level}",
            "",
            "Parameters:",
        ]

        for key, value in self.parameters.items():
            # Truncate long values.
            value_str = str(value)
            if len(value_str) > 100:
                value_str = value_str[:100] + "..."
            lines.append(f"  {key}: {value_str}")

        if self.warnings:
            lines.append("")
            lines.append("Warnings:")
            for w in self.warnings:
                lines.append(f"  - {w}")

        lines.append(f"{'=' * 50}")
        return "\n".join(lines)


# ==================== Security config ====================


@dataclass
class SecurityConfig:
    """
    Security configuration.

    Attributes:
    - require_confirmation: require user confirmation for dangerous ops (per MCP spec)
    - confirmation_callback: user confirmation callback
    - allow_private_urls: allow access to private URLs
    - require_https: enforce HTTPS (recommended in production)
    - allowed_domains: domain allowlist (empty = no restriction)
    """

    require_confirmation: bool = True
    """Whether user confirmation is required for dangerous operations."""

    confirmation_callback: Callable[[ConfirmationRequest], bool] | None = None
    """
    User confirmation callback.

    Args:
        request: ConfirmationRequest containing full context

    Returns:
        True to confirm, False to reject.

    Example:
    ```python
    def my_callback(request: ConfirmationRequest) -> bool:
        print(request.to_display_string())
        print(f"Risk level: {request.risk_level}")
        print(f"Parameters: {request.parameters}")
        return input("Confirm? (y/N): ").lower() == "y"
    ```
    """

    allow_private_urls: bool = False
    """Whether to allow private URLs (SSRF protection)."""

    require_https: bool = False
    """Whether to enforce HTTPS."""

    allowed_domains: list[str] = field(default_factory=list)
    """Domain allowlist (empty means no restriction)."""


# Global security config.
_security_config: SecurityConfig = SecurityConfig()


def get_security_config() -> SecurityConfig:
    """Return the current security configuration."""
    return _security_config


def configure_security(
    *,
    require_confirmation: bool | None = None,
    confirmation_callback: Callable[[ConfirmationRequest], bool] | None = None,
    allow_private_urls: bool | None = None,
    require_https: bool | None = None,
    allowed_domains: list[str] | None = None,
) -> None:
    """
    Configure security options.

    Args:
        require_confirmation: require user confirmation for dangerous operations
        confirmation_callback: callback receiving ConfirmationRequest
        allow_private_urls: allow access to private URLs
        require_https: enforce HTTPS
        allowed_domains: domain allowlist
    """
    global _security_config

    if require_confirmation is not None:
        _security_config.require_confirmation = require_confirmation
    if confirmation_callback is not None:
        _security_config.confirmation_callback = confirmation_callback
    if allow_private_urls is not None:
        _security_config.allow_private_urls = allow_private_urls
    if require_https is not None:
        _security_config.require_https = require_https
    if allowed_domains is not None:
        _security_config.allowed_domains = allowed_domains


def reset_security_config() -> None:
    """Reset security config to defaults (mainly for tests)."""
    global _security_config
    _security_config = SecurityConfig()


# ==================== URL validation ====================


def _check_private_ip(ip_str: str) -> bool:
    """Check whether an IP address is private."""
    try:
        ip = ipaddress.ip_address(ip_str)
        return any(ip in network for network in PRIVATE_IP_RANGES)
    except ValueError:
        return False


def is_private_ip(hostname: str) -> bool:
    """
    Check whether a hostname resolves to a private IP (with DNS).

    Protects against DNS rebinding:
    - Check literal IP first
    - Resolve DNS and inspect results
    """
    # Check special hostnames.
    lower_hostname = hostname.lower()
    if lower_hostname in ("localhost", "localhost.localdomain", "ip6-localhost"):
        return True

    # Check if the hostname is already an IP.
    if _check_private_ip(hostname):
        return True

    # Resolve DNS to prevent rebinding.
    try:
        addr_info = socket.getaddrinfo(hostname, None, socket.AF_UNSPEC, socket.SOCK_STREAM)
        for _family, _, _, _, sockaddr in addr_info:
            ip_str = sockaddr[0]
            if _check_private_ip(ip_str):
                logger.warning(f"DNS resolved to private IP: {hostname} -> {ip_str}")
                return True
    except socket.gaierror as e:
        # DNS resolution failed; treat as suspicious.
        logger.warning(f"DNS resolution failed: {hostname}, error={e}")
        return True

    return False


def validate_url(url: str) -> None:
    """
    Validate URL safety (SSRF protection).

    Args:
        url: URL to validate

    Raises:
        URLNotAllowedError: URL does not meet security requirements.
    """
    config = get_security_config()
    parsed = urlparse(url)

    # Check scheme.
    if parsed.scheme not in ("http", "https"):
        raise URLNotAllowedError(f"Unsupported scheme: {parsed.scheme}. Only HTTP(S) is allowed.")

    # Enforce HTTPS.
    if config.require_https and parsed.scheme != "https":
        raise URLNotAllowedError(f"HTTPS is required by security config: {url}")

    # Resolve hostname.
    hostname = parsed.hostname
    if not hostname:
        raise URLNotAllowedError(f"Invalid URL: missing hostname: {url}")

    # Check private IP.
    if not config.allow_private_urls and is_private_ip(hostname):
        raise URLNotAllowedError(
            f"Private address is not allowed: {hostname}\n"
            "To allow private URLs, set allow_private_urls=True."
        )

    # Check domain allowlist.
    if config.allowed_domains and not any(
        hostname == domain or hostname.endswith(f".{domain}")
        for domain in config.allowed_domains
    ):
        raise URLNotAllowedError(
            f"Domain is not in allowlist: {hostname}\n"
            f"Allowed domains: {', '.join(config.allowed_domains)}"
        )
