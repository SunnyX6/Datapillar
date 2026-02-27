from __future__ import annotations

import pytest
from datapillar_oneagentic.messages import Message

import src.modules.llm.service as playground_service_module
from src.modules.llm.schemas import PlaygroundChatRequest
from src.modules.llm.service import LlmPlaygroundService


def test_build_llm_provider_applies_model_and_thinking(monkeypatch: pytest.MonkeyPatch) -> None:
    captured_payload: dict[str, object] = {}

    def _mock_get_llm_config() -> dict[str, object]:
        return {"retry": {"max_retries": 1}, "provider": "openai"}

    class _FakeLLMConfig:
        @staticmethod
        def model_validate(payload: dict[str, object]) -> dict[str, object]:
            captured_payload.update(payload)
            return payload

    class _FakeLLMProvider:
        def __init__(self, config: dict[str, object]):
            self.config = config

    monkeypatch.setattr(playground_service_module, "get_llm_config", _mock_get_llm_config)
    monkeypatch.setattr(playground_service_module, "LLMConfig", _FakeLLMConfig)
    monkeypatch.setattr(playground_service_module, "LLMProvider", _FakeLLMProvider)

    service = LlmPlaygroundService()
    provider = service._build_llm_provider(
        model={
            "provider_code": "glm",
            "provider_model_id": "glm-4.5",
            "base_url": "https://open.bigmodel.cn/api/paas/v4",
        },
        api_key="plain-key",
        thinking_enabled=True,
    )

    assert isinstance(provider, _FakeLLMProvider)
    assert captured_payload["provider"] == "glm"
    assert captured_payload["api_key"] == "plain-key"
    assert captured_payload["model"] == "glm-4.5"
    assert captured_payload["base_url"] == "https://open.bigmodel.cn/api/paas/v4"
    assert captured_payload["enable_thinking"] is True


def test_build_messages_includes_system_instruction() -> None:
    service = LlmPlaygroundService()

    messages = service._build_messages(
        message="你好",
        system_instruction="你是严谨助手",
    )

    assert len(messages) == 2
    assert messages[0].role == "system"
    assert messages[0].content == "你是严谨助手"
    assert messages[1].role == "user"
    assert messages[1].content == "你好"


@pytest.mark.asyncio
async def test_stream_chat_emits_text_chunks_with_top_p(monkeypatch: pytest.MonkeyPatch) -> None:
    class _FakeLlm:
        async def astream(self, _messages):
            yield Message.assistant("你", metadata={"reasoning_content": "先"})
            yield Message.assistant("好", metadata={"reasoning_content": "先想"})
            yield Message.assistant("，世界", metadata={"reasoning_content": "先想后答"})

    class _FakeProvider:
        def __init__(self) -> None:
            self.kwargs: dict[str, object] | None = None

        def __call__(self, **kwargs):
            self.kwargs = kwargs
            return _FakeLlm()

    provider = _FakeProvider()

    def _mock_get_model(**_kwargs):
        return {
            "provider_code": "glm",
            "provider_model_id": "glm-4.5",
            "api_key": "ENCv1:xxx",
            "base_url": "https://open.bigmodel.cn/api/paas/v4",
        }

    service = LlmPlaygroundService()

    async def _mock_decrypt_api_key(**_kwargs):
        return "plain-key"

    monkeypatch.setattr(playground_service_module.Model, "get_active_chat_model", _mock_get_model)
    monkeypatch.setattr(service, "_decrypt_api_key", _mock_decrypt_api_key)
    monkeypatch.setattr(service, "_build_llm_provider", lambda **_kwargs: provider)

    payload = PlaygroundChatRequest.model_validate(
        {
            "aiModelId": 88,
            "message": "你好",
            "modelConfig": {
                "temperature": 0.7,
                "topP": 0.9,
                "thinkingEnabled": True,
            },
        }
    )

    deltas: list[tuple[str, str]] = []
    async for delta in service.stream_chat(tenant_id=1, tenant_code="tenant-1", payload=payload):
        if delta.thinking_delta:
            deltas.append(("thinking", delta.thinking_delta))
        if delta.text_delta:
            deltas.append(("text", delta.text_delta))

    assert deltas == [
        ("thinking", "先"),
        ("text", "你"),
        ("thinking", "想"),
        ("text", "好"),
        ("thinking", "后答"),
        ("text", "，世界"),
    ]
    assert provider.kwargs == {
        "temperature": 0.7,
        "top_p": 0.9,
        "streaming": True,
    }


@pytest.mark.asyncio
async def test_decrypt_api_key_calls_auth_crypto_rpc(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    class _FakeAuthCryptoRpcClient:
        async def decrypt_llm_api_key(self, *, tenant_code: str, ciphertext: str) -> str:
            captured["tenant_code"] = tenant_code
            captured["ciphertext"] = ciphertext
            return "plain-key"

    monkeypatch.setattr(
        playground_service_module,
        "auth_crypto_rpc_client",
        _FakeAuthCryptoRpcClient(),
    )

    service = LlmPlaygroundService()
    decrypted = await service._decrypt_api_key(tenant_code="tenant-11", encrypted_value="ENCv1:abc")

    assert decrypted == "plain-key"
    assert captured == {
        "tenant_code": "tenant-11",
        "ciphertext": "ENCv1:abc",
    }
