"""
MCP server configuration models.

Security:
    - HTTP/SSE modes validate URLs (SSRF protection)
    - Tool safety checks use MCP Tool Annotations at runtime
    Reference: https://modelcontextprotocol.io/specification
"""

from __future__ import annotations

from dataclasses import dataclass, field

from datapillar_oneagentic.security import validate_url


@dataclass
class MCPServerStdio:
    """
    Stdio MCP server configuration.

    Used to connect to a local MCP server process.

    Security:
        Tool safety is declared by MCP Server via Tool Annotations;
        the framework decides whether to request confirmation at runtime.

    Example:
    ```python
    config = MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
        env={"NODE_ENV": "production"},
    )
    ```
    """

    command: str
    """Command (e.g., python, npx, uvx)."""

    args: list[str] = field(default_factory=list)
    """Command arguments."""

    env: dict[str, str] | None = None
    """Environment variables."""

    cwd: str | None = None
    """Working directory."""

    def __post_init__(self):
        if not self.command:
            raise ValueError("command cannot be empty")


@dataclass
class MCPServerHTTP:
    """
    HTTP MCP server configuration.

    Used to connect to a remote HTTP MCP server.

    Security:
        URLs are validated for SSRF protection; private IPs are blocked by default.

    Example:
    ```python
    config = MCPServerHTTP(
        url="https://api.example.com/mcp",
        headers={"Authorization": "Bearer token"},
    )
    ```
    """

    url: str
    """Server URL."""

    headers: dict[str, str] | None = None
    """HTTP headers."""

    timeout: int = 30
    """Request timeout in seconds."""

    streamable: bool = True
    """Whether to use streaming transport."""

    skip_security_check: bool = False
    """Skip security checks (testing only; forbidden in production)."""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url cannot be empty")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url must be an HTTP(S) URL: {self.url}")

        # SSRF protection check.
        if not self.skip_security_check:
            validate_url(self.url)


@dataclass
class MCPServerSSE:
    """
    SSE MCP server configuration.

    Used to connect to a Server-Sent Events MCP server.

    Security:
        URLs are validated for SSRF protection; private IPs are blocked by default.

    Example:
    ```python
    config = MCPServerSSE(
        url="https://api.example.com/mcp/sse",
        headers={"Authorization": "Bearer token"},
    )
    ```
    """

    url: str
    """Server URL."""

    headers: dict[str, str] | None = None
    """HTTP headers."""

    timeout: int = 30
    """Connection timeout in seconds."""

    skip_security_check: bool = False
    """Skip security checks (testing only; forbidden in production)."""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url cannot be empty")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url must be an HTTP(S) URL: {self.url}")

        # SSRF protection check.
        if not self.skip_security_check:
            validate_url(self.url)


# Config type union
MCPServerConfig = MCPServerStdio | MCPServerHTTP | MCPServerSSE
