from __future__ import annotations

import base64
import os

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import src.infrastructure.keystore.crypto_service as crypto_module
from src.infrastructure.keystore.crypto_service import LocalCryptoService
from src.shared.exception import BadRequestException


def _build_ciphertext(private_key: rsa.RSAPrivateKey, plaintext: str) -> str:
    aes_key = AESGCM.generate_key(bit_length=256)
    nonce = os.urandom(12)
    encrypted_payload = AESGCM(aes_key).encrypt(nonce, plaintext.encode("utf-8"), None)

    encrypted_aes_key = private_key.public_key().encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    merged = encrypted_aes_key + nonce + encrypted_payload
    return "ENCv1:" + base64.b64encode(merged).decode("ascii")


@pytest.mark.asyncio
async def test_decrypt_key_async_with_local_keystore(monkeypatch: pytest.MonkeyPatch) -> None:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )

    class _FakeStorage:
        def __init__(self) -> None:
            self.tenant_code: str | None = None

        def load_private_key(self, tenant_code: str) -> bytes:
            self.tenant_code = tenant_code
            return private_pem

    fake_storage = _FakeStorage()
    monkeypatch.setattr(crypto_module, "get_key_storage", lambda: fake_storage)

    ciphertext = _build_ciphertext(private_key, "sk-live-123")
    service = LocalCryptoService()

    plaintext = await service.decrypt_key_async(
        tenant_code="tenant-acme",
        ciphertext=ciphertext,
    )

    assert plaintext == "sk-live-123"
    assert fake_storage.tenant_code == "tenant-acme"


def test_decrypt_key_async_rejects_invalid_prefix() -> None:
    service = LocalCryptoService()

    with pytest.raises(BadRequestException, match="invalid encryption format"):
        service.decrypt_key(tenant_code="tenant-acme", ciphertext="plain-value")


def test_decrypt_key_supports_running_loop(monkeypatch: pytest.MonkeyPatch) -> None:
    service = LocalCryptoService()

    async def _mock_decrypt(*, tenant_code: str, ciphertext: str) -> str:
        return f"{tenant_code}:{ciphertext}"

    monkeypatch.setattr(service, "decrypt_key_async", _mock_decrypt)

    value = service.decrypt_key(tenant_code="tenant-3", ciphertext="ENCv1:abc")

    assert value == "tenant-3:ENCv1:abc"


def test_is_encrypted_ciphertext() -> None:
    assert crypto_module.is_encrypted_ciphertext("ENCv1:abc")
    assert not crypto_module.is_encrypted_ciphertext("plain")
    assert not crypto_module.is_encrypted_ciphertext(None)
