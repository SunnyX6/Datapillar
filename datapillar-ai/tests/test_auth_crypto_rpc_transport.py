from __future__ import annotations

import importlib
from contextlib import suppress

import grpc
import pytest

from src.infrastructure.rpc.crypto.crypto_client import AuthCryptoRpcClient

auth_crypto_module = importlib.import_module("src.infrastructure.rpc.crypto.crypto_client")


@pytest.mark.asyncio
async def test_call_decrypt_fallbacks_to_java_service_path_and_passes_metadata() -> None:
    client = AuthCryptoRpcClient(rpc_group="datapillar", rpc_version="1.0.0")
    server = grpc.aio.server()
    captured_metadata: list[tuple[tuple[str, str], ...]] = []

    async def _decrypt_handler(request_bytes: bytes, context: grpc.aio.ServicerContext) -> bytes:
        request = auth_crypto_module._DECRYPT_REQUEST_CLS.FromString(request_bytes)
        assert request.tenant_code == "tenant-1"
        assert request.purpose == "llm.api_key"
        assert request.ciphertext == "ENCv1:cipher"

        captured_metadata.append(
            tuple((item.key, item.value) for item in context.invocation_metadata())
        )

        result = auth_crypto_module._DECRYPT_RESULT_CLS()
        result.data.plaintext = "plain-value"
        return result.SerializeToString()

    handler = grpc.unary_unary_rpc_method_handler(
        _decrypt_handler,
        request_deserializer=lambda value: value,
        response_serializer=lambda value: value,
    )
    generic_handler = grpc.method_handlers_generic_handler(
        auth_crypto_module._SERVICE_NAME_JAVA,
        {"Decrypt": handler},
    )
    server.add_generic_rpc_handlers((generic_handler,))

    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()

    endpoint = auth_crypto_module._RpcEndpoint(
        host="127.0.0.1",
        port=port,
        service_name=auth_crypto_module._SERVICE_NAME_JAVA,
    )

    request = client._build_decrypt_request(
        tenant_code="tenant-1",
        ciphertext="ENCv1:cipher",
        caller_service="datapillar-ai",
    )

    try:
        response = await client._call_decrypt(endpoint=endpoint, request=request)
        assert response.data.plaintext == "plain-value"
        assert len(captured_metadata) == 1

        metadata = dict(captured_metadata[0])
        assert metadata["tri-service-group"] == "datapillar"
        assert metadata["tri-service-version"] == "1.0.0"
    finally:
        with suppress(Exception):
            await server.stop(0)
