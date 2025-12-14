"""
ETL 组件数据访问层（Component Repository）
负责 job_component 表的查询操作
"""

from typing import List, Dict, Any, Optional
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)

from src.config.connection import MySQLClient


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
                component_id,
                component_name,
                component_type,
                category,
                description,
                config_schema
            FROM job_component
            WHERE status = 'ACTIVE'
            ORDER BY component_type, component_id
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
            component_id: 组件ID

        Returns:
            组件配置字典，不存在返回 None
        """
        query = text("""
            SELECT
                component_id,
                component_name,
                component_type,
                category,
                description,
                config_schema
            FROM job_component
            WHERE component_id = :component_id AND status = 'ACTIVE'
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"component_id": component_id})
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
                component_id,
                component_name,
                component_type,
                category,
                description,
                config_schema
            FROM job_component
            WHERE component_type = :component_type AND status = 'ACTIVE'
            ORDER BY component_id
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"component_type": component_type})
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"获取 {component_type} 类型组件失败: {e}")
            return []
