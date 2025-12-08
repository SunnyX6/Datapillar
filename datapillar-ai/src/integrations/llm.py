"""
LLM统一调用层 - 原生SDK + 统一接口
合并了原app/config/llm_factory.py和app/config/llm_caller.py
支持OpenAI、Claude、智谱GLM、OpenRouter、Ollama
支持LangChain ChatModel包装器 + 结构化输出
"""

from typing import Optional, Any, List, Dict, TypeVar, Union, Literal
import logging

logger = logging.getLogger(__name__)
from pydantic import BaseModel

from src.core.config import model_manager, ModelConfig
from langchain_core.language_models.base import LanguageModelInput
from src.integrations.glm import GlmNativeChatModel
from langchain_core.language_models.chat_models import BaseChatModel

T = TypeVar('T', bound=BaseModel)

# ==================== LLM工厂 ====================

class LLMFactory:
    """LLM工厂类 - 从数据库读取配置，返回原生SDK客户端"""

    @staticmethod
    def create_langchain_chat_model(model_id: Optional[int] = None) -> Any:
        """
        创建LangChain ChatModel实例（支持with_structured_output）

        Args:
            model_id: 指定模型ID，如果为None则使用默认Chat模型

        Returns:
            LangChain ChatModel实例 (ChatOpenAI/ChatAnthropic/ChatZhipuAI)
        """
        if model_id:
            model = model_manager.get_model_by_id(model_id)
        else:
            model = model_manager.get_default_chat_model()

        if not model:
            raise ValueError("未找到可用的Chat模型配置，请在数据库中配置")

        logger.info(f"创建LangChain ChatModel: {model.name} ({model.provider}/{model.model_name})")

        provider = model.provider.lower()

        if provider == "openai":
            from langchain_openai import ChatOpenAI
            return ChatOpenAI(
                api_key=model.api_key,
                base_url=model.base_url,
                model=model.model_name,
            )

        elif provider == "claude":
            from langchain_anthropic import ChatAnthropic
            return ChatAnthropic(
                api_key=model.api_key,
                base_url=model.base_url if model.base_url else None,
                model=model.model_name,
            )

        elif provider == "glm":
            # 使用官方 GLM 接口 + LangChain BaseChatModel 适配器
            return GlmNativeChatModel(
                api_key=model.api_key,
                base_url=model.base_url or "https://open.bigmodel.cn/api/paas/v4/",
                model=model.model_name,
            )

        elif provider == "openrouter":
            from langchain_openai import ChatOpenAI
            return ChatOpenAI(
                api_key=model.api_key,
                base_url=model.base_url or "https://openrouter.ai/api/v1",
                model=model.model_name,
            )

        elif provider == "ollama":
            from langchain_openai import ChatOpenAI
            return ChatOpenAI(
                api_key="ollama",
                base_url=model.base_url or "http://localhost:11434/v1",
                model=model.model_name,
            )

        else:
            raise ValueError(f"不支持的Chat模型提供商: {provider}")


# ==================== 内部辅助函数 ====================

def get_langchain_chat_model(model_id: Optional[int] = None) -> Any:
    """获取LangChain ChatModel实例（内部使用）"""
    return LLMFactory.create_langchain_chat_model(model_id)


# ==================== 统一调用接口（基于LangChain）====================

def call_llm(
    model_id: int = None,
    enable_json_mode: bool = False,
    **kwargs
) -> Any:
    """
    统一的LLM获取接口（屏蔽模型差异）

    作用：返回配置好的 LLM 实例，屏蔽不同模型的差异性
    上层自己决定如何使用（bind_tools、with_structured_output等）

    Args:
        model_id: 模型ID（None=使用默认模型）
        enable_json_mode: 是否启用JSON模式（仅用于structured output，会禁用工具调用）
        **kwargs: temperature, max_tokens等额外参数

    Returns:
        配置好的 LangChain ChatModel 实例

    GLM特殊处理:
        - 禁用thinking: extra_body={"thinking": {"type": "disabled"}}
        - enable_json_mode=True时启用response_format（禁用工具调用）

    Example:
        # 基础使用
        llm = call_llm()
        result = await llm.ainvoke([HumanMessage(content="...")])

        # 绑定工具（不要启用json_mode）
        llm = call_llm()
        llm_with_tools = llm.bind_tools([tool1, tool2])

        # 结构化输出（需要启用json_mode）
        llm = call_llm(enable_json_mode=True)
        llm_structured = llm.with_structured_output(MySchema)
    """
    # 获取模型配置
    if model_id:
        model_config = model_manager.get_model_by_id(model_id)
    else:
        model_config = model_manager.get_default_chat_model()

    is_glm = model_config.provider.lower() == "glm"
    llm = get_langchain_chat_model(model_id)

    # GLM：用官方适配器，直接重建实例以应用 json_mode / temperature
    if is_glm and isinstance(llm, GlmNativeChatModel):
        temp = kwargs.get("temperature", llm.temperature)
        max_tokens = kwargs.get("max_tokens", llm.max_tokens)
        llm = GlmNativeChatModel(
            api_key=llm.api_key,
            base_url=llm.base_url,
            model=llm.model_name,
            temperature=temp,
            max_tokens=max_tokens,
            json_mode=enable_json_mode,
            bound_tools=llm.bound_tools,
        )
    else:
        # 其他模型沿用 LangChain ChatModel 的 bind
        bind_kwargs = {}
        if "temperature" in kwargs:
            bind_kwargs["temperature"] = kwargs["temperature"]
        if "max_tokens" in kwargs:
            bind_kwargs["max_tokens"] = kwargs["max_tokens"]
        if enable_json_mode:
            bind_kwargs["response_format"] = {"type": "json_object"}

        if bind_kwargs and hasattr(llm, "bind"):
            llm = llm.bind(**bind_kwargs)

    return llm
