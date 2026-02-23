from __future__ import annotations

import importlib
from types import SimpleNamespace

import pytest

from src.infrastructure.rpc.crypto.crypto_client import AuthCryptoRpcClient

auth_crypto_module = importlib.import_module("src.infrastructure.rpc.crypto.crypto_client")


@pytest.mark.asyncio
async def test_decrypt_llm_api_key_builds_rpc_meta(monkeypatch: pytest.MonkeyPatch) -> None:
    client = AuthCryptoRpcClient(rpc_group="datapillar", rpc_version="1.0.0")

    async def _mock_resolve_endpoint():
        return auth_crypto_module._RpcEndpoint(
            host="127.0.0.1",
            port=50051,
            service_name="datapillar.security.v1.CryptoService",
        )

    captured_request: dict[str, object] = {}

    async def _mock_call_decrypt(*, endpoint, request):
        captured_request["endpoint"] = endpoint
        captured_request["request"] = request
        result = auth_crypto_module._DECRYPT_RESULT_CLS()
        result.data.plaintext = "plain-value"
        return result

    monkeypatch.setattr(client, "_resolve_endpoint", _mock_resolve_endpoint)
    monkeypatch.setattr(client, "_call_decrypt", _mock_call_decrypt)
    monkeypatch.setattr(
        auth_crypto_module,
        "get_nacos_runtime",
        lambda: SimpleNamespace(
            config=SimpleNamespace(service_name="datapillar-ai", group="DATAPILLAR")
        ),
    )

    plaintext = await client.decrypt_llm_api_key(tenant_code="tenant-acme", ciphertext="ENCv1:abc")

    request = captured_request["request"]
    assert plaintext == "plain-value"
    assert request.tenant_code == "tenant-acme"
    assert request.purpose == "llm.api_key"
    assert request.ciphertext == "ENCv1:abc"
    assert request.meta.protocol_version == "security.v1"
    assert request.meta.caller_service == "datapillar-ai"
    assert request.meta.attrs["caller"] == "datapillar-ai"


@pytest.mark.asyncio
async def test_decrypt_llm_api_key_rejects_empty_plaintext(monkeypatch: pytest.MonkeyPatch) -> None:
    client = AuthCryptoRpcClient()

    async def _mock_resolve_endpoint():
        return auth_crypto_module._RpcEndpoint(
            host="127.0.0.1",
            port=50051,
            service_name="datapillar.security.v1.CryptoService",
        )

    async def _mock_call_decrypt(*, endpoint, request):
        result = auth_crypto_module._DECRYPT_RESULT_CLS()
        result.data.plaintext = "   "
        return result

    monkeypatch.setattr(client, "_resolve_endpoint", _mock_resolve_endpoint)
    monkeypatch.setattr(client, "_call_decrypt", _mock_call_decrypt)
    monkeypatch.setattr(
        auth_crypto_module,
        "get_nacos_runtime",
        lambda: SimpleNamespace(
            config=SimpleNamespace(service_name="datapillar-ai", group="DATAPILLAR")
        ),
    )

    with pytest.raises(RuntimeError, match="空明文"):
        await client.decrypt_llm_api_key(tenant_code="tenant-acme", ciphertext="ENCv1:abc")


def test_candidate_service_names_contains_expected_patterns() -> None:
    client = AuthCryptoRpcClient(rpc_group="datapillar", rpc_version="1.0.0")

    candidates = client._candidate_service_names()

    assert candidates[0] == "providers:datapillar.security.v1.CryptoService:1.0.0:datapillar"
    assert (
        candidates[1]
        == "providers:com.sunny.datapillar.common.rpc.security.v1.CryptoService:1.0.0:datapillar"
    )
    assert "datapillar.security.v1.CryptoService" in candidates
    assert "com.sunny.datapillar.common.rpc.security.v1.CryptoService" in candidates


def test_is_encrypted_ciphertext() -> None:
    assert auth_crypto_module.is_encrypted_ciphertext("ENCv1:abc")
    assert not auth_crypto_module.is_encrypted_ciphertext("plain")
    assert not auth_crypto_module.is_encrypted_ciphertext(None)


@pytest.mark.asyncio
async def test_decrypt_llm_api_key_sync_supports_running_loop(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = AuthCryptoRpcClient()

    async def _mock_decrypt(*, tenant_code: str, ciphertext: str) -> str:
        return f"{tenant_code}:{ciphertext}"

    monkeypatch.setattr(client, "decrypt_llm_api_key", _mock_decrypt)

    value = client.decrypt_llm_api_key_sync(tenant_code="tenant-3", ciphertext="ENCv1:abc")

    assert value == "tenant-3:ENCv1:abc"
