from __future__ import annotations

import pytest

from datapillar_oneagentic.mcp.client import MCPTool, ToolAnnotations
from datapillar_oneagentic.mcp.tool import (
    _build_tool_description,
    _create_input_model,
    _create_mcp_tool,
)
from datapillar_oneagentic.security import (
    NoConfirmationCallbackError,
    UserRejectedError,
    configure_security,
    reset_security_config,
)


@pytest.fixture(autouse=True)
def _reset_security_config() -> None:
    reset_security_config()
    yield
    reset_security_config()


class _StubMcpClient:
    def __init__(self, result: str = "ok") -> None:
        self._result = result

    async def call_tool(self, _name: str, _arguments: dict) -> str:
        return self._result

    def __repr__(self) -> str:
        return "StubMcpClient"


@pytest.mark.asyncio
async def test_create_mcp() -> None:
    tool_def = MCPTool(
        name="danger",
        description="danger tool",
        input_schema={
            "properties": {"name": {"type": "string"}},
            "required": ["name"],
        },
        annotations=ToolAnnotations(destructive_hint=True),
    )
    tool = _create_mcp_tool(_StubMcpClient(), tool_def)

    with pytest.raises(NoConfirmationCallbackError):
        await tool.ainvoke({"name": "x"})


@pytest.mark.asyncio
async def test_create_mcp2() -> None:
    configure_security(confirmation_callback=lambda _req: False)
    tool_def = MCPTool(
        name="danger",
        description="danger tool",
        input_schema={
            "properties": {"name": {"type": "string"}},
            "required": ["name"],
        },
        annotations=ToolAnnotations(destructive_hint=True),
    )
    tool = _create_mcp_tool(_StubMcpClient(), tool_def)

    with pytest.raises(UserRejectedError):
        await tool.ainvoke({"name": "x"})


@pytest.mark.asyncio
async def test_create_mcp3() -> None:
    configure_security(confirmation_callback=lambda _req: True)
    tool_def = MCPTool(
        name="danger",
        description="danger tool",
        input_schema={
            "properties": {"name": {"type": "string"}},
            "required": ["name"],
        },
        annotations=ToolAnnotations(destructive_hint=True),
    )
    tool = _create_mcp_tool(_StubMcpClient("done"), tool_def)

    result = await tool.ainvoke({"name": "x"})
    assert result == "done"


def test_tool_annotations() -> None:
    assert ToolAnnotations(destructive_hint=True).is_dangerous is True
    assert ToolAnnotations(idempotent_hint=False).is_dangerous is True
    assert ToolAnnotations(open_world_hint=True).is_dangerous is True
    assert (
        ToolAnnotations(read_only_hint=True, destructive_hint=False).is_dangerous is False
    )
    assert ToolAnnotations().is_dangerous is True


def test_create_input() -> None:
    tool_def = MCPTool(name="noop", description="noop", input_schema={})
    model = _create_input_model(tool_def)
    assert "placeholder" in model.model_fields


def test_build_tool() -> None:
    tool_def = MCPTool(
        name="tool",
        description="desc",
        annotations=ToolAnnotations(
            destructive_hint=True,
            idempotent_hint=False,
            open_world_hint=True,
        ),
    )
    desc = _build_tool_description(tool_def)
    assert "Safety Notes" in desc
    assert "Destructive operation" in desc
