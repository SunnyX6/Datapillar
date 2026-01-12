"""
模型配置管理

管理 LLM 模型配置，支持多种方式：
- 代码配置（configure 时设置）
- 环境变量
- 配置文件
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass, field
from typing import Any

logger = logging.getLogger(__name__)


@dataclass
class ModelConfig:
    """模型配置"""

    id: str
    """模型 ID"""

    name: str
    """模型显示名称"""

    provider: str
    """提供商：openai, claude, glm, openrouter, ollama"""

    model_name: str
    """模型名称（如 gpt-4o）"""

    api_key: str
    """API Key"""

    base_url: str | None = None
    """Base URL（可选）"""

    is_default: bool = False
    """是否为默认模型"""

    embedding_dimension: int | None = None
    """Embedding 维度（仅 embedding 模型）"""

    config_json: dict[str, Any] = field(default_factory=dict)
    """额外配置（如 pricing）"""

    def __repr__(self) -> str:
        return f"<ModelConfig {self.provider}/{self.model_name}>"

    @property
    def supports_function_calling(self) -> bool:
        """是否支持 function calling"""
        from datapillar_oneagentic.utils.structured_output import ModelCapabilities
        return ModelCapabilities.supports_function_calling(self.provider, self.config_json)

    @property
    def supports_structured_output(self) -> bool:
        """是否支持 structured output"""
        from datapillar_oneagentic.utils.structured_output import ModelCapabilities
        return ModelCapabilities.supports_structured_output(self.provider, self.config_json)


class ModelManager:
    """
    模型配置管理器

    支持多种配置来源：
    1. configure() 时设置的 llm_provider
    2. 环境变量（OPENAI_API_KEY 等）
    3. 配置字典
    """

    _instance: ModelManager | None = None
    _models: dict[str, ModelConfig] = {}
    _default_chat_model_id: str | None = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._models = {}
            cls._instance._default_chat_model_id = None
        return cls._instance

    def register_model(
        self,
        *,
        provider: str,
        model_name: str,
        api_key: str,
        base_url: str | None = None,
        is_default: bool = False,
        model_id: str | None = None,
        config_json: dict[str, Any] | None = None,
    ) -> ModelConfig:
        """
        注册模型配置

        参数：
        - provider: 提供商（openai, claude, glm, openrouter, ollama）
        - model_name: 模型名称
        - api_key: API Key
        - base_url: Base URL（可选）
        - is_default: 是否为默认模型
        - model_id: 模型 ID（可选，默认自动生成）
        - config_json: 额外配置

        返回：
        - ModelConfig 实例
        """
        model_id = model_id or f"{provider}_{model_name}"

        config = ModelConfig(
            id=model_id,
            name=f"{provider}/{model_name}",
            provider=provider,
            model_name=model_name,
            api_key=api_key,
            base_url=base_url,
            is_default=is_default,
            config_json=config_json or {},
        )

        self._models[model_id] = config

        if is_default:
            self._default_chat_model_id = model_id

        logger.info(f"注册模型: {config}")
        return config

    def get_model(self, model_id: str) -> ModelConfig | None:
        """根据 ID 获取模型配置"""
        return self._models.get(model_id)

    def default_chat_model(self) -> ModelConfig | None:
        """
        获取默认 Chat 模型

        优先级：
        1. 已注册的默认模型
        2. 从环境变量自动创建
        3. 从 configure() 配置创建
        """
        # 已注册的默认模型
        if self._default_chat_model_id:
            return self._models.get(self._default_chat_model_id)

        # 尝试从环境变量自动创建
        model = self._create_from_env()
        if model:
            return model

        # 从 configure() 配置创建
        model = self._create_from_config()
        if model:
            return model

        logger.warning("未找到默认的 Chat 模型配置")
        return None

    def _create_from_env(self) -> ModelConfig | None:
        """从环境变量创建模型配置"""
        # OpenAI
        openai_key = os.environ.get("OPENAI_API_KEY")
        if openai_key:
            return self.register_model(
                provider="openai",
                model_name=os.environ.get("OPENAI_MODEL", "gpt-4o"),
                api_key=openai_key,
                base_url=os.environ.get("OPENAI_BASE_URL"),
                is_default=True,
                model_id="env_openai",
            )

        # Anthropic
        anthropic_key = os.environ.get("ANTHROPIC_API_KEY")
        if anthropic_key:
            return self.register_model(
                provider="claude",
                model_name=os.environ.get("ANTHROPIC_MODEL", "claude-3-opus-20240229"),
                api_key=anthropic_key,
                base_url=os.environ.get("ANTHROPIC_BASE_URL"),
                is_default=True,
                model_id="env_anthropic",
            )

        # GLM
        glm_key = os.environ.get("GLM_API_KEY") or os.environ.get("ZHIPU_API_KEY")
        if glm_key:
            return self.register_model(
                provider="glm",
                model_name=os.environ.get("GLM_MODEL", "glm-4.7"),
                api_key=glm_key,
                base_url=os.environ.get("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/"),
                is_default=True,
                model_id="env_glm",
            )

        return None

    def _create_from_config(self) -> ModelConfig | None:
        """从 datapillar.llm 配置创建模型配置"""
        # 延迟导入避免循环依赖
        from datapillar_oneagentic.config import datapillar

        llm_config = datapillar.llm
        if not llm_config.api_key or not llm_config.model:
            return None

        return self.register_model(
            provider="openai",  # OpenAI 兼容接口
            model_name=llm_config.model,
            api_key=llm_config.api_key,
            base_url=llm_config.base_url,
            is_default=True,
            model_id="config_llm",
        )

    def list_models(self) -> list[ModelConfig]:
        """列出所有已注册的模型"""
        return list(self._models.values())

    def clear(self) -> None:
        """清空所有模型配置（测试用）"""
        self._models.clear()
        self._default_chat_model_id = None


# 全局单例
model_manager = ModelManager()
