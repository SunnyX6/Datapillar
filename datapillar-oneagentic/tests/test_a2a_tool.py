from __future__ import annotations

import sys
import types

import pytest

from datapillar_oneagentic.a2a.config import A2AConfig
from datapillar_oneagentic.a2a.tool import create_a2a_tool
from datapillar_oneagentic.security import (
    UserRejectedError,
    configure_security,
    reset_security_config,
)


@pytest.fixture(autouse=True)
def _reset_security_config() -> None:
    reset_security_config()
    yield
    reset_security_config()


@pytest.fixture(autouse=True)
def _stub_a2a() -> None:
    a2a_module = types.ModuleType("a2a")
    client_module = types.ModuleType("a2a.client")
    client_module.ClientFactory = object()
    sys.modules["a2a"] = a2a_module
    sys.modules["a2a.client"] = client_module
    yield
    sys.modules.pop("a2a.client", None)
    sys.modules.pop("a2a", None)


@pytest.mark.asyncio
async def test_a2a_tool() -> None:
    configure_security(
        require_confirmation=True,
        confirmation_callback=lambda _req: False,
    )
    config = A2AConfig(
        endpoint="https://example.com/.well-known/agent.json",
        require_confirmation=True,
        skip_security_check=True,
    )
    tool = create_a2a_tool(config, name="delegate")

    with pytest.raises(UserRejectedError):
        await tool.ainvoke({"task": "analyze", "context": "context"})


@pytest.mark.asyncio
async def test_a2a_tool2(monkeypatch) -> None:
    configure_security(
        require_confirmation=True,
        confirmation_callback=lambda _req: True,
    )
    config = A2AConfig(
        endpoint="https://example.com/.well-known/agent.json",
        require_confirmation=True,
        skip_security_check=True,
    )
    tool = create_a2a_tool(config, name="delegate")

    async def fake_call(_endpoint: str, _task: str) -> str:
        return "ok"

    monkeypatch.setattr("datapillar_oneagentic.a2a.tool._call_a2a_agent", fake_call)

    result = await tool.ainvoke({"task": "analyze", "context": ""})
    assert result == "ok"
