# @author Sunny
# @date 2026-02-07

"""Tenant data access."""

from __future__ import annotations

import logging

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient

logger = logging.getLogger(__name__)


class Tenant:
    @staticmethod
    def get_code(tenant_id: int) -> str | None:
        query = text(
            """
            SELECT code
            FROM tenants
            WHERE id = :tenant_id
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = conn.execute(query, {"tenant_id": tenant_id}).fetchone()
                if not row:
                    return None
                value = str(row[0] or "").strip()
                return value or None
        except Exception as exc:
            logger.error("Failed to obtain tenant code:%s", exc)
            return None

    @staticmethod
    def get_encrypt_public_key(tenant_id: int) -> str | None:
        query = text(
            """
            SELECT encrypt_public_key
            FROM tenants
            WHERE id = :tenant_id
            LIMIT 1
            """
        )
        try:
            with MySQLClient.get_engine().connect() as conn:
                row = conn.execute(query, {"tenant_id": tenant_id}).fetchone()
                if not row:
                    return None
                return row[0]
        except Exception as exc:
            logger.error("Failed to obtain tenant public key:%s", exc)
            return None
