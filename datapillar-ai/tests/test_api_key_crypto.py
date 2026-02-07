# -*- coding: utf-8 -*-

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from src.infrastructure.crypto.key_crypto import decrypt_api_key, encrypt_api_key


def _generate_keys() -> tuple[str, bytes]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    public_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode("utf-8")
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return public_pem, private_pem


def test_encrypt_decrypt_roundtrip() -> None:
    public_pem, private_pem = _generate_keys()
    plaintext = "secret-key"
    encrypted = encrypt_api_key(public_pem, plaintext)
    assert encrypted.startswith("ENCv1:")
    decrypted = decrypt_api_key(private_pem, encrypted)
    assert decrypted == plaintext
