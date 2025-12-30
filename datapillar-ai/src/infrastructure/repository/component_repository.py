"""
ETL 组件数据访问层（Component Repository）
负责 job_component 表的查询操作
"""

from typing import List, Dict, Any, Optional
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)

from src.infrastructure.database.mysql import MySQLClient


class ComponentRepository:
    """ETL 组件数据访问"""

    @staticmethod
    def list_active_components() -> List[Dict[str, Any]]:
        """
        获取所有激活的 ETL 组件

        Returns:
            组件配置列表
        """
        query = text("""
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE status = 1 AND is_deleted = 0
            ORDER BY sort_order, id
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query)
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"获取组件列表失败: {e}")
            return []

    @staticmethod
    def get_component_by_id(component_id: str) -> Optional[Dict[str, Any]]:
        """
        根据组件ID获取组件配置

        Args:
            component_id: 组件ID（component_code）

        Returns:
            组件配置字典，不存在返回 None
        """
        query = text("""
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE component_code = :component_code AND status = 1 AND is_deleted = 0
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"component_code": component_id})
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取组件 {component_id} 失败: {e}")
            return None

    @staticmethod
    def get_components_by_type(component_type: str) -> List[Dict[str, Any]]:
        """
        根据组件类型获取组件列表

        Args:
            component_type: 组件类型（ETL/SQL/SCRIPT）

        Returns:
            组件配置列表
        """
        query = text("""
            SELECT
                id,
                component_code,
                component_name,
                component_type,
                job_params,
                description
            FROM job_component
            WHERE component_type = :component_type AND status = 1 AND is_deleted = 0
            ORDER BY sort_order, id
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"component_type": component_type})
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"获取 {component_type} 类型组件失败: {e}")
            return []
