# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
AI 模型数据访问

表：ai_model, ai_llm_usage
"""

import logging
from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient

logger = logging.getLogger(__name__)

_MODEL_FIELDS = (
    "id, "
    "name, "
    "provider, "
    "model_name, "
    "model_type, "
    "api_key, "
    "base_url, "
    "is_enabled, "
    "embedding_dimension, "
    "supports_function_calling, "
    "supports_structured_output, "
    "scope"
)


class Model:
    """AI 模型查询（ai_model）"""

    @staticmethod
    def get_chat_default() -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS} FROM ai_model
            WHERE is_enabled = 1 AND model_type = 'chat'
            ORDER BY updated_at DESC, created_at DESC, id DESC
            LIMIT 1
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query)
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取启用 Chat 模型失败: {e}")
            return None

    @staticmethod
    def get_embedding_default() -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS} FROM ai_model
            WHERE is_enabled = 1
              AND model_type = 'embedding'
              AND scope = 'SYSTEM'
            ORDER BY updated_at DESC, created_at DESC, id DESC
            LIMIT 1
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query)
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取启用 Embedding 模型失败: {e}")
            return None

    @staticmethod
    def get_model(model_id: int) -> dict[str, Any] | None:
        query = text(
            f"""
            SELECT {_MODEL_FIELDS} FROM ai_model
            WHERE id = :id AND is_enabled = 1
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"id": model_id})
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取模型 #{model_id} 失败: {e}")
            return None

    @staticmethod
    def list_enabled_models(model_type: str | None = None) -> list[dict[str, Any]]:
        if model_type:
            query = text(
                f"""
                SELECT {_MODEL_FIELDS} FROM ai_model
                WHERE is_enabled = 1 AND model_type = :model_type
                ORDER BY updated_at DESC, created_at DESC, id DESC
            """
            )
            params: dict[str, Any] = {"model_type": model_type}
        else:
            query = text(
                f"""
                SELECT {_MODEL_FIELDS} FROM ai_model
                WHERE is_enabled = 1
                ORDER BY model_type, updated_at DESC, created_at DESC, id DESC
            """
            )
            params = {}

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
              user_id, session_id, module, agent_id,
              provider, model_name,
              run_id, parent_run_id,
              prompt_tokens, completion_tokens, total_tokens, estimated,
              prompt_cost_usd, completion_cost_usd, total_cost_usd,
              raw_usage_json
            )
            VALUES (
              :user_id, :session_id, :module, :agent_id,
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
