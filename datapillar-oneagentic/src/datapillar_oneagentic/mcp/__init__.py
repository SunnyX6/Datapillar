# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
MCP (Model Context Protocol) client module.

Built on the official MCP SDK and provides MCP server connectivity.

Supported transports:
- Stdio transport (local process)
- HTTP transport (streamable HTTP)
- SSE transport (streaming service)

Example:
```python
from datapillar_oneagentic.mcp import MCPServerStdio, MCPToolkit

# Recommended: use MCPToolkit to manage connection lifecycle
servers = [
    MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    ),
]

async with MCPToolkit(servers) as toolkit:
    tools = toolkit.get_tools()
    # Use tools...
```
"""

from datapillar_oneagentic.mcp.client import (
    MCPClient,
    MCPConnectionError,
    MCPError,
    MCPTimeoutError,
    MCPTool,
    ToolAnnotations,
)
from datapillar_oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
)
from datapillar_oneagentic.mcp.tool import (
    MCPToolkit,
)

__all__ = [
    # Config
    "MCPServerConfig",
    "MCPServerStdio",
    "MCPServerHTTP",
    "MCPServerSSE",
    # Client
    "MCPClient",
    "MCPTool",
    "ToolAnnotations",
    # Errors
    "MCPError",
    "MCPConnectionError",
    "MCPTimeoutError",
    # Toolkit
    "MCPToolkit",
]
