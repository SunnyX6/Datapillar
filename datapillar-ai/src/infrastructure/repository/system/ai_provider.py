# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""AI 供应商数据访问."""

from __future__ import annotations

from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient


class Provider:
    """AI 供应商查询（ai_provider）"""

    @staticmethod
    def list_all() -> list[dict[str, Any]]:
        query = text(
            """
            SELECT
              id,
              code,
              name,
              base_url,
              model_ids,
              created_at,
              updated_at
            FROM ai_provider
            ORDER BY id
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            return [dict(row) for row in conn.execute(query).mappings()]

    @staticmethod
    def get_by_code(code: str) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
              id,
              code,
              name,
              base_url,
              model_ids,
              created_at,
              updated_at
            FROM ai_provider
            WHERE code = :code
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = conn.execute(query, {"code": code}).mappings().fetchone()
            return dict(row) if row else None
