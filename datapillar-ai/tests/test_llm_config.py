"""
LLM 配置构建测试
"""

from __future__ import annotations

import pytest

import src.infrastructure.llm.config as config_module


class SettingsStub:
    def __init__(self, data: dict[str, object]):
        self._data = data

    def get(self, key: str, default: object | None = None) -> object:
        return self._data.get(key, default)


@pytest.fixture(autouse=True)
def _clear_config_cache():
    config_module.get_datapillar_config.cache_clear()
    yield
    config_module.get_datapillar_config.cache_clear()


def _mock_models(monkeypatch: pytest.MonkeyPatch) -> None:
    chat_model = {
        "provider": "openai",
        "api_key": "db-key",
        "model_name": "gpt-4o",
        "base_url": "https://api.openai.com/v1",
    }
    embedding_model = {
        "provider": "openai",
        "api_key": "embed-key",
        "model_name": "text-embedding-3-small",
        "base_url": "https://api.openai.com/v1",
        "embedding_dimension": 1536,
    }
    monkeypatch.setattr(config_module.Model, "get_chat_default", lambda: chat_model)
    monkeypatch.setattr(config_module.Model, "get_embedding_default", lambda: embedding_model)


def test_get_datapillar_config_merges_settings(monkeypatch: pytest.MonkeyPatch):
    settings_data = {
        "llm": {
            "provider": "anthropic",
            "retry": {"max_retries": 3},
            "cache": {
                "enabled": True,
                "backend": "redis",
                "redis_url": "redis://127.0.0.1:6379/0",
                "ttl_seconds": 300,
                "key_prefix": "llm_cache:",
            },
        },
        "agent": {
            "max_steps": 5,
            "checkpointer": {
                "type": "redis",
                "url": "redis://127.0.0.1:6379/0",
                "ttl_minutes": 10080,
            },
            "deliverable_store": {
                "type": "redis",
                "url": "redis://127.0.0.1:6379/0",
            },
        },
    }
    monkeypatch.setattr(config_module, "settings", SettingsStub(settings_data))
    _mock_models(monkeypatch)

    config = config_module.get_datapillar_config()

    assert config.llm.provider == "openai"
    assert config.llm.model == "gpt-4o"
    assert config.llm.retry.max_retries == 3
    assert config.llm.cache.backend == "redis"
    assert config.llm.cache.redis_url == "redis://127.0.0.1:6379/0"
    assert config.agent.max_steps == 5
    assert config.agent.checkpointer.type == "redis"
    assert config.agent.checkpointer.url == "redis://127.0.0.1:6379/0"
    assert config.embedding.model == "text-embedding-3-small"


def test_get_datapillar_config_uses_defaults(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(config_module, "settings", SettingsStub({}))
    _mock_models(monkeypatch)

    config = config_module.get_datapillar_config()

    assert config.llm.cache.backend == "memory"
    assert config.agent.max_steps == 25
