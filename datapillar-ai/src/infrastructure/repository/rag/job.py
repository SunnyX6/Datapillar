# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 任务仓储."""

from __future__ import annotations

from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient


class JobRepository:
    @staticmethod
    def create(payload: dict[str, Any]) -> int:
        query = text(
            """
            INSERT INTO knowledge_document_job (
              tenant_id,
              namespace_id,
              document_id,
              job_type,
              status,
              progress,
              progress_seq,
              total_chunks,
              processed_chunks,
              error_message,
              started_at,
              finished_at,
              created_by
            ) VALUES (
              :tenant_id,
              :namespace_id,
              :document_id,
              :job_type,
              :status,
              :progress,
              :progress_seq,
              :total_chunks,
              :processed_chunks,
              :error_message,
              :started_at,
              :finished_at,
              :created_by
            )
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            result = conn.execute(query, payload)
            return int(result.lastrowid)

    @staticmethod
    def get(job_id: int, tenant_id: int, user_id: int) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
              job_id,
              namespace_id,
              document_id,
              job_type,
              status,
              progress,
              progress_seq,
              total_chunks,
              processed_chunks,
              error_message,
              started_at,
              finished_at,
              created_at,
              updated_at
            FROM knowledge_document_job
            WHERE job_id = :job_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
            """
        )
        with MySQLClient.get_engine().connect() as conn:
            row = (
                conn.execute(
                    query,
                    {
                        "job_id": job_id,
                        "tenant_id": tenant_id,
                        "created_by": user_id,
                    },
                )
                .mappings()
                .fetchone()
            )
            return dict(row) if row else None

    @staticmethod
    def list_by_document(
        document_id: int,
        tenant_id: int,
        user_id: int,
        *,
        limit: int,
        offset: int,
    ) -> tuple[list[dict[str, Any]], int]:
        query = text(
            """
            SELECT
              job_id,
              namespace_id,
              document_id,
              job_type,
              status,
              progress,
              progress_seq,
              total_chunks,
              processed_chunks,
              error_message,
              started_at,
              finished_at,
              created_at,
              updated_at
            FROM knowledge_document_job
            WHERE document_id = :document_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
            ORDER BY updated_at DESC, job_id DESC
            LIMIT :limit OFFSET :offset
            """
        )
        count_query = text(
            """
            SELECT COUNT(1) AS total
            FROM knowledge_document_job
            WHERE document_id = :document_id
              AND tenant_id = :tenant_id
              AND created_by = :created_by
            """
        )
        params = {
            "document_id": document_id,
            "tenant_id": tenant_id,
            "created_by": user_id,
            "limit": limit,
            "offset": offset,
        }
        with MySQLClient.get_engine().connect() as conn:
            rows = [dict(row) for row in conn.execute(query, params).mappings()]
            total = conn.execute(
                count_query,
                {
                    "document_id": document_id,
                    "tenant_id": tenant_id,
                    "created_by": user_id,
                },
            ).scalar_one()
        return rows, int(total or 0)

    @staticmethod
    def mark_running(job_id: int, tenant_id: int) -> None:
        query = text(
            """
            UPDATE knowledge_document_job
            SET status = 'running',
                started_at = COALESCE(started_at, NOW()),
                progress_seq = progress_seq + 1
            WHERE job_id = :job_id AND tenant_id = :tenant_id
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            conn.execute(query, {"job_id": job_id, "tenant_id": tenant_id})

    @staticmethod
    def update_progress(
        job_id: int,
        tenant_id: int,
        *,
        processed_chunks: int,
        total_chunks: int,
        progress: int,
    ) -> None:
        query = text(
            """
            UPDATE knowledge_document_job
            SET status = 'running',
                progress = :progress,
                total_chunks = :total_chunks,
                processed_chunks = :processed_chunks,
                progress_seq = progress_seq + 1
            WHERE job_id = :job_id AND tenant_id = :tenant_id
            """
        )
        params = {
            "job_id": job_id,
            "tenant_id": tenant_id,
            "progress": progress,
            "total_chunks": total_chunks,
            "processed_chunks": processed_chunks,
        }
        with MySQLClient.get_engine().begin() as conn:
            conn.execute(query, params)

    @staticmethod
    def mark_success(
        job_id: int,
        tenant_id: int,
        *,
        processed_chunks: int,
        total_chunks: int,
    ) -> None:
        query = text(
            """
            UPDATE knowledge_document_job
            SET status = 'success',
                progress = 100,
                total_chunks = :total_chunks,
                processed_chunks = :processed_chunks,
                finished_at = NOW(),
                progress_seq = progress_seq + 1
            WHERE job_id = :job_id AND tenant_id = :tenant_id
            """
        )
        params = {
            "job_id": job_id,
            "tenant_id": tenant_id,
            "total_chunks": total_chunks,
            "processed_chunks": processed_chunks,
        }
        with MySQLClient.get_engine().begin() as conn:
            conn.execute(query, params)

    @staticmethod
    def mark_error(job_id: int, tenant_id: int, message: str) -> None:
        query = text(
            """
            UPDATE knowledge_document_job
            SET status = 'error',
                error_message = :message,
                finished_at = NOW(),
                progress_seq = progress_seq + 1
            WHERE job_id = :job_id AND tenant_id = :tenant_id
            """
        )
        with MySQLClient.get_engine().begin() as conn:
            conn.execute(
                query,
                {"job_id": job_id, "tenant_id": tenant_id, "message": message},
            )
