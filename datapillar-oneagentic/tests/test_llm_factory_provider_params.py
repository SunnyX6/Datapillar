from __future__ import annotations

import sys
import types

import pytest

from datapillar_oneagentic.providers.llm.llm import LLMFactory, LLMProviderConfig


@pytest.mark.parametrize(
    ("provider", "model_name", "api_key", "base_url", "streaming"),
    [
        ("deepseek", "deepseek-chat", "deepseek-key", "https://custom.deepseek/v1", True),
        ("openrouter", "openrouter/model", "router-key", "https://custom.openrouter/v1", False),
        ("ollama", "llama3.1", "ollama-key", "http://127.0.0.1:11434/v1", True),
    ],
)
def test_chat_openai_providers_use_configured_credentials_and_base_url(
    monkeypatch: pytest.MonkeyPatch,
    provider: str,
    model_name: str,
    api_key: str,
    base_url: str,
    streaming: bool,
) -> None:
    captured: dict[str, object] = {}

    class _FakeChatOpenAI:
        def __init__(self, **kwargs):
            captured.update(kwargs)

        def bind(self, **_kwargs):
            return self

    monkeypatch.setitem(
        sys.modules,
        "langchain_openai",
        types.SimpleNamespace(ChatOpenAI=_FakeChatOpenAI),
    )

    config = LLMProviderConfig(
        provider=provider,
        model_name=model_name,
        api_key=api_key,
        base_url=base_url,
        streaming=streaming,
    )

    LLMFactory.create_chat_model(config)

    assert captured["api_key"] == api_key
    assert captured["base_url"] == base_url
    assert captured["model"] == model_name
    assert captured["streaming"] is streaming


def test_deepseek_without_base_url_passes_none(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    class _FakeChatOpenAI:
        def __init__(self, **kwargs):
            captured.update(kwargs)

        def bind(self, **_kwargs):
            return self

    monkeypatch.setitem(
        sys.modules,
        "langchain_openai",
        types.SimpleNamespace(ChatOpenAI=_FakeChatOpenAI),
    )

    config = LLMProviderConfig(
        provider="deepseek",
        model_name="deepseek-chat",
        api_key="deepseek-key",
        base_url=None,
        streaming=False,
    )

    LLMFactory.create_chat_model(config)

    assert captured["base_url"] is None
