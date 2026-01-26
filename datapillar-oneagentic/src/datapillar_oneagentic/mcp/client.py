"""
MCP client.

Implemented on top of the official MCP SDK for server communication.

Official SDK: https://github.com/modelcontextprotocol/python-sdk
"""

from __future__ import annotations

import logging
from contextlib import AsyncExitStack
from dataclasses import dataclass, field
from typing import Any, Self

from datapillar_oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
)

logger = logging.getLogger(__name__)


@dataclass
class ToolAnnotations:
    """
    MCP tool safety annotations.

    Based on MCP Tool Annotations:
    https://modelcontextprotocol.io/specification

    These hints help the host decide tool risk and whether user confirmation is required.
    """

    destructive_hint: bool | None = None
    """
    Destructive hint: whether the tool may perform destructive operations.

    - True: potentially destructive (e.g., delete_file, drop_table)
    - False: explicitly non-destructive
    - None: unknown (treated as potentially destructive)
    """

    idempotent_hint: bool | None = None
    """
    Idempotency hint: whether repeated calls yield the same result.

    - True: idempotent (e.g., read_file, get_status)
    - False: non-idempotent (e.g., send_email, create_record)
    - None: unknown
    """

    open_world_hint: bool | None = None
    """
    Open-world hint: whether the tool interacts with external systems.

    - True: accesses external network/services (e.g., http_request, send_notification)
    - False: local-only
    - None: unknown
    """

    read_only_hint: bool | None = None
    """
    Read-only hint: whether the tool only reads data.

    - True: read-only (e.g., list_files, query_database)
    - False: may write data
    - None: unknown
    """

    @property
    def is_dangerous(self) -> bool:
        """
        Determine whether the tool is dangerous (requires user confirmation).

        Dangerous if any of the following are true:
        1. Marked as destructive
        2. Marked as non-idempotent
        3. Accesses the external world
        4. Not read-only and not explicitly non-destructive
        """
        if self.destructive_hint is True:
            return True
        if self.idempotent_hint is False:
            return True
        if self.open_world_hint is True:
            return True
        return self.read_only_hint is not True and self.destructive_hint is not False


@dataclass
class MCPTool:
    """MCP tool definition."""

    name: str
    """Tool name."""

    description: str
    """Tool description."""

    input_schema: dict[str, Any] = field(default_factory=dict)
    """Input JSON schema."""

    annotations: ToolAnnotations = field(default_factory=ToolAnnotations)
    """Safety annotations."""

    title: str | None = None
    """Tool display title."""

    mime_type: str | None = None
    """MIME type."""


class MCPClient:
    """
    MCP client.

    Based on the official MCP SDK. Supports Stdio, HTTP, and SSE transports.
    Uses an async context manager to manage connection lifecycle.

    Example:
    ```python
    config = MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    )

    async with MCPClient(config) as client:
        # List tools
        tools = await client.list_tools()
        print(f"Available tools: {[t.name for t in tools]}")

        # Call a tool
        result = await client.call_tool("read_file", {"path": "/tmp/test.txt"})
        print(f"Result: {result}")
    ```
    """

    def __init__(self, config: MCPServerConfig):
        """
        Initialize the client.

        Args:
            config: MCP server configuration
        """
        self.config = config
        self._session: Any = None
        self._exit_stack: AsyncExitStack | None = None
        self._connected = False

    async def __aenter__(self) -> Self:
        """Enter context and establish connection."""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """Exit context and close connection."""
        await self.close()

    async def connect(self) -> None:
        """
        Establish a connection to the MCP server.

        Transport is selected based on config:
        - MCPServerStdio: spawn subprocess with stdin/stdout
        - MCPServerHTTP: HTTP transport (streamable HTTP)
        - MCPServerSSE: SSE transport
        """
        if self._connected:
            return

        import importlib.util

        if importlib.util.find_spec("mcp") is None:
            raise ImportError("The mcp SDK is not installed. Run: pip install mcp")

        self._exit_stack = AsyncExitStack()
        await self._exit_stack.__aenter__()

        try:
            if isinstance(self.config, MCPServerStdio):
                await self._connect_stdio()
            elif isinstance(self.config, MCPServerHTTP):
                await self._connect_http()
            elif isinstance(self.config, MCPServerSSE):
                await self._connect_sse()
            else:
                raise MCPConnectionError(f"Unsupported config type: {type(self.config)}")

            self._connected = True
            logger.info(f"MCP client connected: {self.config}")

        except Exception as e:
            await self._exit_stack.__aexit__(type(e), e, e.__traceback__)
            self._exit_stack = None
            raise MCPConnectionError(f"Connection failed: {e}") from e

    async def _connect_stdio(self) -> None:
        """Stdio transport connection."""
        from mcp import ClientSession, StdioServerParameters
        from mcp.client.stdio import stdio_client

        config: MCPServerStdio = self.config

        server_params = StdioServerParameters(
            command=config.command,
            args=list(config.args) if config.args else [],
            env=dict(config.env) if config.env else None,
            cwd=config.cwd,
        )

        read, write = await self._exit_stack.enter_async_context(
            stdio_client(server_params)
        )

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(read, write)
        )

        await self._session.initialize()

    async def _connect_http(self) -> None:
        """HTTP transport connection (streamable HTTP)."""
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client

        config: MCPServerHTTP = self.config

        # Build httpx client factory with headers and timeout.
        def httpx_client_factory(**kwargs):
            import httpx
            merged_headers = {**(kwargs.get("headers") or {}), **(config.headers or {})}
            timeout = httpx.Timeout(config.timeout)
            return httpx.AsyncClient(
                headers=merged_headers,
                timeout=timeout,
                **{k: v for k, v in kwargs.items() if k not in ("headers", "timeout")},
            )

        read, write, _ = await self._exit_stack.enter_async_context(
            streamablehttp_client(config.url, httpx_client_factory=httpx_client_factory)
        )

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(read, write)
        )

        await self._session.initialize()

    async def _connect_sse(self) -> None:
        """SSE transport connection."""
        from mcp import ClientSession
        from mcp.client.sse import sse_client

        config: MCPServerSSE = self.config

        read, write = await self._exit_stack.enter_async_context(
            sse_client(
                config.url,
                headers=config.headers,
                timeout=float(config.timeout),
            )
        )

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(read, write)
        )

        await self._session.initialize()

    async def close(self) -> None:
        """Close the connection."""
        if self._exit_stack:
            await self._exit_stack.__aexit__(None, None, None)
            self._exit_stack = None

        self._session = None
        self._connected = False

    @property
    def is_connected(self) -> bool:
        """Return whether the client is connected."""
        return self._connected

    async def list_tools(self) -> list[MCPTool]:
        """
        List available tools.

        Returns:
            A list of tools.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        result = await self._session.list_tools()
        tools = []

        for tool in result.tools:
            # Parse annotations.
            annotations = ToolAnnotations()
            if hasattr(tool, 'annotations') and tool.annotations:
                ann = tool.annotations
                annotations = ToolAnnotations(
                    destructive_hint=getattr(ann, 'destructiveHint', None),
                    idempotent_hint=getattr(ann, 'idempotentHint', None),
                    open_world_hint=getattr(ann, 'openWorldHint', None),
                    read_only_hint=getattr(ann, 'readOnlyHint', None),
                )

            tools.append(MCPTool(
                name=tool.name,
                description=tool.description or "",
                input_schema=tool.inputSchema if hasattr(tool, 'inputSchema') else {},
                annotations=annotations,
                title=getattr(tool, 'title', None),
            ))

        return tools

    async def call_tool(self, name: str, arguments: dict[str, Any] | None = None) -> Any:
        """
        Call a tool.

        Args:
            name: tool name
            arguments: tool arguments

        Returns:
            Tool execution result.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        result = await self._session.call_tool(name, arguments or {})

        # Extract result content.
        if result.content:
            contents = []
            for item in result.content:
                if hasattr(item, 'text'):
                    contents.append(item.text)
                elif hasattr(item, 'data'):
                    contents.append(item.data)
                else:
                    contents.append(str(item))

            if len(contents) == 1:
                return contents[0]
            return contents

        return result.structuredContent if hasattr(result, 'structuredContent') else None

    async def list_resources(self) -> list[dict[str, Any]]:
        """
        List available resources.

        Returns:
            A list of resources.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        result = await self._session.list_resources()
        return [
            {
                "uri": str(r.uri),
                "name": r.name,
                "description": getattr(r, 'description', None),
                "mimeType": getattr(r, 'mimeType', None),
            }
            for r in result.resources
        ]

    async def read_resource(self, uri: str) -> Any:
        """
        Read a resource.

        Args:
            uri: resource URI

        Returns:
            Resource content.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        from pydantic import AnyUrl

        result = await self._session.read_resource(AnyUrl(uri))

        if result.contents:
            contents = []
            for item in result.contents:
                if hasattr(item, 'text'):
                    contents.append(item.text)
                elif hasattr(item, 'blob'):
                    contents.append(item.blob)
                else:
                    contents.append(str(item))

            if len(contents) == 1:
                return contents[0]
            return contents

        return None

    async def list_prompts(self) -> list[dict[str, Any]]:
        """
        List available prompt templates.

        Returns:
            A list of prompts.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        result = await self._session.list_prompts()
        return [
            {
                "name": p.name,
                "description": getattr(p, 'description', None),
                "arguments": [
                    {
                        "name": arg.name,
                        "description": getattr(arg, 'description', None),
                        "required": getattr(arg, 'required', False),
                    }
                    for arg in (p.arguments or [])
                ],
            }
            for p in result.prompts
        ]

    async def get_prompt(self, name: str, arguments: dict[str, str] | None = None) -> list[dict[str, Any]]:
        """
        Fetch a prompt by name.

        Args:
            name: prompt name
            arguments: prompt arguments

        Returns:
            A list of messages.
        """
        if not self._session:
            raise MCPConnectionError("Client is not connected")

        result = await self._session.get_prompt(name, arguments or {})
        return [
            {
                "role": msg.role,
                "content": msg.content.text if hasattr(msg.content, 'text') else str(msg.content),
            }
            for msg in result.messages
        ]

    def __repr__(self) -> str:
        status = "connected" if self._connected else "disconnected"
        return f"MCPClient({self.config}, {status})"


# ==================== Error types ====================


class MCPError(Exception):
    """Base MCP error."""
    pass


class MCPConnectionError(MCPError):
    """Connection error."""
    pass


class MCPTimeoutError(MCPError):
    """Timeout error."""
    pass
