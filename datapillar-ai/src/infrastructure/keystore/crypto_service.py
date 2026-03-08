# @author Sunny
# @date 2026-03-05

"""Local crypto service based on tenant private keys."""

from __future__ import annotations

import asyncio
import base64
import binascii
import threading
from typing import Any

from cryptography.exceptions import InvalidTag, UnsupportedAlgorithm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from src.infrastructure.keystore import get_key_storage
from src.shared.exception import BadRequestException

_ENCRYPTED_VALUE_PREFIX = "ENCv1:"
_GCM_NONCE_BYTES = 12


class LocalCryptoService:
    """Tenant secret decryption service."""

    async def decrypt_key_async(self, *, tenant_code: str, ciphertext: str) -> str:
        return self._decrypt_key(tenant_code=tenant_code, ciphertext=ciphertext)

    def decrypt_key(self, *, tenant_code: str, ciphertext: str) -> str:
        coroutine = self.decrypt_key_async(tenant_code=tenant_code, ciphertext=ciphertext)
        try:
            asyncio.get_running_loop()
        except RuntimeError:
            return asyncio.run(coroutine)
        return self._run_coroutine_in_thread(coroutine)

    def _decrypt_key(self, *, tenant_code: str, ciphertext: str) -> str:
        normalized_tenant_code = self._validate_tenant_code(tenant_code)
        payload = self._decode_payload(ciphertext)

        private_key_pem = get_key_storage().load_private_key(normalized_tenant_code)
        private_key = self._load_private_key(private_key_pem)

        encrypted_key_length = private_key.key_size // 8
        if len(payload) <= encrypted_key_length + _GCM_NONCE_BYTES:
            raise BadRequestException("api_key invalid encryption format")

        encrypted_aes_key = payload[:encrypted_key_length]
        nonce = payload[encrypted_key_length : encrypted_key_length + _GCM_NONCE_BYTES]
        encrypted_payload = payload[encrypted_key_length + _GCM_NONCE_BYTES :]

        aes_key = self._decrypt_aes_key(private_key, encrypted_aes_key)
        plaintext = self._decrypt_payload(aes_key, nonce, encrypted_payload)

        normalized_plaintext = plaintext.strip()
        if not normalized_plaintext:
            raise BadRequestException("api_key invalid encryption format")
        return normalized_plaintext

    def _decode_payload(self, ciphertext: str) -> bytes:
        normalized_ciphertext = str(ciphertext or "").strip()
        if not normalized_ciphertext:
            raise BadRequestException("ciphertext cannot be empty")
        if not normalized_ciphertext.startswith(_ENCRYPTED_VALUE_PREFIX):
            raise BadRequestException("api_key invalid encryption format")

        encoded = normalized_ciphertext[len(_ENCRYPTED_VALUE_PREFIX) :]
        try:
            return base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise BadRequestException("api_key invalid encryption format") from exc

    def _load_private_key(self, pem_bytes: bytes) -> rsa.RSAPrivateKey:
        try:
            private_key = serialization.load_pem_private_key(pem_bytes, password=None)
        except (TypeError, UnsupportedAlgorithm, ValueError) as exc:
            raise BadRequestException("api_key invalid encryption format") from exc

        if not isinstance(private_key, rsa.RSAPrivateKey):
            raise BadRequestException("api_key invalid encryption format")
        return private_key

    def _decrypt_aes_key(self, private_key: rsa.RSAPrivateKey, encrypted_aes_key: bytes) -> bytes:
        try:
            return private_key.decrypt(
                encrypted_aes_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None,
                ),
            )
        except ValueError as exc:
            raise BadRequestException("api_key invalid encryption format") from exc

    def _decrypt_payload(self, aes_key: bytes, nonce: bytes, encrypted_payload: bytes) -> str:
        try:
            plaintext = AESGCM(aes_key).decrypt(nonce, encrypted_payload, None)
            return plaintext.decode("utf-8")
        except (InvalidTag, UnicodeDecodeError, ValueError) as exc:
            raise BadRequestException("api_key invalid encryption format") from exc

    def _validate_tenant_code(self, tenant_code: str) -> str:
        normalized = str(tenant_code or "").strip()
        if not normalized:
            raise BadRequestException("tenant_code invalid")
        if "/" in normalized or "\\" in normalized or ".." in normalized:
            raise BadRequestException("tenant_code invalid")
        return normalized

    def _run_coroutine_in_thread(self, coroutine: Any) -> Any:
        result: dict[str, Any] = {}
        error: dict[str, BaseException] = {}

        def _runner() -> None:
            try:
                result["value"] = asyncio.run(coroutine)
            except BaseException as exc:  # pragma: no cover - defense branch
                error["value"] = exc

        thread = threading.Thread(target=_runner, daemon=True)
        thread.start()
        thread.join()

        if "value" in error:
            raise error["value"]
        return result.get("value")


def is_encrypted_ciphertext(value: str | None) -> bool:
    return bool(value and value.startswith(_ENCRYPTED_VALUE_PREFIX))


local_crypto_service = LocalCryptoService()
