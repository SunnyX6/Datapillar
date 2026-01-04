"""
LLM统一调用层 - 基于 LangChain 统一接口
支持 OpenAI、Claude、智谱GLM、OpenRouter、Ollama
GLM 使用 langchain-openai 的 ChatOpenAI（官方推荐方式）
"""

import logging
from typing import Any, TypeVar, cast

from langchain_core.globals import set_llm_cache
from pydantic import BaseModel

from src.infrastructure.llm.model_manager import model_manager
from src.infrastructure.llm.semantic_cache import create_semantic_cache

logger = logging.getLogger(__name__)


# ==================== 全局语义缓存（业务无关）====================
#
# 说明：
# - 只做"高相似直接命中"（不返回中等相似参考答案）
# - 只依赖 prompt + llm_string 推导缓存隔离边界（不引入任何业务字段）
set_llm_cache(create_semantic_cache())
logger.info("LLM 语义缓存已启用（通用语义缓存）")

T = TypeVar('T', bound=BaseModel)

# ==================== LLM 实例缓存 ====================
# 按 (model_id, temperature, max_tokens, json_mode) 缓存，避免重复创建
_llm_cache: dict[tuple, Any] = {}


# ==================== LLM工厂 ====================


class LLMFactory:
    """LLM工厂类 - 从数据库读取配置，返回原生SDK客户端"""

    @staticmethod
    def create_chat_model(model_id: int | None = None) -> Any:
        """
        创建LangChain ChatModel实例（支持with_structured_output）

        Args:
            model_id: 指定模型ID，如果为None则使用默认Chat模型

        Returns:
            LangChain ChatModel实例 (ChatOpenAI/ChatAnthropic/ChatZhipuAI)
        """
        if model_id:
            model = model_manager.model_by_id(model_id)
        else:
            model = model_manager.default_chat_model()

        if not model:
            raise ValueError("未找到可用的Chat模型配置，请在数据库中配置")

        provider = model.provider.lower()

        if provider == "openai":
            from langchain_openai import ChatOpenAI as _ChatOpenAI

            ChatOpenAI = cast(Any, _ChatOpenAI)

            return ChatOpenAI(
                api_key=model.api_key,
                base_url=model.base_url,
                model=model.model_name,
                streaming=False,
            )

        elif provider == "claude":
            from langchain_anthropic import ChatAnthropic as _ChatAnthropic

            ChatAnthropic = cast(Any, _ChatAnthropic)

            return ChatAnthropic(
                api_key=model.api_key,
                base_url=model.base_url if model.base_url else None,
                model=model.model_name,
                streaming=False,
            )

        elif provider == "glm":
            # GLM 兼容 OpenAI API，直接用 ChatOpenAI
            from langchain_openai import ChatOpenAI as _ChatOpenAI

            ChatOpenAI = cast(Any, _ChatOpenAI)

            return ChatOpenAI(
                api_key=model.api_key,
                base_url=model.base_url or "https://open.bigmodel.cn/api/paas/v4/",
                model=model.model_name,
                streaming=False,
                extra_body={"thinking": {"type": "disabled"}},  # 关闭 GLM 思考模式
            )

        elif provider == "openrouter":
            from langchain_openai import ChatOpenAI as _ChatOpenAI

            ChatOpenAI = cast(Any, _ChatOpenAI)

            return ChatOpenAI(
                api_key=model.api_key,
                base_url=model.base_url or "https://openrouter.ai/api/v1",
                model=model.model_name,
                streaming=False,
            )

        elif provider == "ollama":
            from langchain_openai import ChatOpenAI as _ChatOpenAI

            ChatOpenAI = cast(Any, _ChatOpenAI)

            return ChatOpenAI(
                api_key="ollama",
                base_url=model.base_url or "http://localhost:11434/v1",
                model=model.model_name,
                streaming=False,
            )

        else:
            raise ValueError(f"不支持的Chat模型提供商: {provider}")


# ==================== 统一调用接口（基于LangChain）====================


def call_llm(model_id: int | None = None, enable_json_mode: bool = False, **kwargs) -> Any:
    """
    统一的LLM获取接口（屏蔽模型差异，带缓存）

    Args:
        model_id: 模型ID（None=使用默认模型）
        enable_json_mode: 是否启用JSON模式
        **kwargs: temperature, max_tokens等额外参数

    Returns:
        配置好的 LangChain ChatModel 实例（缓存复用）
    """
    # 构建缓存键
    temperature = kwargs.get("temperature")
    max_tokens = kwargs.get("max_tokens")
    cache_key = (model_id, temperature, max_tokens, enable_json_mode)

    if cache_key in _llm_cache:
        return _llm_cache[cache_key]

    # 创建基础 LLM
    llm = LLMFactory.create_chat_model(model_id)
    logger.info(f"创建 LLM 实例: model_id={model_id}, temp={temperature}, json={enable_json_mode}")

    # 构建 bind 参数
    bind_kwargs = {}
    if temperature is not None:
        bind_kwargs["temperature"] = temperature
    if max_tokens is not None:
        bind_kwargs["max_tokens"] = max_tokens
    if enable_json_mode:
        bind_kwargs["response_format"] = {"type": "json_object"}

    if bind_kwargs and hasattr(llm, "bind"):
        llm = llm.bind(**bind_kwargs)

    # 缓存并返回
    _llm_cache[cache_key] = llm
    return llm
