"""
AI 模型数据访问层（Model Repository）
负责 ai_model 表的所有查询操作
"""

from typing import Optional, List, Dict, Any
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)

from src.config.connection import MySQLClient


class ModelRepository:
    """AI 模型数据访问"""

    @staticmethod
    def get_default_chat_model() -> Optional[Dict[str, Any]]:
        """
        获取默认的 Chat 模型

        Returns:
            模型配置字典，如果未找到则返回 None
        """
        query = text("""
            SELECT * FROM ai_model
            WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'chat'
            LIMIT 1
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query)
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取默认 Chat 模型失败: {e}")
            return None

    @staticmethod
    def get_default_embedding_model() -> Optional[Dict[str, Any]]:
        """
        获取默认的 Embedding 模型

        Returns:
            模型配置字典，如果未找到则返回 None
        """
        query = text("""
            SELECT * FROM ai_model
            WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'embedding'
            LIMIT 1
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query)
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取默认 Embedding 模型失败: {e}")
            return None

    @staticmethod
    def get_model_by_id(model_id: int) -> Optional[Dict[str, Any]]:
        """
        根据 ID 获取模型配置

        Args:
            model_id: 模型 ID

        Returns:
            模型配置字典，如果未找到则返回 None
        """
        query = text("""
            SELECT * FROM ai_model
            WHERE id = :id AND is_enabled = 1
        """)

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, {"id": model_id})
                row = result.mappings().fetchone()
                return dict(row) if row else None
        except Exception as e:
            logger.error(f"获取模型 #{model_id} 失败: {e}")
            return None

    @staticmethod
    def list_enabled_models(model_type: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        列出所有启用的模型

        Args:
            model_type: 模型类型筛选（chat/embedding），None 表示不筛选

        Returns:
            模型配置列表
        """
        if model_type:
            query = text("""
                SELECT * FROM ai_model
                WHERE is_enabled = 1 AND model_type = :model_type
                ORDER BY is_default DESC, created_at DESC
            """)
            params = {"model_type": model_type}
        else:
            query = text("""
                SELECT * FROM ai_model
                WHERE is_enabled = 1
                ORDER BY model_type, is_default DESC, created_at DESC
            """)
            params = {}

        try:
            with MySQLClient.get_engine().connect() as conn:
                result = conn.execute(query, params)
                return [dict(row) for row in result.mappings()]
        except Exception as e:
            logger.error(f"列出启用模型失败: {e}")
            return []
