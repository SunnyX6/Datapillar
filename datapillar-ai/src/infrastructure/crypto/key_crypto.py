# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-07

"""
API Key 加解密工具（RSA-OAEP + AES-GCM）
"""

from __future__ import annotations

import base64
import os

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ENC_PREFIX = "ENCv1:"


def is_encrypted(value: str | None) -> bool:
    if not value:
        return False
    return value.startswith(ENC_PREFIX)


def encrypt_api_key(public_key_pem: str, api_key: str) -> str:
    if not public_key_pem or not public_key_pem.strip():
        raise ValueError("public_key_pem 不能为空")
    if not api_key or not api_key.strip():
        raise ValueError("api_key 不能为空")

    public_key = serialization.load_pem_public_key(public_key_pem.encode("utf-8"))
    aes_key = AESGCM.generate_key(bit_length=256)
    aesgcm = AESGCM(aes_key)
    nonce = os.urandom(12)
    ciphertext = aesgcm.encrypt(nonce, api_key.encode("utf-8"), None)

    encrypted_key = public_key.encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )

    payload = encrypted_key + nonce + ciphertext
    encoded = base64.b64encode(payload).decode("utf-8")
    return f"{ENC_PREFIX}{encoded}"


def decrypt_api_key(private_key_pem: bytes, encrypted_value: str) -> str:
    if not private_key_pem:
        raise ValueError("private_key_pem 不能为空")
    if not encrypted_value or not encrypted_value.strip():
        raise ValueError("encrypted_value 不能为空")
    if not encrypted_value.startswith(ENC_PREFIX):
        raise ValueError("api_key 未加密")

    payload = base64.b64decode(encrypted_value[len(ENC_PREFIX) :])
    private_key = serialization.load_pem_private_key(private_key_pem, password=None)
    key_size = private_key.key_size // 8
    if len(payload) <= key_size + 12:
        raise ValueError("密文长度无效")

    encrypted_key = payload[:key_size]
    nonce = payload[key_size : key_size + 12]
    ciphertext = payload[key_size + 12 :]

    aes_key = private_key.decrypt(
        encrypted_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    aesgcm = AESGCM(aes_key)
    plaintext = aesgcm.decrypt(nonce, ciphertext, None)
    return plaintext.decode("utf-8")
