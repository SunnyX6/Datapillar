# @author Sunny
# @date 2026-02-05

"""
AI Model data access

table:ai_model,ai_llm_usage

DEPRECATED:- This file is reserved for compatible use of historical links
- Please use the new link `ai_model_new.py`(Explicit model input,Dont allow defaulting to the truth)
"""

from __future__ import annotations

import logging
from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient

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


class Model:
    """AI Model query(ai_model)."""

    @staticmethod
    def get_chat_default(tenant_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.tenant_id = :tenant_id
              AND m.status = 'ACTIVE'
              AND m.model_type = 'chat'
            ORDER BY m.updated_at DESC, m.created_at DESC, m.id DESC
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = conn.execute(query, {"tenant_id": tenant_id}).mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"Get enabled Chat Model failed:{e}")
            return None

    @staticmethod
    def get_embedding_default(tenant_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.tenant_id = :tenant_id
              AND m.status = 'ACTIVE'
              AND m.model_type = 'embeddings'
            ORDER BY m.updated_at DESC, m.created_at DESC, m.id DESC
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = conn.execute(query, {"tenant_id": tenant_id}).mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"Get enabled Embedding Model failed:{e}")
            return None

    @staticmethod
    def get_model(ai_model_id: int, tenant_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.id = :id AND m.tenant_id = :tenant_id
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = (
                    conn.execute(query, {"id": ai_model_id, "tenant_id": tenant_id})
                    .mappings()
                    .fetchone()
                )
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"Get model #{ai_model_id} failed:{e}")
            return None

    @staticmethod
    def get_active_chat_model(
        *,
        tenant_id: int,
        ai_model_id: int,
    ) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.tenant_id = :tenant_id
              AND m.id = :ai_model_id
              AND m.model_type = 'chat'
              AND m.status = 'ACTIVE'
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
                            "ai_model_id": ai_model_id,
                        },
                    )
                    .mappings()
                    .fetchone()
                )
                return dict(row) if row else None
        except Exception as e:
            logger.error(
                "Query model failed:tenant=%s aiModelId=%s err=%s",
                tenant_id,
                ai_model_id,
                e,
            )
            return None


class LlmUsage:
    """
    LLM Token Usage(ai_llm_usage)

    constraint:- use run_id The only way to remove duplicates,Support disconnection and reconnection/Duplicate writes caused by event replay
    - DB Exceptions should not affect the main link:The upper layer should catch exceptions and only provide warnings
    """

    @staticmethod
    def upsert_usage(*, record: dict[str, Any]) -> None:
        query = text(
            """
            INSERT INTO ai_llm_usage (
              tenant_id, user_id, session_id, module, agent_id,
              provider, model_name,
              run_id, parent_run_id,
              prompt_tokens, completion_tokens, total_tokens, estimated,
              prompt_cost_usd, completion_cost_usd, total_cost_usd,
              raw_usage_json
            )
            VALUES (
              :tenant_id, :user_id, :session_id, :module, :agent_id,
              :provider, :model_name,
              :run_id, :parent_run_id,
              :prompt_tokens, :completion_tokens, :total_tokens, :estimated,
              :prompt_cost_usd, :completion_cost_usd, :total_cost_usd,
              :raw_usage_json
            )
            ON DUPLICATE KEY UPDATE
              prompt_tokens = VALUES(prompt_tokens),
              completion_tokens = VALUES(completion_tokens),
              total_tokens = VALUES(total_tokens),
              estimated = VALUES(estimated),
              prompt_cost_usd = VALUES(prompt_cost_usd),
              completion_cost_usd = VALUES(completion_cost_usd),
              total_cost_usd = VALUES(total_cost_usd),
              raw_usage_json = VALUES(raw_usage_json)
            """
        )

        with MySQLClient.get_engine().begin() as conn:
            conn.execute(query, record)
