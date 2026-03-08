# @author Sunny
# @date 2026-02-26

"""
AI Model data access(new implementation)

Purpose:- ETL Waiting for model reading of new links and API Key Decrypt
- Disable default by default,Must be specified explicitly chat model
"""

from __future__ import annotations

import logging
from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient
from src.infrastructure.keystore.crypto_service import (
    is_encrypted_ciphertext,
    local_crypto_service,
)

logger = logging.getLogger(__name__)

_MODEL_FIELDS = (
    "m.id, "
    "m.provider_model_id, "
    "m.name, "
    "m.provider_id, "
    "p.code AS provider_code, "
    "p.name AS provider_name, "
    "m.model_type, "
    "m.description, "
    "m.tags, "
    "m.context_tokens, "
    "m.input_price_usd, "
    "m.output_price_usd, "
    "m.embedding_dimension, "
    "m.api_key, "
    "m.base_url, "
    "m.status, "
    "m.created_by, "
    "m.updated_by, "
    "m.created_at, "
    "m.updated_at"
)


class ModelNew:
    """AI model query (new implementation)."""

    @staticmethod
    def get_active_chat_model_by_id(
        *,
        tenant_id: int,
        user_id: int,
        ai_model_id: int,
    ) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            JOIN ai_model_grant AS g
              ON g.tenant_id = m.tenant_id
             AND g.model_id = m.id
             AND g.user_id = :user_id
            JOIN permissions AS perm ON perm.id = g.permission_id
            WHERE m.tenant_id = :tenant_id
              AND m.id = :ai_model_id
              AND m.model_type = 'chat'
              AND m.status = 'ACTIVE'
              AND perm.status = 1
              AND UPPER(perm.code) <> 'DISABLE'
              AND (g.expires_at IS NULL OR g.expires_at > NOW())
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = (
                    conn.execute(
                        query,
                        {
                            "tenant_id": tenant_id,
                            "user_id": user_id,
                            "ai_model_id": ai_model_id,
                        },
                    )
                    .mappings()
                    .fetchone()
                )
                return dict(row) if row else None
        except Exception as exc:
            logger.error(
                "Failed to query active Chat model: tenant=%s user=%s aiModelId=%s err=%s",
                tenant_id,
                user_id,
                ai_model_id,
                exc,
            )
            return None

    @staticmethod
    def get_active_embedding_default(*, tenant_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.tenant_id = :tenant_id
              AND m.model_type = 'embeddings'
              AND m.status = 'ACTIVE'
            ORDER BY m.updated_at DESC, m.created_at DESC, m.id DESC
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = conn.execute(query, {"tenant_id": tenant_id}).mappings().fetchone()
                return dict(row) if row else None
        except Exception as exc:
            logger.error("Failed to query active Embedding model: tenant=%s err=%s", tenant_id, exc)
            return None

    @staticmethod
    def decrypt_key(*, tenant_code: str, encrypted_value: str | None) -> str:
        if not encrypted_value or not encrypted_value.strip():
            raise ValueError("api_key is empty")
        if not is_encrypted_ciphertext(encrypted_value):
            raise ValueError("api_key is not encrypted")
        return local_crypto_service.decrypt_key(
            tenant_code=tenant_code,
            ciphertext=encrypted_value,
        )
