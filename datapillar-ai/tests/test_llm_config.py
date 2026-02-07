"""
LLM 配置构建测试
"""

from __future__ import annotations

import pytest

import src.infrastructure.llm.config as config_module
from src.shared.config.exceptions import ConfigurationError


@pytest.fixture(autouse=True)
def _clear_config_cache():
    config_module.get_datapillar_config.cache_clear()
    yield
    config_module.get_datapillar_config.cache_clear()


def _mock_models(monkeypatch: pytest.MonkeyPatch) -> None:
    chat_model = {
        "provider_code": "openai",
        "api_key": "db-key",
        "model_id": "openai/gpt-4o",
        "base_url": "https://api.openai.com/v1",
    }
    embedding_model = {
        "provider_code": "openai",
        "api_key": "embed-key",
        "model_id": "openai/text-embedding-3-small",
        "base_url": "https://api.openai.com/v1",
        "embedding_dimension": 1536,
    }
    monkeypatch.setattr(config_module.Model, "get_chat_default", lambda: chat_model)
    monkeypatch.setattr(config_module.Model, "get_embedding_default", lambda: embedding_model)


def test_get_datapillar_config_merges_settings(monkeypatch: pytest.MonkeyPatch):
    llm_config = {
        "provider": "anthropic",
        "retry": {"max_retries": 3},
        "cache": {
            "enabled": True,
            "backend": "redis",
            "redis_url": "redis://127.0.0.1:6379/0",
            "ttl_seconds": 300,
            "key_prefix": "llm_cache:",
        },
    }
    agent_config = {
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
    }
    monkeypatch.setattr(config_module, "get_llm_config", lambda: llm_config)
    monkeypatch.setattr(config_module, "get_agent_config", lambda: agent_config)
    _mock_models(monkeypatch)

    config = config_module.get_datapillar_config()

    assert config.llm.provider == "openai"
    assert config.llm.model == "openai/gpt-4o"
    assert config.llm.retry.max_retries == 3
    assert config.llm.cache.backend == "redis"
    assert config.llm.cache.redis_url == "redis://127.0.0.1:6379/0"
    assert config.agent.max_steps == 5
    assert config.agent.checkpointer.type == "redis"
    assert config.agent.checkpointer.url == "redis://127.0.0.1:6379/0"
    assert config.embedding.model == "openai/text-embedding-3-small"


def test_get_datapillar_config_requires_runtime_sections(monkeypatch: pytest.MonkeyPatch):
    def _raise_missing_llm():
        raise ConfigurationError("缺少 llm")

    monkeypatch.setattr(config_module, "get_llm_config", _raise_missing_llm)
    monkeypatch.setattr(config_module, "get_agent_config", lambda: {"max_steps": 5})
    _mock_models(monkeypatch)

    with pytest.raises(ConfigurationError):
        config_module.get_datapillar_config()
