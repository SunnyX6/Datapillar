# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""
AI 模型数据访问

表：ai_model, ai_llm_usage
"""

from __future__ import annotations

import logging
from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient

logger = logging.getLogger(__name__)

_MODEL_FIELDS = (
    "m.id, "
    "m.model_id, "
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
    """AI 模型查询（ai_model）"""

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
            logger.error(f"获取启用 Chat 模型失败: {e}")
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
            logger.error(f"获取启用 Embedding 模型失败: {e}")
            return None

    @staticmethod
    def get_detail(model_id: int, tenant_id: int) -> dict[str, Any] | None:
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
                row = conn.execute(query, {"id": model_id, "tenant_id": tenant_id}).mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取模型 #{model_id} 失败: {e}")
            return None

    @staticmethod
    def get_model(model_id: int, tenant_id: int) -> dict[str, Any] | None:
        return Model.get_detail(model_id, tenant_id)

    @staticmethod
    def get_by_model_id(model_id: str, tenant_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE m.tenant_id = :tenant_id AND m.model_id = :model_id
            LIMIT 1
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = conn.execute(query, {"model_id": model_id, "tenant_id": tenant_id}).mappings().fetchone()
            return dict(row) if row else None

    @staticmethod
    def list_models(
        *,
        limit: int,
        offset: int,
        tenant_id: int,
        keyword: str | None = None,
        provider_code: str | None = None,
        model_type: str | None = None,
    ) -> tuple[list[dict[str, Any]], int]:
        filters: list[str] = ["m.tenant_id = :tenant_id"]
        params: dict[str, Any] = {"limit": limit, "offset": offset, "tenant_id": tenant_id}

        if keyword:
            filters.append("(m.model_id LIKE :keyword OR m.name LIKE :keyword)")
            params["keyword"] = f"%{keyword}%"
        if provider_code:
            filters.append("p.code = :provider_code")
            params["provider_code"] = provider_code
        if model_type:
            filters.append("m.model_type = :model_type")
            params["model_type"] = model_type

        where_clause = " AND ".join(filters) if filters else "1=1"

        query = text(
            f"""
            SELECT {_MODEL_FIELDS}
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE {where_clause}
            ORDER BY m.updated_at DESC, m.created_at DESC, m.id DESC
            LIMIT :limit OFFSET :offset
            """
        )
        count_query = text(
            f"""
            SELECT COUNT(1) AS total
            FROM ai_model AS m
            JOIN ai_provider AS p ON m.provider_id = p.id
            WHERE {where_clause}
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            rows = [dict(row) for row in conn.execute(query, params).mappings()]
            total = conn.execute(count_query, params).scalar_one()
        return rows, int(total or 0)

    @staticmethod
    def create(payload: dict[str, Any]) -> int:
        query = text(
            """
            INSERT INTO ai_model (
              tenant_id,
              model_id,
              name,
              provider_id,
              model_type,
              description,
              tags,
              context_tokens,
              input_price_usd,
              output_price_usd,
              embedding_dimension,
              api_key,
              base_url,
              status,
              created_by,
              updated_by
            ) VALUES (
              :tenant_id,
              :model_id,
              :name,
              :provider_id,
              :model_type,
              :description,
              :tags,
              :context_tokens,
              :input_price_usd,
              :output_price_usd,
              :embedding_dimension,
              :api_key,
              :base_url,
              :status,
              :created_by,
              :updated_by
            )
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, payload)
            return int(result.lastrowid)

    @staticmethod
    def update(model_id: int, tenant_id: int, fields: dict[str, Any]) -> int:
        if not fields:
            return 0
        sets = ", ".join([f"{key} = :{key}" for key in fields])
        query = text(
            f"""
            UPDATE ai_model
            SET {sets}
            WHERE id = :id AND tenant_id = :tenant_id
            """
        )
        params = {"id": model_id, "tenant_id": tenant_id, **fields}
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, params)
            return int(result.rowcount or 0)

    @staticmethod
    def update_connection(model_id: int, tenant_id: int, fields: dict[str, Any]) -> int:
        return Model.update(model_id, tenant_id, fields)

    @staticmethod
    def delete(model_id: int, tenant_id: int) -> int:
        query = text(
            """
            DELETE FROM ai_model
            WHERE id = :id AND tenant_id = :tenant_id
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, {"id": model_id, "tenant_id": tenant_id})
            return int(result.rowcount or 0)

    @staticmethod
    def list_enabled_models(tenant_id: int, model_type: str | None = None) -> list[dict[str, Any]]:
        if model_type:
            query = text(
                f"""
                SELECT {_MODEL_FIELDS}
                FROM ai_model AS m
                JOIN ai_provider AS p ON m.provider_id = p.id
                WHERE m.tenant_id = :tenant_id
                  AND m.status = 'ACTIVE'
                  AND m.model_type = :model_type
                ORDER BY m.updated_at DESC, m.created_at DESC, m.id DESC
                """
            )
            params: dict[str, Any] = {"model_type": model_type, "tenant_id": tenant_id}
        else:
            query = text(
                f"""
                SELECT {_MODEL_FIELDS}
                FROM ai_model AS m
                JOIN ai_provider AS p ON m.provider_id = p.id
                WHERE m.tenant_id = :tenant_id AND m.status = 'ACTIVE'
                ORDER BY m.model_type, m.updated_at DESC, m.created_at DESC, m.id DESC
                """
            )
            params = {"tenant_id": tenant_id}

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, params)
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"列出启用模型失败: {e}")
            return []


class LlmUsage:
    """
    LLM Token 使用量（ai_llm_usage）

    约束：
    - 使用 run_id 唯一去重，支持断线重连/事件重放导致的重复写入
    - DB 异常不应影响主链路：上层应捕获异常仅做告警
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
