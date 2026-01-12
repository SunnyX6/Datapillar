"""
MCP (Model Context Protocol) 客户端模块

提供 MCP 服务器连接能力，支持：
- Stdio 传输（本地进程）
- HTTP 传输（远程服务）
- SSE 传输（流式服务）

使用示例：
```python
from src.modules.oneagentic.mcp import MCPClient, MCPServerStdio

# 连接本地 MCP 服务器
config = MCPServerStdio(
    command="npx",
    args=["-y", "@modelcontextprotocol/server-filesystem", "/path/to/dir"],
)

async with MCPClient(config) as client:
    tools = await client.list_tools()
    result = await client.call_tool("read_file", {"path": "/path/to/file.txt"})
```
"""

from src.modules.oneagentic.mcp.client import MCPClient
from src.modules.oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
)
from src.modules.oneagentic.mcp.servers import (
    fetch_server,
    filesystem_server,
    git_server,
    memory_server,
    postgres_server,
    sequential_thinking_server,
    sqlite_server,
)
from src.modules.oneagentic.mcp.tool import create_mcp_tools

__all__ = [
    # 配置
    "MCPServerConfig",
    "MCPServerStdio",
    "MCPServerHTTP",
    "MCPServerSSE",
    # 客户端
    "MCPClient",
    # 工具
    "create_mcp_tools",
    # 预配置服务器
    "filesystem_server",
    "git_server",
    "memory_server",
    "postgres_server",
    "sqlite_server",
    "fetch_server",
    "sequential_thinking_server",
]
