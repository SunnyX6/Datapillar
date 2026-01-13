"""
MCP (Model Context Protocol) 客户端模块

基于官方 MCP SDK 实现，提供 MCP 服务器连接能力。

支持传输方式：
- Stdio 传输（本地进程）
- HTTP 传输（Streamable HTTP）
- SSE 传输（流式服务）

使用示例：
```python
from datapillar_oneagentic.mcp import MCPServerStdio, MCPToolkit

# 推荐：使用 MCPToolkit 管理连接生命周期
servers = [
    MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    ),
]

async with MCPToolkit(servers) as toolkit:
    tools = toolkit.get_tools()
    # 使用工具...
```
"""

from datapillar_oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerStdio,
    MCPServerHTTP,
    MCPServerSSE,
)
from datapillar_oneagentic.mcp.client import (
    MCPClient,
    MCPTool,
    ToolAnnotations,
    MCPError,
    MCPConnectionError,
    MCPTimeoutError,
)
from datapillar_oneagentic.mcp.tool import (
    MCPToolkit,
)

__all__ = [
    # 配置
    "MCPServerConfig",
    "MCPServerStdio",
    "MCPServerHTTP",
    "MCPServerSSE",
    # 客户端
    "MCPClient",
    "MCPTool",
    "ToolAnnotations",
    # 异常
    "MCPError",
    "MCPConnectionError",
    "MCPTimeoutError",
    # 工具包
    "MCPToolkit",
]
