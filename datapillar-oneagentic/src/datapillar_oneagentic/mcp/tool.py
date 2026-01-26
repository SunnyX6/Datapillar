"""
MCP tool integration.

Convert MCP tools to LangChain tools with built-in security checks.

Security:
- Determine risk based on MCP tool annotations
- Dangerous tools require user confirmation (per MCP spec)

Lifecycle:
- MCPToolkit manages client pools
- async context manager for automatic cleanup
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


def _build_pydantic_field(
    name: str,
    schema: dict[str, Any],
    required: bool = False,
) -> tuple[type, Any]:
    """Convert a JSON Schema field into a Pydantic field."""
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
    """Create a Pydantic model from an MCP tool input_schema."""
    schema = mcp_tool.input_schema
    properties = schema.get("properties", {})
    required = set(schema.get("required", []))

    if not properties:
        # No-arg tool: create a model with a placeholder field.
        return create_model(
            f"{mcp_tool.name}Input",
            placeholder=(str | None, Field(default=None, description="Placeholder parameter")),
        )

    fields = {}
    for name, prop_schema in properties.items():
        fields[name] = _build_pydantic_field(
            name, prop_schema, name in required
        )

    return create_model(f"{mcp_tool.name}Input", **fields)


def _build_tool_description(mcp_tool: MCPTool) -> str:
    """Build tool description with safety warnings."""
    desc = mcp_tool.description

    warnings = []
    if mcp_tool.annotations.destructive_hint is True:
        warnings.append("Destructive operation")
    if mcp_tool.annotations.open_world_hint is True:
        warnings.append("External network access")
    if mcp_tool.annotations.idempotent_hint is False:
        warnings.append("Non-idempotent operation")

    if warnings:
        desc = f"{desc}\n\nSafety Notes: {', '.join(warnings)}"

    return desc


def _create_mcp_tool(
    client: MCPClient,
    mcp_tool: MCPTool,
) -> StructuredTool:
    """
    Convert a single MCP tool into a LangChain tool.

    Args:
        client: MCP client (connected)
        mcp_tool: MCP tool definition

    Returns:
        LangChain StructuredTool
    """

    async def call_mcp_tool(**kwargs: Any) -> str:
        """Call an MCP tool with safety checks."""
        # Remove placeholder parameter.
        kwargs.pop("placeholder", None)

        # Safety checks.
        if mcp_tool.annotations.is_dangerous:
            config = get_security_config()

            if config.require_confirmation:
                # Build warning messages.
                warnings = []
                if mcp_tool.annotations.destructive_hint is True:
                    warnings.append("This tool may perform destructive operations (delete or modify data).")
                if mcp_tool.annotations.open_world_hint is True:
                    warnings.append("This tool will access external networks.")
                if mcp_tool.annotations.idempotent_hint is False:
                    warnings.append("This operation is non-idempotent and may produce different results on retry.")

                # Determine risk level.
                risk_level = "medium"
                if mcp_tool.annotations.destructive_hint is True:
                    risk_level = "high"
                if mcp_tool.annotations.destructive_hint is True and mcp_tool.annotations.open_world_hint is True:
                    risk_level = "critical"

                # Build confirmation request.
                confirmation_request = ConfirmationRequest(
                    operation_type="mcp_tool",
                    name=mcp_tool.name,
                    description=mcp_tool.description or f"MCP tool: {mcp_tool.name}",
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

                # Request user confirmation.
                if config.confirmation_callback:
                    confirmed = config.confirmation_callback(confirmation_request)
                    if not confirmed:
                        raise UserRejectedError(f"User rejected tool execution: {mcp_tool.name}")
                else:
                    # No callback => cannot obtain consent => refuse execution.
                    raise NoConfirmationCallbackError(
                        f"Dangerous tool {mcp_tool.name} requires confirmation, but no confirmation_callback is set.\n"
                        "Configure configure_security(confirmation_callback=...) or set require_confirmation=False."
                    )

        # Execute tool call.
        result = await client.call_tool(mcp_tool.name, kwargs)
        return str(result)

    # Create input model.
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
    MCP toolkit.

    Manage multiple MCP servers and tools, with async context manager cleanup.

    Example:
    ```python
    servers = [
        MCPServerStdio(command="npx", args=["-y", "@mcp/server-filesystem", "/tmp"]),
        MCPServerHTTP(url="https://api.example.com/mcp"),
    ]

    async with MCPToolkit(servers) as toolkit:
        tools = toolkit.get_tools()
        # Use tools...
    ```
    """

    def __init__(
        self,
        servers: list[MCPServerConfig],
        tool_filter: list[str] | None = None,
    ):
        """
        Initialize the toolkit.

        Args:
            servers: MCP server config list
            tool_filter: tool name filter (None for all)
        """
        self._servers = servers
        self._tool_filter = tool_filter
        self._clients: list[MCPClient] = []
        self._tools: list[StructuredTool] = []
        self._exit_stack: AsyncExitStack | None = None

    async def __aenter__(self) -> MCPToolkit:
        """Enter context and connect to all servers."""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """Exit context and close all connections."""
        await self.close()

    async def connect(self) -> None:
        """Connect to all MCP servers and load tools."""
        self._exit_stack = AsyncExitStack()
        await self._exit_stack.__aenter__()

        for config in self._servers:
            try:
                client = MCPClient(config)
                await self._exit_stack.enter_async_context(client)
                self._clients.append(client)

                # Get tool list.
                mcp_tools = await client.list_tools()

                for mcp_tool in mcp_tools:
                    # Filter tools.
                    if self._tool_filter and mcp_tool.name not in self._tool_filter:
                        continue

                    # Create LangChain tool.
                    tool = _create_mcp_tool(client, mcp_tool)
                    self._tools.append(tool)

                logger.info(f"MCP server connected; loaded {len(mcp_tools)} tools: {config}")

            except Exception as e:
                logger.error(f"MCP server connection failed: {config}, error={e}")
                continue

    async def close(self) -> None:
        """Close all connections."""
        if self._exit_stack:
            await self._exit_stack.__aexit__(None, None, None)
            self._exit_stack = None

        self._clients.clear()
        self._tools.clear()

    def get_tools(self) -> list[StructuredTool]:
        """Return all tools."""
        return self._tools.copy()

    @property
    def clients(self) -> list[MCPClient]:
        """Return all clients."""
        return self._clients.copy()
