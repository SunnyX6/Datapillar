"""
MCP 客户端

基于官方 MCP SDK 实现，提供与 MCP 服务器通信的能力。

官方 SDK: https://github.com/modelcontextprotocol/python-sdk
"""

from __future__ import annotations

import logging
from contextlib import AsyncExitStack
from dataclasses import dataclass, field
from typing import Any, Self

from datapillar_oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerStdio,
    MCPServerHTTP,
    MCPServerSSE,
)

logger = logging.getLogger(__name__)


@dataclass
class ToolAnnotations:
    """
    MCP 工具安全标注

    基于 MCP 规范的 Tool Annotations：
    https://modelcontextprotocol.io/specification

    这些标注帮助 Host 判断工具的风险等级，决定是否需要用户确认。
    """

    destructive_hint: bool | None = None
    """
    破坏性提示：工具可能执行破坏性操作（删除数据、修改状态等）

    - True: 工具可能有破坏性（如 delete_file, drop_table）
    - False: 工具明确无破坏性
    - None: 未知（保守起见视为可能有破坏性）
    """

    idempotent_hint: bool | None = None
    """
    幂等性提示：多次调用是否产生相同结果

    - True: 幂等操作（如 read_file, get_status）
    - False: 非幂等操作（如 send_email, create_record）
    - None: 未知
    """

    open_world_hint: bool | None = None
    """
    开放世界提示：工具是否会与外部系统交互

    - True: 会访问外部网络/服务（如 http_request, send_notification）
    - False: 仅本地操作
    - None: 未知
    """

    read_only_hint: bool | None = None
    """
    只读提示：工具是否只读取数据

    - True: 只读操作（如 list_files, query_database）
    - False: 可能写入数据
    - None: 未知
    """

    @property
    def is_dangerous(self) -> bool:
        """
        判断工具是否危险（需要用户确认）

        危险条件（任一满足）：
        1. 明确标记为破坏性
        2. 明确标记为非幂等
        3. 会访问外部网络
        4. 未标记为只读且未标记为非破坏性
        """
        if self.destructive_hint is True:
            return True
        if self.idempotent_hint is False:
            return True
        if self.open_world_hint is True:
            return True
        if self.read_only_hint is not True and self.destructive_hint is not False:
            return True
        return False


@dataclass
class MCPTool:
    """MCP 工具定义"""

    name: str
    """工具名称"""

    description: str
    """工具描述"""

    input_schema: dict[str, Any] = field(default_factory=dict)
    """输入参数 JSON Schema"""

    annotations: ToolAnnotations = field(default_factory=ToolAnnotations)
    """安全标注"""

    title: str | None = None
    """工具显示标题"""

    mime_type: str | None = None
    """MIME 类型"""


class MCPClient:
    """
    MCP 客户端

    基于官方 MCP SDK，支持 Stdio、HTTP、SSE 三种传输方式。
    使用 async context manager 自动管理连接生命周期。

    使用示例：
    ```python
    config = MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    )

    async with MCPClient(config) as client:
        # 列出工具
        tools = await client.list_tools()
        print(f"可用工具: {[t.name for t in tools]}")

        # 调用工具
        result = await client.call_tool("read_file", {"path": "/tmp/test.txt"})
        print(f"结果: {result}")
    ```
    """

    def __init__(self, config: MCPServerConfig):
        """
        初始化客户端

        参数：
        - config: MCP 服务器配置
        """
        self.config = config
        self._session: Any = None
        self._exit_stack: AsyncExitStack | None = None
        self._connected = False

    async def __aenter__(self) -> Self:
        """进入上下文，建立连接"""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """退出上下文，关闭连接"""
        await self.close()

    async def connect(self) -> None:
        """
        建立与 MCP 服务器的连接

        根据配置类型选择传输方式：
        - MCPServerStdio: 启动子进程，通过 stdin/stdout 通信
        - MCPServerHTTP: HTTP 传输（Streamable HTTP）
        - MCPServerSSE: SSE 传输
        """
        if self._connected:
            return

        try:
            from mcp import ClientSession, StdioServerParameters
            from mcp.client.stdio import stdio_client
        except ImportError:
            raise ImportError(
                "mcp SDK 未安装。请运行: pip install mcp"
            )

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
                raise MCPConnectionError(f"不支持的配置类型: {type(self.config)}")

            self._connected = True
            logger.info(f"MCP 客户端已连接: {self.config}")

        except Exception as e:
            await self._exit_stack.__aexit__(type(e), e, e.__traceback__)
            self._exit_stack = None
            raise MCPConnectionError(f"连接失败: {e}") from e

    async def _connect_stdio(self) -> None:
        """Stdio 传输连接"""
        from mcp import ClientSession, StdioServerParameters
        from mcp.client.stdio import stdio_client

        config: MCPServerStdio = self.config

        server_params = StdioServerParameters(
            command=config.command,
            args=list(config.args) if config.args else [],
            env=dict(config.env) if config.env else None,
        )

        read, write = await self._exit_stack.enter_async_context(
            stdio_client(server_params)
        )

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(read, write)
        )

        await self._session.initialize()

    async def _connect_http(self) -> None:
        """HTTP 传输连接（Streamable HTTP）"""
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client

        config: MCPServerHTTP = self.config

        # 构建 httpx 客户端工厂（传递 headers 和 timeout）
        def httpx_client_factory(**kwargs):
            import httpx
            merged_headers = {**(kwargs.get("headers") or {}), **(config.headers or {})}
            timeout = httpx.Timeout(config.timeout)
            return httpx.AsyncClient(headers=merged_headers, timeout=timeout, **{k: v for k, v in kwargs.items() if k not in ("headers", "timeout")})

        read, write, _ = await self._exit_stack.enter_async_context(
            streamablehttp_client(config.url, httpx_client_factory=httpx_client_factory)
        )

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(read, write)
        )

        await self._session.initialize()

    async def _connect_sse(self) -> None:
        """SSE 传输连接"""
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
        """关闭连接"""
        if self._exit_stack:
            await self._exit_stack.__aexit__(None, None, None)
            self._exit_stack = None

        self._session = None
        self._connected = False

    @property
    def is_connected(self) -> bool:
        """是否已连接"""
        return self._connected

    async def list_tools(self) -> list[MCPTool]:
        """
        列出可用工具

        返回：
        - 工具列表
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

        result = await self._session.list_tools()
        tools = []

        for tool in result.tools:
            # 解析 annotations
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
        调用工具

        参数：
        - name: 工具名称
        - arguments: 工具参数

        返回：
        - 工具执行结果
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

        result = await self._session.call_tool(name, arguments or {})

        # 提取结果内容
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
        列出可用资源

        返回：
        - 资源列表
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

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
        读取资源

        参数：
        - uri: 资源 URI

        返回：
        - 资源内容
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

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
        列出可用提示词模板

        返回：
        - 提示词列表
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

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
        获取提示词

        参数：
        - name: 提示词名称
        - arguments: 提示词参数

        返回：
        - 消息列表
        """
        if not self._session:
            raise MCPConnectionError("客户端未连接")

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


# ==================== 异常定义 ====================


class MCPError(Exception):
    """MCP 基础异常"""
    pass


class MCPConnectionError(MCPError):
    """连接错误"""
    pass


class MCPTimeoutError(MCPError):
    """超时错误"""
    pass
