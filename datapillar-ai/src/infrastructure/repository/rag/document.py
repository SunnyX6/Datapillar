# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 文档仓储."""

from __future__ import annotations

from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient


class DocumentRepository:
    @staticmethod
    def list_by_namespace(
        namespace_id: int,
        user_id: int,
        *,
        status: str | None,
        keyword: str | None,
        limit: int,
        offset: int,
    ) -> tuple[list[dict[str, Any]], int]:
        filters = ["namespace_id = :namespace_id", "created_by = :created_by", "is_deleted = 0"]
        params: dict[str, Any] = {"namespace_id": namespace_id, "created_by": user_id}
        if status:
            filters.append("status = :status")
            params["status"] = status
        if keyword:
            filters.append("title LIKE :keyword")
            params["keyword"] = f"%{keyword}%"

        where_clause = " AND ".join(filters)
        query = text(
            f"""
            SELECT
              document_id,
              namespace_id,
              doc_uid,
              title,
              file_type,
              size_bytes,
              status,
              chunk_count,
              token_count,
              error_message,
              embedding_model_id,
              embedding_dimension,
              chunk_mode,
              chunk_config_json,
              last_chunked_at,
              created_by,
              created_at,
              updated_at
            FROM knowledge_document
            WHERE {where_clause}
            ORDER BY updated_at DESC, document_id DESC
            LIMIT :limit OFFSET :offset
            """
        )
        count_query = text(
            f"""
            SELECT COUNT(1) AS total
            FROM knowledge_document
            WHERE {where_clause}
            """
        )
        params.update({"limit": limit, "offset": offset})
        with MySQLClient.get_engine().connect() as conn:
            rows = [dict(row) for row in conn.execute(query, params).mappings()]
            total = conn.execute(count_query, params).scalar_one()
        return rows, int(total or 0)

    @staticmethod
    def get(document_id: int, user_id: int) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
              document_id,
              namespace_id,
              doc_uid,
              title,
              file_type,
              size_bytes,
              storage_uri,
              storage_type,
              storage_key,
              status,
              chunk_count,
              token_count,
              error_message,
              embedding_model_id,
              embedding_dimension,
              chunk_mode,
              chunk_config_json,
              last_chunked_at,
              created_by,
              created_at,
              updated_at
            FROM knowledge_document
            WHERE document_id = :document_id AND created_by = :created_by AND is_deleted = 0
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = conn.execute(
                query, {"document_id": document_id, "created_by": user_id}
            ).mappings().fetchone()
            return dict(row) if row else None

    @staticmethod
    def get_by_doc_uid(doc_uid: str, user_id: int) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
              document_id,
              namespace_id,
              doc_uid,
              title,
              file_type,
              size_bytes,
              storage_uri,
              storage_type,
              storage_key,
              status,
              chunk_count,
              token_count,
              error_message,
              embedding_model_id,
              embedding_dimension,
              chunk_mode,
              chunk_config_json,
              last_chunked_at,
              created_by,
              created_at,
              updated_at
            FROM knowledge_document
            WHERE doc_uid = :doc_uid AND created_by = :created_by AND is_deleted = 0
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = conn.execute(query, {"doc_uid": doc_uid, "created_by": user_id}).mappings().fetchone()
            return dict(row) if row else None

    @staticmethod
    def list_namespace_embedding_models(namespace_id: int) -> list[int]:
        query = text(
            """
            SELECT DISTINCT embedding_model_id
            FROM knowledge_document
            WHERE namespace_id = :namespace_id AND is_deleted = 0 AND embedding_model_id IS NOT NULL
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            rows = conn.execute(query, {"namespace_id": namespace_id}).fetchall()
            return [int(row[0]) for row in rows if row and row[0] is not None]

    @staticmethod
    def create(payload: dict[str, Any]) -> int:
        query = text(
            """
            INSERT INTO knowledge_document (
              namespace_id,
              doc_uid,
              title,
              file_type,
              size_bytes,
              storage_uri,
              storage_type,
              storage_key,
              status,
              chunk_count,
              token_count,
              error_message,
              embedding_model_id,
              embedding_dimension,
              chunk_mode,
              chunk_config_json,
              last_chunked_at,
              created_by
            ) VALUES (
              :namespace_id,
              :doc_uid,
              :title,
              :file_type,
              :size_bytes,
              :storage_uri,
              :storage_type,
              :storage_key,
              :status,
              :chunk_count,
              :token_count,
              :error_message,
              :embedding_model_id,
              :embedding_dimension,
              :chunk_mode,
              :chunk_config_json,
              :last_chunked_at,
              :created_by
            )
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, payload)
            return int(result.lastrowid)

    @staticmethod
    def update(document_id: int, user_id: int, fields: dict[str, Any]) -> int:
        if not fields:
            return 0
        sets = ", ".join([f"{key} = :{key}" for key in fields])
        query = text(
            f"""
            UPDATE knowledge_document
            SET {sets}
            WHERE document_id = :document_id AND created_by = :created_by AND is_deleted = 0
            """
        )
        params = {"document_id": document_id, "created_by": user_id, **fields}
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, params)
            return int(result.rowcount or 0)

    @staticmethod
    def soft_delete(document_id: int, user_id: int) -> int:
        query = text(
            """
            UPDATE knowledge_document
            SET is_deleted = 1
            WHERE document_id = :document_id AND created_by = :created_by AND is_deleted = 0
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, {"document_id": document_id, "created_by": user_id})
            return int(result.rowcount or 0)
