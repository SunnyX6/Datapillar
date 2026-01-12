"""
MCP 工具集成

将 MCP 工具转换为 LangChain 工具。
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

from src.modules.oneagentic.mcp.client import MCPClient, MCPTool
from src.modules.oneagentic.mcp.config import MCPServerConfig

logger = logging.getLogger(__name__)


def _json_schema_to_pydantic_field(
    name: str,
    schema: dict[str, Any],
    required: bool,
) -> tuple[type, Any]:
    """将 JSON Schema 转换为 Pydantic 字段"""
    json_type = schema.get("type", "string")
    description = schema.get("description", "")

    # 类型映射
    type_map = {
        "string": str,
        "integer": int,
        "number": float,
        "boolean": bool,
        "array": list,
        "object": dict,
    }

    python_type = type_map.get(json_type, str)

    if required:
        return (python_type, Field(description=description))
    else:
        return (python_type | None, Field(default=None, description=description))


def _create_input_model(tool: MCPTool) -> type[BaseModel]:
    """从 MCP 工具创建 Pydantic 输入模型"""
    input_schema = tool.input_schema
    properties = input_schema.get("properties", {})
    required = set(input_schema.get("required", []))

    fields = {}
    for prop_name, prop_schema in properties.items():
        is_required = prop_name in required
        fields[prop_name] = _json_schema_to_pydantic_field(prop_name, prop_schema, is_required)

    if not fields:
        # 没有参数，创建空模型
        fields["_placeholder"] = (str | None, Field(default=None, description="占位"))

    model = create_model(
        f"{tool.name}Input",
        **fields,
    )

    return model


def create_mcp_tool(
    client: MCPClient,
    mcp_tool: MCPTool,
) -> StructuredTool:
    """
    将 MCP 工具转换为 LangChain StructuredTool

    参数：
    - client: MCP 客户端
    - mcp_tool: MCP 工具描述

    返回：
    - LangChain StructuredTool
    """

    async def call_mcp_tool(**kwargs) -> str:
        """调用 MCP 工具"""
        # 移除占位参数
        kwargs.pop("_placeholder", None)
        result = await client.call_tool(mcp_tool.name, kwargs)
        return str(result)

    # 创建输入模型
    input_model = _create_input_model(mcp_tool)

    return StructuredTool.from_function(
        func=call_mcp_tool,
        coroutine=call_mcp_tool,
        name=mcp_tool.name,
        description=mcp_tool.description or f"MCP 工具: {mcp_tool.name}",
        args_schema=input_model,
    )


async def create_mcp_tools(
    config: MCPServerConfig,
    tool_filter: list[str] | None = None,
) -> tuple[MCPClient, list[StructuredTool]]:
    """
    从 MCP 服务器创建工具列表

    参数：
    - config: MCP 服务器配置
    - tool_filter: 工具名称过滤（None 表示全部）

    返回：
    - (client, tools) 元组，client 需要手动关闭

    使用示例：
    ```python
    config = MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    )

    client, tools = await create_mcp_tools(config)

    try:
        # 使用工具
        for tool in tools:
            print(f"工具: {tool.name}")
    finally:
        await client.close()
    ```
    """
    client = MCPClient(config)
    await client.connect()

    mcp_tools = await client.list_tools()
    tools = []

    for mcp_tool in mcp_tools:
        # 过滤
        if tool_filter and mcp_tool.name not in tool_filter:
            continue

        tool = create_mcp_tool(client, mcp_tool)
        tools.append(tool)
        logger.info(f"创建 MCP 工具: {mcp_tool.name}")

    return client, tools
