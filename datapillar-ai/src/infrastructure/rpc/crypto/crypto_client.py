# @author Sunny
# @date 2026-02-19

"""Auth 加解密 Dubbo RPC 客户端。"""

from __future__ import annotations

import asyncio
import logging
import os
import threading
from dataclasses import dataclass
from typing import Any

import grpc
from v2.nacos.naming.model.instance import Instance
from v2.nacos.naming.model.naming_param import ListInstanceParam, ListServiceParam

from src.infrastructure.rpc.proto.datapillar.security.v1.crypto_pb2 import (
    DecryptRequest,
    DecryptResult,
)
from src.shared.config.nacos_client import get_nacos_runtime
from src.shared.exception import (
    AlreadyExistsException,
    BadRequestException,
    ConflictException,
    NotFoundException,
    ServiceUnavailableException,
)
from src.shared.web.code import Code

logger = logging.getLogger(__name__)

_PROTO_PROTOCOL_VERSION = "security.v1"
_PURPOSE_LLM_API_KEY = "llm.api_key"
_LIST_SERVICES_PAGE_SIZE = 100
_ENCRYPTED_VALUE_PREFIX = "ENCv1:"

_SERVICE_NAME_PROTO = "datapillar.security.v1.CryptoService"
_SERVICE_NAME_JAVA = "com.sunny.datapillar.common.rpc.security.v1.CryptoService"

_METHOD_PATHS = (
    f"/{_SERVICE_NAME_PROTO}/Decrypt",
    f"/{_SERVICE_NAME_JAVA}/Decrypt",
)

_DECRYPT_REQUEST_CLS = DecryptRequest
_DECRYPT_RESULT_CLS = DecryptResult


@dataclass(frozen=True)
class _RpcEndpoint:
    host: str
    port: int
    service_name: str


class AuthCryptoRpcClient:
    """Auth Crypto RPC 调用客户端。"""

    def __init__(
        self,
        *,
        timeout_seconds: float = 5.0,
        rpc_group: str | None = None,
        rpc_version: str | None = None,
    ) -> None:
        self._timeout_seconds = timeout_seconds
        self._rpc_group = (rpc_group or os.getenv("DUBBO_GROUP") or "datapillar").strip()
        self._rpc_version = (rpc_version or os.getenv("DUBBO_VERSION") or "1.0.0").strip()
        self._endpoint: _RpcEndpoint | None = None
        self._channel: grpc.aio.Channel | None = None
        self._channel_target: str | None = None
        self._lock = asyncio.Lock()

    async def decrypt_llm_api_key(self, *, tenant_code: str, ciphertext: str) -> str:
        """解密 LLM API Key。"""
        endpoint = await self._resolve_endpoint()
        request = self._build_runtime_decrypt_request(
            tenant_code=tenant_code, ciphertext=ciphertext
        )
        response = await self._call_decrypt(endpoint=endpoint, request=request)
        return self._extract_plaintext(response)

    def decrypt_llm_api_key_sync(self, *, tenant_code: str, ciphertext: str) -> str:
        """同步上下文使用的解密入口。"""
        coroutine = self.decrypt_llm_api_key(tenant_code=tenant_code, ciphertext=ciphertext)
        try:
            asyncio.get_running_loop()
        except RuntimeError:
            return asyncio.run(coroutine)
        return self._run_coroutine_in_thread(coroutine)

    def _build_decrypt_request(
        self,
        *,
        tenant_code: str,
        ciphertext: str,
        caller_service: str,
    ) -> DecryptRequest:
        request = DecryptRequest()
        request.meta.protocol_version = _PROTO_PROTOCOL_VERSION
        request.meta.caller_service = caller_service
        request.meta.attrs["caller"] = caller_service
        request.tenant_code = tenant_code
        request.purpose = _PURPOSE_LLM_API_KEY
        request.ciphertext = ciphertext
        return request

    def _build_runtime_decrypt_request(self, *, tenant_code: str, ciphertext: str):
        valid_tenant_code = self._validate_tenant_code(tenant_code)
        normalized_ciphertext = self._validate_ciphertext(ciphertext)
        caller_service = get_nacos_runtime().config.service_name
        return self._build_decrypt_request(
            tenant_code=valid_tenant_code,
            ciphertext=normalized_ciphertext,
            caller_service=caller_service,
        )

    def _extract_plaintext(self, response: Any) -> str:
        if response is None:
            raise ServiceUnavailableException("密钥存储服务不可用")

        if response.HasField("error"):
            self._raise_rpc_error(response.error)

        if not response.HasField("data"):
            raise ServiceUnavailableException("密钥存储服务不可用")

        plaintext = str(getattr(response.data, "plaintext", "") or "").strip()
        if not plaintext:
            raise RuntimeError("Auth Crypto RPC 返回空明文")
        return plaintext

    async def _call_decrypt(self, *, endpoint: _RpcEndpoint, request):
        channel = await self._get_channel(endpoint)
        metadata = self._build_rpc_metadata()
        last_unimplemented_error: grpc.aio.AioRpcError | None = None
        for method_path in _METHOD_PATHS:
            try:
                return await self._invoke_decrypt(
                    channel=channel,
                    method_path=method_path,
                    request=request,
                    metadata=metadata,
                )
            except grpc.aio.AioRpcError as exc:
                if exc.code() == grpc.StatusCode.UNIMPLEMENTED:
                    last_unimplemented_error = exc
                    continue
                await self._handle_transport_error(exc=exc, endpoint=endpoint)
                raise

        if last_unimplemented_error is not None:
            raise last_unimplemented_error
        raise RuntimeError("Auth Crypto RPC Decrypt 调用失败")

    def _build_rpc_metadata(self) -> tuple[tuple[str, str], tuple[str, str]]:
        return (
            ("tri-service-group", self._rpc_group),
            ("tri-service-version", self._rpc_version),
        )

    async def _invoke_decrypt(
        self,
        *,
        channel: grpc.aio.Channel,
        method_path: str,
        request: Any,
        metadata: tuple[tuple[str, str], tuple[str, str]],
    ) -> Any:
        unary_call = channel.unary_unary(
            method_path,
            request_serializer=lambda message: message.SerializeToString(),
            response_deserializer=DecryptResult.FromString,
        )
        return await unary_call(
            request,
            timeout=self._timeout_seconds,
            metadata=metadata,
        )

    def _raise_rpc_error(self, rpc_error: Any) -> None:
        code = int(getattr(rpc_error, "code", Code.INTERNAL_ERROR) or Code.INTERNAL_ERROR)
        error_type = str(getattr(rpc_error, "type", "") or "").strip()
        message = str(getattr(rpc_error, "message", "") or "").strip() or "服务调用失败"
        context = dict(getattr(rpc_error, "context", {}) or {})
        retryable = bool(getattr(rpc_error, "retryable", False))

        if error_type == "TENANT_KEY_NOT_FOUND":
            raise NotFoundException(message, error_type=error_type, context=context)
        if error_type == "TENANT_PRIVATE_KEY_ALREADY_EXISTS":
            raise AlreadyExistsException(message, error_type=error_type, context=context)
        if error_type in {"TENANT_PUBLIC_KEY_MISSING", "TENANT_PRIVATE_KEY_MISSING"}:
            raise ConflictException(message, error_type=error_type, context=context)
        if error_type in {"CIPHERTEXT_INVALID", "PURPOSE_NOT_ALLOWED", "TENANT_KEY_INVALID"}:
            raise BadRequestException(message, error_type=error_type, context=context)
        if error_type == "KEY_STORAGE_UNAVAILABLE":
            raise ServiceUnavailableException(
                message, error_type=error_type, context=context, retryable=True
            )

        if code == Code.BAD_REQUEST:
            raise BadRequestException(
                message, error_type=error_type or "BAD_REQUEST", context=context
            )
        if code == Code.NOT_FOUND:
            raise NotFoundException(message, error_type=error_type or "NOT_FOUND", context=context)
        if code == Code.CONFLICT:
            raise ConflictException(message, error_type=error_type or "CONFLICT", context=context)
        if code in {Code.BAD_GATEWAY, Code.SERVICE_UNAVAILABLE}:
            raise ServiceUnavailableException(
                message,
                error_type=error_type or "SERVICE_UNAVAILABLE",
                context=context,
                retryable=True,
            )
        if retryable:
            raise ServiceUnavailableException(
                message,
                error_type=error_type or "SERVICE_UNAVAILABLE",
                context=context,
                retryable=True,
            )
        raise ServiceUnavailableException(
            message,
            error_type=error_type or "INTERNAL_ERROR",
            context=context,
            retryable=False,
        )

    async def _handle_transport_error(
        self, *, exc: grpc.aio.AioRpcError, endpoint: _RpcEndpoint
    ) -> None:
        if exc.code() in {grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.DEADLINE_EXCEEDED}:
            await self._invalidate_cached_endpoint(endpoint)

    async def _resolve_endpoint(self) -> _RpcEndpoint:
        if self._endpoint is not None:
            return self._endpoint

        async with self._lock:
            if self._endpoint is not None:
                return self._endpoint

            runtime = get_nacos_runtime()
            endpoint = await self._discover_endpoint(nacos_group=runtime.config.group)
            self._endpoint = endpoint
            return endpoint

    async def _discover_endpoint(self, *, nacos_group: str) -> _RpcEndpoint:
        naming_client = get_nacos_runtime().naming_client
        endpoint = await self._discover_from_service_names(
            naming_client=naming_client,
            service_names=self._candidate_service_names(),
            nacos_group=nacos_group,
        )
        if endpoint is not None:
            return endpoint

        discovered_services = await self._find_crypto_services(nacos_group=nacos_group)
        endpoint = await self._discover_from_service_names(
            naming_client=naming_client,
            service_names=discovered_services,
            nacos_group=nacos_group,
        )
        if endpoint is not None:
            return endpoint

        raise RuntimeError("未发现 Auth Crypto RPC 服务实例")

    async def _discover_from_service_names(
        self,
        *,
        naming_client: Any,
        service_names: list[str],
        nacos_group: str,
    ) -> _RpcEndpoint | None:
        for service_name in service_names:
            instances = await self._list_instances(
                naming_client=naming_client,
                service_name=service_name,
                nacos_group=nacos_group,
            )
            endpoint = self._select_endpoint(service_name=service_name, instances=instances)
            if endpoint is not None:
                return endpoint
        return None

    def _candidate_service_names(self) -> list[str]:
        raw_candidates = [
            f"providers:{_SERVICE_NAME_PROTO}:{self._rpc_version}:{self._rpc_group}",
            f"providers:{_SERVICE_NAME_JAVA}:{self._rpc_version}:{self._rpc_group}",
            _SERVICE_NAME_PROTO,
            _SERVICE_NAME_JAVA,
        ]
        return self._deduplicate(raw_candidates)

    async def _find_crypto_services(self, *, nacos_group: str) -> list[str]:
        naming_client = get_nacos_runtime().naming_client

        page_no = 1
        discovered: list[str] = []
        while True:
            services, total = await self._list_services_page(
                naming_client=naming_client,
                nacos_group=nacos_group,
                page_no=page_no,
            )
            for service_name in services:
                if self._is_crypto_service(service_name):
                    discovered.append(service_name)

            if (
                len(services) < _LIST_SERVICES_PAGE_SIZE
                or page_no * _LIST_SERVICES_PAGE_SIZE >= total
            ):
                break
            page_no += 1

        return self._deduplicate(discovered)

    async def _list_services_page(
        self,
        *,
        naming_client: Any,
        nacos_group: str,
        page_no: int,
    ) -> tuple[list[str], int]:
        service_list = await naming_client.list_services(
            ListServiceParam(
                group_name=nacos_group,
                page_no=page_no,
                page_size=_LIST_SERVICES_PAGE_SIZE,
            )
        )
        services = list(service_list.services or [])
        total = int(getattr(service_list, "count", 0) or 0)
        return services, total

    def _is_crypto_service(self, service_name: str) -> bool:
        normalized = service_name.lower()
        if "cryptoservice" not in normalized:
            return False
        return "datapillar.security.v1" in normalized or "common.rpc.security.v1" in normalized

    async def _list_instances(
        self,
        *,
        naming_client,
        service_name: str,
        nacos_group: str,
    ) -> list[Instance]:
        try:
            result = await naming_client.list_instances(
                ListInstanceParam(
                    service_name=service_name,
                    group_name=nacos_group,
                    subscribe=False,
                    healthy_only=True,
                )
            )
        except Exception as exc:
            logger.debug("查询 Auth Crypto 服务实例失败: service=%s err=%s", service_name, exc)
            return []
        return list(result or [])

    def _select_endpoint(
        self,
        *,
        service_name: str,
        instances: list[Instance],
    ) -> _RpcEndpoint | None:
        healthy = [
            item
            for item in instances
            if item.enabled and item.healthy and item.ip and int(item.port) > 0
        ]
        if not healthy:
            healthy = [
                item for item in instances if item.enabled and item.ip and int(item.port) > 0
            ]
        if not healthy:
            return None

        healthy.sort(key=lambda item: float(item.weight or 0.0), reverse=True)
        selected = healthy[0]
        return _RpcEndpoint(
            host=str(selected.ip),
            port=int(selected.port),
            service_name=service_name,
        )

    async def _get_channel(self, endpoint: _RpcEndpoint) -> grpc.aio.Channel:
        target = f"{endpoint.host}:{endpoint.port}"

        if self._channel is not None and self._channel_target == target:
            return self._channel

        if self._channel is not None:
            await self._channel.close()

        self._channel = grpc.aio.insecure_channel(target)
        self._channel_target = target
        return self._channel

    async def _invalidate_cached_endpoint(self, endpoint: _RpcEndpoint) -> None:
        async with self._lock:
            if self._endpoint != endpoint:
                return
            self._endpoint = None
            if self._channel is not None:
                await self._channel.close()
            self._channel = None
            self._channel_target = None

    def _validate_tenant_code(self, tenant_code: str) -> str:
        normalized = str(tenant_code or "").strip()
        if not normalized:
            raise ValueError("tenant_code 无效")
        return normalized

    def _validate_ciphertext(self, ciphertext: str) -> str:
        normalized = str(ciphertext or "").strip()
        if not normalized:
            raise ValueError("ciphertext 不能为空")
        return normalized

    def _deduplicate(self, values: list[str]) -> list[str]:
        deduplicated: list[str] = []
        seen: set[str] = set()
        for value in values:
            normalized = value.strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            deduplicated.append(normalized)
        return deduplicated

    def _run_coroutine_in_thread(self, coroutine: Any) -> Any:
        result: dict[str, Any] = {}
        error: dict[str, BaseException] = {}

        def _runner() -> None:
            try:
                result["value"] = asyncio.run(coroutine)
            except BaseException as exc:  # pragma: no cover - 防御分支
                error["value"] = exc

        thread = threading.Thread(target=_runner, daemon=True)
        thread.start()
        thread.join()

        if "value" in error:
            raise error["value"]
        return result.get("value")


def is_encrypted_ciphertext(value: str | None) -> bool:
    if not value:
        return False
    return value.startswith(_ENCRYPTED_VALUE_PREFIX)


auth_crypto_rpc_client = AuthCryptoRpcClient()
