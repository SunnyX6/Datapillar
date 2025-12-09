"""
ETL 组件数据访问层（Component Repository）
负责 xxl_job_component 表的查询操作
"""

from typing import List, Dict, Any
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
                config_schema,
                supported_operations
            FROM xxl_job_component
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
