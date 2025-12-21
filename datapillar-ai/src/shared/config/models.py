"""
LLM 模型配置管理
负责从 MySQL 的 ai_model 表读取模型配置
使用 ModelRepository 进行数据访问
"""

from typing import Optional, Dict, Any
import logging

logger = logging.getLogger(__name__)

from src.infrastructure.repository import ModelRepository


class ModelConfig:
    """模型配置"""

    def __init__(self, row: Dict[str, Any]):
        self.id = row["id"]
        self.name = row["name"]
        self.provider = row["provider"]
        self.model_name = row["model_name"]
        self.model_type = row["model_type"]
        self.api_key = row["api_key"]
        self.base_url = row["base_url"]
        self.is_enabled = bool(row["is_enabled"])
        self.is_default = bool(row["is_default"])
        self.config_json = row.get("config_json")
        # Embedding模型专用属性
        self.embedding_dimension = row.get("embedding_dimension")

    def __repr__(self):
        return f"<ModelConfig {self.provider}/{self.model_name} ({self.model_type})>"


class ModelManager:
    """AI模型配置管理器（使用 ModelRepository）"""

    _instance = None

    def __new__(cls):
        """单例模式"""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def get_default_chat_model(self) -> Optional[ModelConfig]:
        """获取默认的Chat模型"""
        row = ModelRepository.get_default_chat_model()
        if row:
            return ModelConfig(row)
        logger.warning("未找到默认的Chat模型")
        return None

    def get_default_embedding_model(self) -> Optional[ModelConfig]:
        """获取默认的Embedding模型"""
        row = ModelRepository.get_default_embedding_model()
        if row:
            return ModelConfig(row)
        logger.warning("未找到默认的Embedding模型")
        return None

    def get_model_by_id(self, model_id: int) -> Optional[ModelConfig]:
        """根据ID获取模型配置"""
        row = ModelRepository.get_model_by_id(model_id)
        if row:
            return ModelConfig(row)
        return None

    def list_enabled_models(self, model_type: Optional[str] = None):
        """列出所有启用的模型"""
        rows = ModelRepository.list_enabled_models(model_type)
        return [ModelConfig(row) for row in rows]

    def get_embedding_dimension(self) -> int:
        """获取当前默认Embedding模型的向量维度"""
        model = self.get_default_embedding_model()
        if model and model.embedding_dimension:
            return model.embedding_dimension
        # 默认返回2048（智谱embedding-3）
        logger.warning("未找到Embedding模型维度配置，使用默认值2048")
        return 2048


# 全局单例
model_manager = ModelManager()
