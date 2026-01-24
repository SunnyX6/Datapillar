"""
MCP 工具集成

将 MCP 工具转换为 LangChain 工具，并集成安全校验。

安全机制：
- 根据 MCP 工具的 annotations 判断工具是否危险
- 危险工具调用前需要用户确认（遵循 MCP 规范）

生命周期管理：
- 使用 MCPToolkit 管理客户端连接池
- 支持 async context manager 自动清理资源
"""

from __future__ import annotations

import logging
from contextlib import AsyncExitStack
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

from datapillar_oneagentic.mcp.client import MCPClient, MCPTool
from datapillar_oneagentic.mcp.config import MCPServerConfig
from datapillar_oneagentic.security import (
    ConfirmationRequest,
    NoConfirmationCallbackError,
    UserRejectedError,
    get_security_config,
)

logger = logging.getLogger(__name__)


def _json_schema_to_pydantic_field(
    name: str,
    schema: dict[str, Any],
    required: bool = False,
) -> tuple[type, Any]:
    """将 JSON Schema 字段转换为 Pydantic 字段"""
    json_type = schema.get("type", "string")
    description = schema.get("description", "")
    default = ... if required else None

    type_mapping = {
        "string": str,
        "integer": int,
        "number": float,
        "boolean": bool,
        "array": list,
        "object": dict,
    }

    python_type = type_mapping.get(json_type, Any)

    if not required:
        python_type = python_type | None

    return (python_type, Field(default=default, description=description))


def _create_input_model(mcp_tool: MCPTool) -> type[BaseModel]:
    """根据 MCP 工具的 input_schema 创建 Pydantic 模型"""
    schema = mcp_tool.input_schema
    properties = schema.get("properties", {})
    required = set(schema.get("required", []))

    if not properties:
        # 无参数工具，创建带占位符的模型
        return create_model(
            f"{mcp_tool.name}Input",
            placeholder=(str | None, Field(default=None, description="Placeholder parameter")),
        )

    fields = {}
    for name, prop_schema in properties.items():
        fields[name] = _json_schema_to_pydantic_field(
            name, prop_schema, name in required
        )

    return create_model(f"{mcp_tool.name}Input", **fields)


def _build_tool_description(mcp_tool: MCPTool) -> str:
    """构建工具描述（包含安全警告）"""
    desc = mcp_tool.description

    warnings = []
    if mcp_tool.annotations.destructive_hint is True:
        warnings.append("⚠️ Destructive operation")
    if mcp_tool.annotations.open_world_hint is True:
        warnings.append("🌐 External network access")
    if mcp_tool.annotations.idempotent_hint is False:
        warnings.append("🔄 Non-idempotent operation")

    if warnings:
        desc = f"{desc}\n\nSafety Notes: {', '.join(warnings)}"

    return desc


def _create_mcp_tool(
    client: MCPClient,
    mcp_tool: MCPTool,
) -> StructuredTool:
    """
    将单个 MCP 工具转换为 LangChain 工具

    参数：
    - client: MCP 客户端（已连接）
    - mcp_tool: MCP 工具定义

    返回：
    - LangChain StructuredTool
    """

    async def call_mcp_tool(**kwargs: Any) -> str:
        """调用 MCP 工具（带安全校验）"""
        # 移除占位参数
        kwargs.pop("placeholder", None)

        # 安全校验
        if mcp_tool.annotations.is_dangerous:
            config = get_security_config()

            if config.require_confirmation:
                # 构建警告信息
                warnings = []
                if mcp_tool.annotations.destructive_hint is True:
                    warnings.append("此工具可能执行破坏性操作（删除、修改数据）")
                if mcp_tool.annotations.open_world_hint is True:
                    warnings.append("此工具会访问外部网络")
                if mcp_tool.annotations.idempotent_hint is False:
                    warnings.append("此操作不可撤销，重复执行可能产生不同结果")

                # 确定风险等级
                risk_level = "medium"
                if mcp_tool.annotations.destructive_hint is True:
                    risk_level = "high"
                if mcp_tool.annotations.destructive_hint is True and mcp_tool.annotations.open_world_hint is True:
                    risk_level = "critical"

                # 构建确认请求
                confirmation_request = ConfirmationRequest(
                    operation_type="mcp_tool",
                    name=mcp_tool.name,
                    description=mcp_tool.description or f"MCP 工具: {mcp_tool.name}",
                    parameters=kwargs.copy(),
                    risk_level=risk_level,
                    warnings=warnings,
                    source=str(client),
                    metadata={
                        "tool_title": mcp_tool.title,
                        "annotations": {
                            "destructive_hint": mcp_tool.annotations.destructive_hint,
                            "idempotent_hint": mcp_tool.annotations.idempotent_hint,
                            "open_world_hint": mcp_tool.annotations.open_world_hint,
                            "read_only_hint": mcp_tool.annotations.read_only_hint,
                        },
                    },
                )

                # 请求用户确认
                if config.confirmation_callback:
                    confirmed = config.confirmation_callback(confirmation_request)
                    if not confirmed:
                        raise UserRejectedError(f"用户拒绝执行工具: {mcp_tool.name}")
                else:
                    # 无确认回调 = 无法获得用户同意 = 拒绝执行
                    raise NoConfirmationCallbackError(
                        f"危险工具 {mcp_tool.name} 需要用户确认，但未配置 confirmation_callback。\n"
                        f"请配置 configure_security(confirmation_callback=...) 或设置 require_confirmation=False"
                    )

        # 执行工具调用
        result = await client.call_tool(mcp_tool.name, kwargs)
        return str(result)

    # 创建输入模型
    input_model = _create_input_model(mcp_tool)

    return StructuredTool.from_function(
        func=call_mcp_tool,
        coroutine=call_mcp_tool,
        name=mcp_tool.name,
        description=_build_tool_description(mcp_tool),
        args_schema=input_model,
    )


class MCPToolkit:
    """
    MCP 工具包

    管理多个 MCP 服务器的连接和工具，使用 async context manager 自动清理资源。

    使用示例：
    ```python
    servers = [
        MCPServerStdio(command="npx", args=["-y", "@mcp/server-filesystem", "/tmp"]),
        MCPServerHTTP(url="https://api.example.com/mcp"),
    ]

    async with MCPToolkit(servers) as toolkit:
        tools = toolkit.get_tools()
        # 使用工具...
    ```
    """

    def __init__(
        self,
        servers: list[MCPServerConfig],
        tool_filter: list[str] | None = None,
    ):
        """
        初始化工具包

        参数：
        - servers: MCP 服务器配置列表
        - tool_filter: 工具名称过滤（None 表示全部）
        """
        self._servers = servers
        self._tool_filter = tool_filter
        self._clients: list[MCPClient] = []
        self._tools: list[StructuredTool] = []
        self._exit_stack: AsyncExitStack | None = None

    async def __aenter__(self) -> MCPToolkit:
        """进入上下文，连接所有服务器"""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """退出上下文，关闭所有连接"""
        await self.close()

    async def connect(self) -> None:
        """连接所有 MCP 服务器并加载工具"""
        self._exit_stack = AsyncExitStack()
        await self._exit_stack.__aenter__()

        for config in self._servers:
            try:
                client = MCPClient(config)
                await self._exit_stack.enter_async_context(client)
                self._clients.append(client)

                # 获取工具列表
                mcp_tools = await client.list_tools()

                for mcp_tool in mcp_tools:
                    # 过滤
                    if self._tool_filter and mcp_tool.name not in self._tool_filter:
                        continue

                    # 创建 LangChain 工具
                    tool = _create_mcp_tool(client, mcp_tool)
                    self._tools.append(tool)

                logger.info(f"MCP 服务器连接成功，加载 {len(mcp_tools)} 个工具: {config}")

            except Exception as e:
                logger.error(f"MCP 服务器连接失败: {config}, 错误: {e}")
                continue

    async def close(self) -> None:
        """关闭所有连接"""
        if self._exit_stack:
            await self._exit_stack.__aexit__(None, None, None)
            self._exit_stack = None

        self._clients.clear()
        self._tools.clear()

    def get_tools(self) -> list[StructuredTool]:
        """获取所有工具"""
        return self._tools.copy()

    @property
    def clients(self) -> list[MCPClient]:
        """获取所有客户端"""
        return self._clients.copy()
