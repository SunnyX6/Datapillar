"""
MCP 服务器配置模型

安全说明：
    - HTTP/SSE 模式会进行 URL 校验（SSRF 防护）
    - 工具安全校验在运行时基于 MCP Tool Annotations 进行
    参考：https://modelcontextprotocol.io/specification
"""

from __future__ import annotations

from dataclasses import dataclass, field

from datapillar_oneagentic.security import validate_url


@dataclass
class MCPServerStdio:
    """
    Stdio MCP 服务器配置

    用于连接本地 MCP 服务器进程。

    安全说明：
        工具的安全性由 MCP Server 通过 Tool Annotations 声明，
        框架在运行时根据 annotations 判断是否需要用户确认。

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

    def __post_init__(self):
        if not self.command:
            raise ValueError("command 不能为空")


@dataclass
class MCPServerHTTP:
    """
    HTTP MCP 服务器配置

    用于连接远程 HTTP MCP 服务器。

    安全说明：
        URL 会进行 SSRF 防护校验，默认禁止访问内网地址。

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

    skip_security_check: bool = False
    """跳过安全检查（仅用于测试，生产环境禁止）"""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url 不能为空")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url 必须是 HTTP(S) URL: {self.url}")

        # SSRF 防护校验
        if not self.skip_security_check:
            validate_url(self.url)


@dataclass
class MCPServerSSE:
    """
    SSE MCP 服务器配置

    用于连接 Server-Sent Events MCP 服务器。

    安全说明：
        URL 会进行 SSRF 防护校验，默认禁止访问内网地址。

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

    skip_security_check: bool = False
    """跳过安全检查（仅用于测试，生产环境禁止）"""

    def __post_init__(self):
        if not self.url:
            raise ValueError("url 不能为空")

        if not self.url.startswith(("http://", "https://")):
            raise ValueError(f"url 必须是 HTTP(S) URL: {self.url}")

        # SSRF 防护校验
        if not self.skip_security_check:
            validate_url(self.url)


# 配置类型联合
MCPServerConfig = MCPServerStdio | MCPServerHTTP | MCPServerSSE
