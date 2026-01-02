import json

import pytest

from src.modules.etl.tools.agent_tools import resolve_doc_pointer


@pytest.mark.asyncio
async def test_resolve_doc_pointer_inline_success():
    raw = await resolve_doc_pointer.ainvoke({"provider": "inline", "ref": {"content": "hello"}})
    data = json.loads(raw)
    assert data["status"] == "success"
    assert data["content"] == "hello"
    assert data["source"]["provider"] == "inline"


@pytest.mark.asyncio
async def test_resolve_doc_pointer_url_uses_fetcher_and_supports_span_and_truncate(monkeypatch):
    async def fake_fetch_url_text(url: str, *, timeout_seconds: int = 10) -> str:
        assert url == "https://example.com/doc.md"
        assert timeout_seconds == 10
        return "0123456789"

    monkeypatch.setattr("src.modules.etl.tools.agent_tools._fetch_url_text", fake_fetch_url_text)

    raw = await resolve_doc_pointer.ainvoke(
        {
            "provider": "url",
            "ref": {
                "url": "https://example.com/doc.md",
                "span": {"start": 2, "end": 8},  # -> 234567
                "max_chars": 3,  # -> 234 (truncated)
            },
        }
    )
    data = json.loads(raw)
    assert data["status"] == "success"
    assert data["content"] == "234"
    assert data["truncated"] is True
    assert data["source"]["ref"]["url"] == "https://example.com/doc.md"
