"""
MCP 服务器配置模型
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class MCPServerStdio:
    """
    Stdio MCP 服务器配置

    用于连接本地 MCP 服务器进程。

    使用示例：
    ```python
    config = MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
        env={"NODE_ENV": "production"},
    )
    ```
    """

    command: str
    """命令（如 python, npx, uvx）"""

    args: list[str] = field(default_factory=list)
    """命令参数"""

    env: dict[str, str] | None = None
    """环境变量"""

    cwd: str | None = None
    """工作目录"""

    timeout: int = 30
    """连接超时（秒）"""

    def __post_init__(self):
        if not self.command:
            raise ValueError("command 不能为空")


@dataclass
class MCPServerHTTP:
    """
    HTTP MCP 服务器配置

    用于连接远程 HTTP MCP 服务器。

    使用示例：
    ```python
    config = MCPServerHTTP(
        url="https://api.example.com/mcp",
        headers={"Authorization": "Bearer token"},
    )
    ```
    """

    url: str
    """服务器 URL"""

    headers: dict[str, str] | None = None
    """HTTP 头"""

    timeout: int = 30
    """请求超时（秒）"""

    streamable: bool = True
    """是否使用流式传输"""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url 不能为空")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url 必须是 HTTP(S) URL: {self.url}")


@dataclass
class MCPServerSSE:
    """
    SSE MCP 服务器配置

    用于连接 Server-Sent Events MCP 服务器。

    使用示例：
    ```python
    config = MCPServerSSE(
        url="https://api.example.com/mcp/sse",
        headers={"Authorization": "Bearer token"},
    )
    ```
    """

    url: str
    """服务器 URL"""

    headers: dict[str, str] | None = None
    """HTTP 头"""

    timeout: int = 30
    """连接超时（秒）"""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url 不能为空")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url 必须是 HTTP(S) URL: {self.url}")


# 配置类型联合
MCPServerConfig = MCPServerStdio | MCPServerHTTP | MCPServerSSE
