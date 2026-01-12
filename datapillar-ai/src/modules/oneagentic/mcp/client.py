"""
MCP 客户端

提供与 MCP 服务器通信的能力。
"""

from __future__ import annotations

import asyncio
import json
import logging
import subprocess
from dataclasses import dataclass, field
from typing import Any

from src.modules.oneagentic.mcp.config import (
    MCPServerConfig,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
)

logger = logging.getLogger(__name__)


@dataclass
class MCPTool:
    """MCP 工具描述"""

    name: str
    """工具名称"""

    description: str = ""
    """工具描述"""

    input_schema: dict[str, Any] = field(default_factory=dict)
    """输入参数 schema"""


@dataclass
class MCPResource:
    """MCP 资源描述"""

    uri: str
    """资源 URI"""

    name: str = ""
    """资源名称"""

    description: str = ""
    """资源描述"""

    mime_type: str = ""
    """MIME 类型"""


class MCPClient:
    """
    MCP 客户端

    支持 Stdio、HTTP、SSE 三种传输方式。

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
        self._process: subprocess.Popen | None = None
        self._http_client: Any = None
        self._connected = False
        self._request_id = 0
        self._pending_requests: dict[int, asyncio.Future] = {}
        self._reader_task: asyncio.Task | None = None

    async def __aenter__(self) -> MCPClient:
        """异步上下文管理器入口"""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """异步上下文管理器出口"""
        await self.close()

    @property
    def connected(self) -> bool:
        """是否已连接"""
        return self._connected

    async def connect(self) -> None:
        """连接到 MCP 服务器"""
        if self._connected:
            return

        if isinstance(self.config, MCPServerStdio):
            await self._connect_stdio()
        elif isinstance(self.config, MCPServerHTTP):
            await self._connect_http()
        elif isinstance(self.config, MCPServerSSE):
            await self._connect_sse()
        else:
            raise ValueError(f"不支持的配置类型: {type(self.config)}")

        self._connected = True
        logger.info(f"MCP 客户端已连接: {self._get_server_name()}")

    async def _connect_stdio(self) -> None:
        """Stdio 连接"""
        config: MCPServerStdio = self.config

        cmd = [config.command] + config.args
        env = config.env

        try:
            self._process = await asyncio.create_subprocess_exec(
                *cmd,
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                env=env,
                cwd=config.cwd,
            )

            # 发送初始化请求
            await self._send_jsonrpc(
                "initialize",
                {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "oneagentic",
                        "version": "1.0.0",
                    },
                },
            )

            # 发送 initialized 通知
            await self._send_notification("notifications/initialized", {})

            # 启动读取任务
            self._reader_task = asyncio.create_task(self._read_stdout())

        except Exception as e:
            raise MCPConnectionError(f"Stdio 连接失败: {e}") from e

    async def _connect_http(self) -> None:
        """HTTP 连接"""
        try:
            import httpx

            config: MCPServerHTTP = self.config
            self._http_client = httpx.AsyncClient(
                base_url=config.url,
                headers=config.headers or {},
                timeout=config.timeout,
            )
        except ImportError:
            raise MCPConnectionError("需要安装 httpx: uv add httpx")
        except Exception as e:
            raise MCPConnectionError(f"HTTP 连接失败: {e}") from e

    async def _connect_sse(self) -> None:
        """SSE 连接"""
        # SSE 实现类似 HTTP，但使用 SSE 流
        await self._connect_http()

    async def close(self) -> None:
        """关闭连接"""
        if self._reader_task:
            self._reader_task.cancel()
            try:
                await self._reader_task
            except asyncio.CancelledError:
                pass

        if self._process:
            self._process.terminate()
            try:
                await asyncio.wait_for(self._process.wait(), timeout=5)
            except TimeoutError:
                self._process.kill()
            self._process = None

        if self._http_client:
            await self._http_client.aclose()
            self._http_client = None

        self._connected = False
        logger.info(f"MCP 客户端已断开: {self._get_server_name()}")

    def _get_server_name(self) -> str:
        """获取服务器名称"""
        if isinstance(self.config, MCPServerStdio):
            return f"{self.config.command} {' '.join(self.config.args[:2])}"
        elif isinstance(self.config, (MCPServerHTTP, MCPServerSSE)):
            return self.config.url
        return "unknown"

    async def _send_jsonrpc(self, method: str, params: dict) -> Any:
        """发送 JSON-RPC 请求"""
        self._request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self._request_id,
            "method": method,
            "params": params,
        }

        if isinstance(self.config, MCPServerStdio) and self._process:
            # Stdio 传输
            message = json.dumps(request) + "\n"
            self._process.stdin.write(message.encode())
            await self._process.stdin.drain()

            # 等待响应
            future = asyncio.get_event_loop().create_future()
            self._pending_requests[self._request_id] = future

            try:
                result = await asyncio.wait_for(future, timeout=self.config.timeout)
                return result
            except TimeoutError:
                self._pending_requests.pop(self._request_id, None)
                raise MCPTimeoutError(f"请求超时: {method}")

        elif self._http_client:
            # HTTP 传输
            response = await self._http_client.post("/", json=request)
            response.raise_for_status()
            data = response.json()

            if "error" in data:
                raise MCPError(f"RPC 错误: {data['error']}")

            return data.get("result")

        raise MCPConnectionError("未连接到服务器")

    async def _send_notification(self, method: str, params: dict) -> None:
        """发送通知（无需响应）"""
        notification = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
        }

        if isinstance(self.config, MCPServerStdio) and self._process:
            message = json.dumps(notification) + "\n"
            self._process.stdin.write(message.encode())
            await self._process.stdin.drain()

    async def _read_stdout(self) -> None:
        """读取 stdout 响应"""
        if not self._process or not self._process.stdout:
            return

        try:
            while True:
                line = await self._process.stdout.readline()
                if not line:
                    break

                try:
                    data = json.loads(line.decode())
                    request_id = data.get("id")

                    if request_id and request_id in self._pending_requests:
                        future = self._pending_requests.pop(request_id)

                        if "error" in data:
                            future.set_exception(MCPError(str(data["error"])))
                        else:
                            future.set_result(data.get("result"))

                except json.JSONDecodeError:
                    continue

        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error(f"读取 stdout 失败: {e}")

    async def list_tools(self) -> list[MCPTool]:
        """
        列出可用工具

        返回：
        - 工具列表
        """
        result = await self._send_jsonrpc("tools/list", {})
        tools = []

        for tool_data in result.get("tools", []):
            tools.append(
                MCPTool(
                    name=tool_data.get("name", ""),
                    description=tool_data.get("description", ""),
                    input_schema=tool_data.get("inputSchema", {}),
                )
            )

        return tools

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> Any:
        """
        调用工具

        参数：
        - name: 工具名称
        - arguments: 工具参数

        返回：
        - 工具执行结果
        """
        result = await self._send_jsonrpc(
            "tools/call",
            {
                "name": name,
                "arguments": arguments,
            },
        )

        # 解析结果
        content = result.get("content", [])
        if content and len(content) > 0:
            first_content = content[0]
            if first_content.get("type") == "text":
                return first_content.get("text", "")
            return first_content

        return result

    async def list_resources(self) -> list[MCPResource]:
        """
        列出可用资源

        返回：
        - 资源列表
        """
        result = await self._send_jsonrpc("resources/list", {})
        resources = []

        for res_data in result.get("resources", []):
            resources.append(
                MCPResource(
                    uri=res_data.get("uri", ""),
                    name=res_data.get("name", ""),
                    description=res_data.get("description", ""),
                    mime_type=res_data.get("mimeType", ""),
                )
            )

        return resources

    async def read_resource(self, uri: str) -> Any:
        """
        读取资源

        参数：
        - uri: 资源 URI

        返回：
        - 资源内容
        """
        result = await self._send_jsonrpc("resources/read", {"uri": uri})
        contents = result.get("contents", [])

        if contents and len(contents) > 0:
            first_content = contents[0]
            if "text" in first_content:
                return first_content["text"]
            return first_content

        return result


# === 异常类 ===


class MCPError(Exception):
    """MCP 基础异常"""

    pass


class MCPConnectionError(MCPError):
    """连接错误"""

    pass


class MCPTimeoutError(MCPError):
    """超时错误"""

    pass
