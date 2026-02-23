# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 命名空间仓储."""

from __future__ import annotations

from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient


class NamespaceRepository:
    @staticmethod
    def list_by_user(
        tenant_id: int,
        user_id: int,
        *,
        limit: int,
        offset: int,
    ) -> tuple[list[dict[str, Any]], int]:
        query = text(
            """
            SELECT
              ns.namespace_id,
              ns.namespace,
              ns.description,
              ns.status,
              ns.created_by,
              ns.created_at,
              ns.updated_at,
              COALESCE(doc_stats.doc_count, 0) AS doc_count
            FROM knowledge_namespace AS ns
            LEFT JOIN (
              SELECT namespace_id, COUNT(1) AS doc_count
              FROM knowledge_document
              WHERE tenant_id = :tenant_id AND created_by = :created_by AND is_deleted = 0
              GROUP BY namespace_id
            ) AS doc_stats
              ON doc_stats.namespace_id = ns.namespace_id
            WHERE ns.tenant_id = :tenant_id AND ns.created_by = :created_by AND ns.is_deleted = 0
            ORDER BY ns.updated_at DESC, ns.namespace_id DESC
            LIMIT :limit OFFSET :offset
            """
        )
        count_query = text(
            """
            SELECT COUNT(1) AS total
            FROM knowledge_namespace
            WHERE tenant_id = :tenant_id AND created_by = :created_by AND is_deleted = 0
            """
        )

        params = {
            "tenant_id": tenant_id,
            "created_by": user_id,
            "limit": limit,
            "offset": offset,
        }
        with MySQLClient.get_engine().connect() as conn:
            rows = [dict(row) for row in conn.execute(query, params).mappings()]
            total = conn.execute(
                count_query,
                {"tenant_id": tenant_id, "created_by": user_id},
            ).scalar_one()
        return rows, int(total or 0)

    @staticmethod
    def get(namespace_id: int, tenant_id: int, user_id: int) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
              namespace_id,
              namespace,
              description,
              status,
              created_by,
              created_at,
              updated_at
            FROM knowledge_namespace
            WHERE namespace_id = :namespace_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
              AND is_deleted = 0
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = (
                conn.execute(
                    query,
                    {
                        "namespace_id": namespace_id,
                        "tenant_id": tenant_id,
                        "created_by": user_id,
                    },
                )
                .mappings()
                .fetchone()
            )
            return dict(row) if row else None

    @staticmethod
    def create(payload: dict[str, Any]) -> int:
        query = text(
            """
            INSERT INTO knowledge_namespace (
              tenant_id, namespace, description, created_by, status
            ) VALUES (
              :tenant_id, :namespace, :description, :created_by, :status
            )
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, payload)
            return int(result.lastrowid)

    @staticmethod
    def update(namespace_id: int, tenant_id: int, user_id: int, fields: dict[str, Any]) -> int:
        if not fields:
            return 0
        allowed_fields = {"namespace", "description", "status"}
        safe_fields = {key: value for key, value in fields.items() if key in allowed_fields}
        if not safe_fields:
            return 0

        query = text(
            """
            UPDATE knowledge_namespace
            SET
              namespace = COALESCE(:namespace, namespace),
              description = COALESCE(:description, description),
              status = COALESCE(:status, status)
            WHERE namespace_id = :namespace_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
              AND is_deleted = 0
            """
        )
        params = {
            "namespace_id": namespace_id,
            "tenant_id": tenant_id,
            "created_by": user_id,
            "namespace": safe_fields.get("namespace"),
            "description": safe_fields.get("description"),
            "status": safe_fields.get("status"),
        }
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, params)
            return int(result.rowcount or 0)

    @staticmethod
    def soft_delete(namespace_id: int, tenant_id: int, user_id: int) -> int:
        query = text(
            """
            UPDATE knowledge_namespace
            SET is_deleted = 1
            WHERE namespace_id = :namespace_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
              AND is_deleted = 0
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(
                query,
                {
                    "namespace_id": namespace_id,
                    "tenant_id": tenant_id,
                    "created_by": user_id,
                },
            )
            return int(result.rowcount or 0)
