"""LLM 模型管理服务测试."""

from __future__ import annotations

from decimal import Decimal

import pytest

from src.infrastructure.repository.system import ai_model as model_repo
from src.infrastructure.repository.system import ai_provider as provider_repo
from src.modules.llm_manager.schemas import ModelCreateRequest, ModelType
from src.modules.llm_manager.service import ConflictError, LlmManagerService


def test_list_providers_filters_unsupported(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(
        provider_repo.Provider,
        "list_all",
        lambda: [
            {
                "id": 1,
                "code": "openai",
                "name": "OpenAI",
                "base_url": "https://api.openai.com/v1",
                "model_ids": '["openai/gpt-4o"]',
            },
            {
                "id": 2,
                "code": "unknown",
                "name": "Unknown",
                "base_url": None,
                "model_ids": "[]",
            },
        ],
    )
    service = LlmManagerService()

    providers = service.list_providers()

    assert len(providers) == 1
    assert providers[0]["code"] == "openai"
    assert providers[0]["model_ids"] == ["openai/gpt-4o"]


def test_create_model_conflict(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(
        provider_repo.Provider,
        "get_by_code",
        lambda code: {"id": 1, "code": "openai", "name": "OpenAI"},
    )
    monkeypatch.setattr(model_repo.Model, "get_by_model_id", lambda model_id: {"id": 99})

    service = LlmManagerService()
    payload = ModelCreateRequest(
        model_id="openai/gpt-4o",
        name="OpenAI GPT-4o",
        provider_code="openai",
        model_type=ModelType.CHAT,
    )

    with pytest.raises(ConflictError):
        service.create_model(user_id=1, payload=payload)


def test_create_model_success(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(
        provider_repo.Provider,
        "get_by_code",
        lambda code: {"id": 1, "code": "openai", "name": "OpenAI"},
    )
    monkeypatch.setattr(model_repo.Model, "get_by_model_id", lambda model_id: None)
    monkeypatch.setattr(model_repo.Model, "create", lambda payload: 10)

    def _fake_detail(model_id: int):
        return {
            "id": model_id,
            "model_id": "openai/gpt-4o",
            "name": "OpenAI GPT-4o",
            "provider_id": 1,
            "provider_code": "openai",
            "provider_name": "OpenAI",
            "model_type": "chat",
            "description": None,
            "tags": '["chat"]',
            "context_tokens": 128000,
            "input_price_usd": Decimal("5.000000"),
            "output_price_usd": None,
            "embedding_dimension": None,
            "api_key": None,
            "base_url": "https://api.openai.com/v1",
            "status": "CONNECT",
            "created_by": 1,
            "updated_by": 1,
            "created_at": "2026-02-05T00:00:00",
            "updated_at": "2026-02-05T00:00:00",
        }

    monkeypatch.setattr(model_repo.Model, "get_detail", _fake_detail)

    service = LlmManagerService()
    payload = ModelCreateRequest(
        model_id="openai/gpt-4o",
        name="OpenAI GPT-4o",
        provider_code="openai",
        model_type=ModelType.CHAT,
        tags=["chat"],
        context_tokens=128000,
        input_price_usd=Decimal("5.000000"),
        base_url="https://api.openai.com/v1",
    )

    result = service.create_model(user_id=1, payload=payload)

    assert result["id"] == 10
    assert result["has_api_key"] is False
    assert result["tags"] == ["chat"]


@pytest.mark.asyncio
async def test_connect_model_updates_status(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(
        model_repo.Model,
        "get_detail",
        lambda model_id: {
            "id": model_id,
            "model_id": "openai/gpt-4o",
            "provider_code": "openai",
            "base_url": "https://api.openai.com/v1",
        },
    )
    updates: dict[str, object] = {}

    def _update_connection(model_id: int, fields: dict[str, object]) -> int:
        updates["model_id"] = model_id
        updates["fields"] = fields
        return 1

    monkeypatch.setattr(model_repo.Model, "update_connection", _update_connection)

    service = LlmManagerService()

    async def _fake_validate_connection(**_kwargs):
        return None

    monkeypatch.setattr(service, "_validate_connection", _fake_validate_connection)

    result = await service.connect_model(
        user_id=1,
        model_id=1,
        api_key="sk-xxx",
        base_url=None,
    )

    assert result["connected"] is True
    assert updates["fields"]["status"] == "ACTIVE"
