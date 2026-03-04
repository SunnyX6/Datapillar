# @author Sunny
# @date 2026-01-27

"""
ETL Component data access

table：job_component
"""

import logging
from typing import Any

from sqlalchemy import text

from src.infrastructure.database.mysql import MySQLClient

logger = logging.getLogger(__name__)


class Component:
    """ETL Component query（job_component）"""

    @staticmethod
    def list_active(tenant_id: int) -> list[dict[str, Any]]:
        query = text(
            """
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE tenant_id = :tenant_id AND status = 1 AND is_deleted = 0
            ORDER BY sort_order, id
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"tenant_id": tenant_id})
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"Failed to get component list: {e}")
            return []

    @staticmethod
    def get_by_code(component_code: str, tenant_id: int) -> dict[str, Any] | None:
        query = text(
            """
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE tenant_id = :tenant_id
              AND component_code = :component_code
              AND status = 1
              AND is_deleted = 0
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(
                    query,
                    {
                        "tenant_id": tenant_id,
                        "component_code": component_code,
                    },
                )
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"Get component {component_code} failed: {e}")
            return None

    @staticmethod
    def list_by_type(component_type: str, tenant_id: int) -> list[dict[str, Any]]:
        query = text(
            """
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE tenant_id = :tenant_id
              AND component_type = :component_type
              AND status = 1
              AND is_deleted = 0
            ORDER BY sort_order, id
        """
        )

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(
                    query,
                    {
                        "tenant_id": tenant_id,
                        "component_type": component_type,
                    },
                )
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"Get {component_type} Type component failed: {e}")
            return []
