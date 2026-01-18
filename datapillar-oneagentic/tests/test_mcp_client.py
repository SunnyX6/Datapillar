from __future__ import annotations

import pytest

from datapillar_oneagentic.mcp.client import MCPClient, MCPConnectionError, MCPServerHTTP


class _StubToolAnnotations:
    destructiveHint = True
    idempotentHint = False
    openWorldHint = True
    readOnlyHint = None


class _StubTool:
    def __init__(self) -> None:
        self.name = "tool"
        self.description = "desc"
        self.inputSchema = {"type": "object"}
        self.annotations = _StubToolAnnotations()
        self.title = "Tool Title"


class _StubListToolsResult:
    def __init__(self, tools) -> None:
        self.tools = tools


class _StubContentItem:
    def __init__(self, *, text=None, data=None) -> None:
        self.text = text
        self.data = data


class _StubCallToolResult:
    def __init__(self, content=None, structured_content=None) -> None:
        self.content = content
        self.structuredContent = structured_content


class _StubSession:
    async def list_tools(self):
        return _StubListToolsResult([_StubTool()])

    async def call_tool(self, _name, _arguments):
        return _StubCallToolResult([_StubContentItem(text="ok")])


class _StubSessionMulti:
    async def list_tools(self):
        return _StubListToolsResult([])

    async def call_tool(self, _name, _arguments):
        return _StubCallToolResult(
            [_StubContentItem(text="a"), _StubContentItem(text="b")]
        )


class _StubSessionStructured:
    async def list_tools(self):
        return _StubListToolsResult([])

    async def call_tool(self, _name, _arguments):
        return _StubCallToolResult(content=[], structured_content={"status": "ok"})


def _client_with_session(session) -> MCPClient:
    client = MCPClient(
        MCPServerHTTP(url="https://example.com", skip_security_check=True)
    )
    client._session = session
    return client


@pytest.mark.asyncio
async def test_mcp_client_list_tools_parses_annotations() -> None:
    client = _client_with_session(_StubSession())

    tools = await client.list_tools()
    assert len(tools) == 1
    assert tools[0].annotations.destructive_hint is True
    assert tools[0].annotations.idempotent_hint is False
    assert tools[0].annotations.open_world_hint is True


@pytest.mark.asyncio
async def test_mcp_client_call_tool_single_content() -> None:
    client = _client_with_session(_StubSession())
    result = await client.call_tool("tool", {"x": 1})
    assert result == "ok"


@pytest.mark.asyncio
async def test_mcp_client_call_tool_multiple_content_items() -> None:
    client = _client_with_session(_StubSessionMulti())
    result = await client.call_tool("tool", {"x": 1})
    assert result == ["a", "b"]


@pytest.mark.asyncio
async def test_mcp_client_call_tool_structured_content_fallback() -> None:
    client = _client_with_session(_StubSessionStructured())
    result = await client.call_tool("tool", {"x": 1})
    assert result == {"status": "ok"}


@pytest.mark.asyncio
async def test_mcp_client_raises_when_not_connected() -> None:
    client = MCPClient(MCPServerHTTP(url="https://example.com", skip_security_check=True))
    with pytest.raises(MCPConnectionError):
        await client.list_tools()
