"""
MCP 模块测试
"""

import pytest

from src.modules.oneagentic.mcp import (
    MCPClient,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
)
from src.modules.oneagentic.mcp.client import MCPResource, MCPTool


class TestMCPConfig:
    """MCP 配置测试"""

    def test_stdio_config(self):
        """Stdio 配置"""
        config = MCPServerStdio(
            command="npx",
            args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
        )
        assert config.command == "npx"
        assert len(config.args) == 3
        assert config.timeout == 30

    def test_stdio_config_with_env(self):
        """Stdio 配置带环境变量"""
        config = MCPServerStdio(
            command="python",
            args=["server.py"],
            env={"API_KEY": "test"},
            cwd="/home/user",
        )
        assert config.env == {"API_KEY": "test"}
        assert config.cwd == "/home/user"

    def test_stdio_config_empty_command(self):
        """空命令应报错"""
        with pytest.raises(ValueError, match="command 不能为空"):
            MCPServerStdio(command="")

    def test_http_config(self):
        """HTTP 配置"""
        config = MCPServerHTTP(
            url="https://api.example.com/mcp",
            headers={"Authorization": "Bearer token"},
        )
        assert config.url == "https://api.example.com/mcp"
        assert config.headers == {"Authorization": "Bearer token"}
        assert config.streamable is True

    def test_http_config_invalid_url(self):
        """无效 URL 应报错"""
        with pytest.raises(ValueError, match="必须是 HTTP"):
            MCPServerHTTP(url="ftp://example.com")

    def test_sse_config(self):
        """SSE 配置"""
        config = MCPServerSSE(
            url="https://api.example.com/mcp/sse",
            timeout=60,
        )
        assert config.url == "https://api.example.com/mcp/sse"
        assert config.timeout == 60


class TestMCPDataClasses:
    """MCP 数据类测试"""

    def test_mcp_tool(self):
        """MCP 工具"""
        tool = MCPTool(
            name="read_file",
            description="读取文件内容",
            input_schema={
                "type": "object",
                "properties": {
                    "path": {"type": "string", "description": "文件路径"},
                },
                "required": ["path"],
            },
        )
        assert tool.name == "read_file"
        assert "path" in tool.input_schema["properties"]

    def test_mcp_resource(self):
        """MCP 资源"""
        resource = MCPResource(
            uri="file:///tmp/test.txt",
            name="test.txt",
            description="测试文件",
            mime_type="text/plain",
        )
        assert resource.uri == "file:///tmp/test.txt"
        assert resource.mime_type == "text/plain"


class TestMCPClient:
    """MCP 客户端测试"""

    def test_client_init(self):
        """客户端初始化"""
        config = MCPServerStdio(command="echo", args=["hello"])
        client = MCPClient(config)
        assert not client.connected
        assert client._process is None

    def test_client_get_server_name_stdio(self):
        """获取服务器名称（Stdio）"""
        config = MCPServerStdio(
            command="npx",
            args=["-y", "@modelcontextprotocol/server-filesystem"],
        )
        client = MCPClient(config)
        name = client._get_server_name()
        assert "npx" in name

    def test_client_get_server_name_http(self):
        """获取服务器名称（HTTP）"""
        config = MCPServerHTTP(url="https://api.example.com/mcp")
        client = MCPClient(config)
        name = client._get_server_name()
        assert name == "https://api.example.com/mcp"
