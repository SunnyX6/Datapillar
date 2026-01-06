"""
LLM 模型配置管理（基础设施层）

说明：
- 模型配置来自 MySQL 系统表 `ai_model`，属于运行时依赖，不属于 `shared/config`（配置层）职责。
- 这里负责"读取 + 选择默认模型"的逻辑；纯数据访问由 `Model` 负责。
"""

from __future__ import annotations

import json
import logging
from typing import Any

from src.infrastructure.repository.system.ai_model import Model

logger = logging.getLogger(__name__)


class ModelConfig:
    """模型配置（ai_model 行映射）"""

    def __init__(self, row: dict[str, Any]):
        self.id = row["id"]
        self.name = row["name"]
        self.provider = row["provider"]
        self.model_name = row["model_name"]
        self.model_type = row["model_type"]
        self.api_key = row["api_key"]
        self.base_url = row["base_url"]
        self.is_enabled = bool(row["is_enabled"])
        self.is_default = bool(row["is_default"])
        self.embedding_dimension = row.get("embedding_dimension")

        # 解析 config_json
        config_json = row.get("config_json")
        if isinstance(config_json, str):
            try:
                self.config_json = json.loads(config_json) if config_json else {}
            except json.JSONDecodeError:
                self.config_json = {}
        else:
            self.config_json = config_json or {}

    def __repr__(self) -> str:
        return f"<ModelConfig {self.provider}/{self.model_name} ({self.model_type})>"

    @property
    def supports_function_calling(self) -> bool:
        """是否支持 function calling"""
        from src.infrastructure.llm.structured_output import ModelCapabilities

        return ModelCapabilities.supports_function_calling(self.provider, self.config_json)

    @property
    def supports_structured_output(self) -> bool:
        """是否支持 structured output"""
        from src.infrastructure.llm.structured_output import ModelCapabilities

        return ModelCapabilities.supports_structured_output(self.provider, self.config_json)


class ModelManager:
    """AI 模型配置管理器"""

    _instance: ModelManager | None = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def default_chat_model(self) -> ModelConfig | None:
        """获取默认 Chat 模型"""
        row = Model.get_chat_default()
        if row:
            return ModelConfig(row)
        logger.warning("未找到默认的Chat模型")
        return None

    def default_embed_model(self) -> ModelConfig | None:
        """获取默认 Embedding 模型"""
        row = Model.get_embedding_default()
        if row:
            return ModelConfig(row)
        logger.warning("未找到默认的Embedding模型")
        return None

    def model_by_id(self, model_id: int) -> ModelConfig | None:
        """根据 ID 获取模型配置"""
        row = Model.get_model(model_id)
        if row:
            return ModelConfig(row)
        return None

    def list_enabled_models(self, model_type: str | None = None) -> list[ModelConfig]:
        """列出所有启用的模型"""
        rows = Model.list_enabled_models(model_type)
        return [ModelConfig(row) for row in rows]

    def embedding_dim(self) -> int:
        """获取当前默认 Embedding 模型的向量维度"""
        model = self.default_embed_model()
        if model and model.embedding_dimension:
            return int(model.embedding_dimension)
        logger.warning("未找到Embedding模型维度配置，使用默认值2048")
        return 2048


model_manager = ModelManager()
