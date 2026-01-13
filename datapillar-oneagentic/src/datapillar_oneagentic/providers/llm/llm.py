"""
LLM 统一调用层

支持 OpenAI、Anthropic、GLM、DeepSeek、OpenRouter、Ollama

特性：
- 统一接口，屏蔽模型差异
- 内置弹性机制（超时 + 重试 + 熔断）
- 可选缓存
- Token 使用量追踪
"""

import asyncio
import logging
import os
import threading
from dataclasses import dataclass, field
from typing import Any, Dict, Type, TypeVar, cast

from langchain_core.globals import set_llm_cache
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import (
    AIMessageChunk,
    BaseMessage,
    BaseMessageChunk,
    ChatMessageChunk,
    HumanMessageChunk,
    SystemMessageChunk,
)
from langchain_core.runnables import Runnable
from pydantic import BaseModel


# ==================== GLM Thinking 模式 Monkey-Patch ====================


def _patched_convert_delta_to_message_chunk(
    dct: Dict[str, Any], default_class: Type[BaseMessageChunk]
) -> BaseMessageChunk:
    """
    修复版本（流式）：解析 GLM thinking 模式的 reasoning_content 字段

    智谱 API 开启 thinking 后返回格式：
    {
        "delta": {
            "role": "assistant",
            "reasoning_content": "思考内容...",
            "content": "最终回答..."
        }
    }

    将 reasoning_content 放入 additional_kwargs.reasoning_content
    """
    role = dct.get("role")
    content = dct.get("content", "")
    additional_kwargs: Dict[str, Any] = {}

    # 解析 tool_calls
    tool_calls = dct.get("tool_calls", None)
    if tool_calls is not None:
        additional_kwargs["tool_calls"] = tool_calls

    # 解析 reasoning_content（GLM thinking 模式）
    reasoning_content = dct.get("reasoning_content")
    if reasoning_content:
        additional_kwargs["reasoning_content"] = reasoning_content

    if role == "system" or default_class == SystemMessageChunk:
        return SystemMessageChunk(content=content)
    if role == "user" or default_class == HumanMessageChunk:
        return HumanMessageChunk(content=content)
    if role == "assistant" or default_class == AIMessageChunk:
        return AIMessageChunk(content=content, additional_kwargs=additional_kwargs)
    if role or default_class == ChatMessageChunk:
        return ChatMessageChunk(content=content, role=role)  # type: ignore[arg-type]
    return default_class(content=content)  # type: ignore[call-arg]


def _patched_convert_dict_to_message(dct: Dict[str, Any]) -> BaseMessage:
    """
    修复版本（非流式）：解析 GLM thinking 模式的 reasoning_content 字段

    智谱 API 开启 thinking 后返回格式：
    {
        "message": {
            "role": "assistant",
            "reasoning_content": "思考内容...",
            "content": "最终回答..."
        }
    }

    将 reasoning_content 放入 additional_kwargs.reasoning_content
    """
    from langchain_core.messages import (
        AIMessage,
        ChatMessage,
        HumanMessage,
        SystemMessage,
        ToolMessage,
    )

    role = dct.get("role")
    content = dct.get("content", "")

    if role == "system":
        return SystemMessage(content=content)
    if role == "user":
        return HumanMessage(content=content)
    if role == "assistant":
        additional_kwargs: Dict[str, Any] = {}
        tool_calls = dct.get("tool_calls", None)
        if tool_calls is not None:
            additional_kwargs["tool_calls"] = tool_calls
        # 解析 reasoning_content（GLM thinking 模式）
        reasoning_content = dct.get("reasoning_content")
        if reasoning_content:
            additional_kwargs["reasoning_content"] = reasoning_content
        return AIMessage(content=content, additional_kwargs=additional_kwargs)
    if role == "tool":
        additional_kwargs = {}
        if "name" in dct:
            additional_kwargs["name"] = dct["name"]
        return ToolMessage(
            content=content,
            tool_call_id=dct.get("tool_call_id"),
            additional_kwargs=additional_kwargs,
        )
    return ChatMessage(role=role, content=content)  # type: ignore[arg-type]


def _patch_zhipuai_thinking() -> None:
    """应用 GLM thinking 模式的 monkey-patch"""
    try:
        import langchain_community.chat_models.zhipuai as zhipuai_module
        zhipuai_module._convert_delta_to_message_chunk = _patched_convert_delta_to_message_chunk
        zhipuai_module._convert_dict_to_message = _patched_convert_dict_to_message
    except ImportError:
        pass


from datapillar_oneagentic.providers.llm.config import LLMConfig
from datapillar_oneagentic.providers.llm.llm_cache import create_llm_cache
from datapillar_oneagentic.providers.llm.usage_tracker import (
    estimate_cost_usd,
    estimate_usage,
    extract_usage,
    parse_pricing,
)
from datapillar_oneagentic.resilience import (
    CircuitBreakerError,
    ErrorClassifier,
    get_circuit_breaker,
    get_llm_timeout,
    with_retry,
)

logger = logging.getLogger(__name__)

# 模块加载时自动应用 GLM thinking patch
_patch_zhipuai_thinking()

T = TypeVar('T', bound=BaseModel)


# ==================== LLM 配置数据类 ====================


@dataclass
class LLMProviderConfig:
    """LLM Provider 配置"""

    provider: str
    model_name: str
    api_key: str
    base_url: str | None = None
    enable_thinking: bool = False
    thinking_budget_tokens: int | None = None
    config_json: dict[str, Any] = field(default_factory=dict)


# ==================== 全局 LLM 缓存（懒加载）====================


_llm_cache_initialized = False
_llm_cache_init_lock = threading.Lock()


def _init_llm_cache() -> None:
    """初始化 LLM 缓存（懒加载，线程安全）"""
    global _llm_cache_initialized
    if _llm_cache_initialized:
        return

    with _llm_cache_init_lock:
        if _llm_cache_initialized:
            return

        cache_instance = create_llm_cache()
        if cache_instance is not None:
            set_llm_cache(cache_instance)
            logger.info("LLM 缓存已启用")

        # 只有初始化成功后才设置标志位
        _llm_cache_initialized = True


# ==================== LLM 实例缓存 ====================

_llm_instance_cache: dict[tuple, Any] = {}
_llm_instance_cache_lock = threading.Lock()
_LLM_CACHE_MAX_SIZE = 50  # 最大缓存实例数


def _cleanup_llm_cache_if_needed() -> None:
    """如果缓存超限，清理最旧的一半（需持有锁）"""
    if len(_llm_instance_cache) >= _LLM_CACHE_MAX_SIZE:
        keys = list(_llm_instance_cache.keys())
        for key in keys[: len(keys) // 2]:
            _llm_instance_cache.pop(key, None)
        logger.info(f"LLM 实例缓存清理: {len(keys) // 2} 个实例")


# ==================== 弹性包装器 ====================


class ResilientChatModel:
    """
    弹性 LLM 包装器

    包装 LangChain ChatModel，添加：
    - 限流控制（RPM + 并发数）
    - 超时控制
    - 自动重试（可重试错误）
    - 熔断保护
    - Token 使用量追踪

    对上层透明，保持 LangChain Runnable 接口。
    """

    def __init__(
        self,
        llm: BaseChatModel | Runnable,
        *,
        provider: str | None = None,
        model_name: str | None = None,
        config_json: dict | None = None,
    ):
        self._llm = llm
        self._circuit_breaker = get_circuit_breaker("llm")
        self._provider = provider or "unknown"
        self._model_name = model_name
        self._config_json = config_json
        self._pricing = parse_pricing(config_json) if config_json else None

    @property
    def timeout(self) -> float:
        return get_llm_timeout()

    async def ainvoke(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """异步调用 LLM（带弹性保护）"""
        from datapillar_oneagentic.providers.llm.rate_limiter import rate_limit_manager

        async with rate_limit_manager.acquire(self._provider):
            return await self._invoke_with_resilience(input, config, **kwargs)

    @with_retry()
    async def _invoke_with_resilience(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """带重试的调用（内部方法）"""
        if not await self._circuit_breaker.allow_request():
            raise CircuitBreakerError("llm", "LLM 服务熔断中")

        try:
            result = await asyncio.wait_for(
                self._llm.ainvoke(input, config, **kwargs),
                timeout=self.timeout,
            )
            await self._circuit_breaker.record_success()
            asyncio.create_task(self._track_usage_async(input, result))
            return result

        except TimeoutError:
            await self._circuit_breaker.record_failure()
            raise TimeoutError(f"LLM 调用超时（{self.timeout}s）") from None

        except Exception as e:
            if ErrorClassifier.is_retryable(e):
                await self._circuit_breaker.record_failure()
            raise

    async def _track_usage_async(self, input: Any, result: Any) -> None:
        """异步追踪 Token 使用量"""
        try:
            usage = extract_usage(result)

            if usage is None and isinstance(result, dict):
                raw = result.get("raw")
                if raw:
                    usage = extract_usage(raw)

            if usage is None:
                prompt_messages = input if isinstance(input, list) else None
                completion_text = None
                if isinstance(result, dict):
                    raw = result.get("raw")
                    if raw:
                        completion_text = getattr(raw, "content", None)
                else:
                    completion_text = getattr(result, "content", None)

                usage = estimate_usage(
                    prompt_messages=prompt_messages,
                    completion_text=completion_text,
                )

            cost = estimate_cost_usd(usage=usage, pricing=self._pricing)

        except Exception as e:
            logger.warning(f"Usage 追踪失败（不影响主流程）: {e}")

    def invoke(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """同步调用（兼容接口）"""
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            loop = None

        if loop and loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as executor:
                future = executor.submit(asyncio.run, self.ainvoke(input, config, **kwargs))
                return future.result()
        else:
            return asyncio.run(self.ainvoke(input, config, **kwargs))

    def bind(self, **kwargs) -> "ResilientChatModel":
        """绑定参数"""
        if hasattr(self._llm, "bind"):
            bound = self._llm.bind(**kwargs)
            return ResilientChatModel(
                bound,
                provider=self._provider,
                model_name=self._model_name,
                config_json=self._config_json,
            )
        return self

    def bind_tools(self, tools: list, **kwargs) -> "ResilientChatModel":
        """绑定工具（保留原有的 thinking 等参数）"""
        if hasattr(self._llm, "bind_tools"):
            # 保留原有的 kwargs（如 thinking）
            original_kwargs = {}
            if hasattr(self._llm, "kwargs"):
                original_kwargs = dict(self._llm.kwargs)

            bound = self._llm.bind_tools(tools, **kwargs)

            # 如果原来有 thinking 参数，重新绑定
            if "thinking" in original_kwargs and hasattr(bound, "bind"):
                bound = bound.bind(thinking=original_kwargs["thinking"])

            return ResilientChatModel(
                bound,
                provider=self._provider,
                model_name=self._model_name,
                config_json=self._config_json,
            )
        return self

    def with_structured_output(
        self,
        schema: type[BaseModel],
        **kwargs,
    ) -> "ResilientChatModel":
        """绑定结构化输出"""
        if hasattr(self._llm, "with_structured_output"):
            method = kwargs.get("method", "function_calling")

            # GLM (ChatZhipuAI) 只支持 function_calling，自动转换
            if self._provider and self._provider.lower() == "glm" and method == "json_mode":
                kwargs["method"] = "function_calling"

            bound = self._llm.with_structured_output(schema, **kwargs)

            return ResilientChatModel(
                bound,
                provider=self._provider,
                model_name=self._model_name,
                config_json=self._config_json,
            )
        return self

    def __getattr__(self, name: str) -> Any:
        """代理其他属性到底层 LLM"""
        return getattr(self._llm, name)


# ==================== LLM 工厂 ====================


class LLMFactory:
    """LLM 工厂类 - 根据配置创建 LLM 实例"""

    @staticmethod
    def create_chat_model(config: LLMProviderConfig) -> Any:
        """创建 LangChain ChatModel 实例"""
        provider = config.provider.lower()

        if provider == "openai":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            return ChatOpenAI(
                api_key=config.api_key,
                base_url=config.base_url,
                model=config.model_name,
                streaming=False,
            )

        elif provider in ("claude", "anthropic"):
            from langchain_anthropic import ChatAnthropic as _ChatAnthropic
            ChatAnthropic = cast(Any, _ChatAnthropic)
            llm = ChatAnthropic(
                api_key=config.api_key,
                base_url=config.base_url if config.base_url else None,
                model=config.model_name,
                streaming=False,
            )
            # Claude thinking 模式
            if config.enable_thinking:
                thinking_config = {"type": "enabled"}
                if config.thinking_budget_tokens:
                    thinking_config["budget_tokens"] = config.thinking_budget_tokens
                return llm.bind(thinking=thinking_config)
            return llm

        elif provider == "glm":
            from langchain_community.chat_models import ChatZhipuAI
            llm = ChatZhipuAI(
                zhipuai_api_key=config.api_key,
                model_name=config.model_name,
                streaming=False,
            )
            # GLM thinking 模式
            thinking_type = "enabled" if config.enable_thinking else "disabled"
            return llm.bind(thinking={"type": thinking_type})

        elif provider == "deepseek":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            llm = ChatOpenAI(
                api_key=config.api_key,
                base_url=config.base_url or "https://api.deepseek.com/v1",
                model=config.model_name,
                streaming=False,
            )
            # DeepSeek thinking 模式（通过 extra_body）
            if config.enable_thinking:
                return llm.bind(extra_body={"enable_thinking": True})
            return llm

        elif provider == "openrouter":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            return ChatOpenAI(
                api_key=config.api_key,
                base_url=config.base_url or "https://openrouter.ai/api/v1",
                model=config.model_name,
                streaming=False,
            )

        elif provider == "ollama":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            return ChatOpenAI(
                api_key="ollama",
                base_url=config.base_url or "http://localhost:11434/v1",
                model=config.model_name,
                streaming=False,
            )

        else:
            raise ValueError(f"不支持的 LLM 提供商: {provider}")


# ==================== 配置获取 ====================


def _get_llm_config() -> LLMProviderConfig | None:
    """
    获取 LLM 配置

    优先级：
    1. datapillar_configure() 配置
    2. 环境变量
    """
    # 1. 从 datapillar 配置获取
    try:
        from datapillar_oneagentic.config import datapillar

        llm_config: LLMConfig = datapillar.llm
        if llm_config.is_configured():
            return LLMProviderConfig(
                provider=llm_config.provider,
                model_name=llm_config.model,
                api_key=llm_config.api_key,
                base_url=llm_config.base_url,
                enable_thinking=llm_config.enable_thinking,
                thinking_budget_tokens=llm_config.thinking_budget_tokens,
            )
    except Exception:
        pass

    # 2. 从环境变量获取
    # OpenAI
    openai_key = os.environ.get("OPENAI_API_KEY")
    if openai_key:
        return LLMProviderConfig(
            provider="openai",
            model_name=os.environ.get("OPENAI_MODEL", "gpt-4o"),
            api_key=openai_key,
            base_url=os.environ.get("OPENAI_BASE_URL"),
        )

    # Anthropic
    anthropic_key = os.environ.get("ANTHROPIC_API_KEY")
    if anthropic_key:
        return LLMProviderConfig(
            provider="anthropic",
            model_name=os.environ.get("ANTHROPIC_MODEL", "claude-3-opus-20240229"),
            api_key=anthropic_key,
            base_url=os.environ.get("ANTHROPIC_BASE_URL"),
        )

    # GLM
    glm_key = os.environ.get("GLM_API_KEY") or os.environ.get("ZHIPU_API_KEY")
    if glm_key:
        return LLMProviderConfig(
            provider="glm",
            model_name=os.environ.get("GLM_MODEL", "glm-4.7"),
            api_key=glm_key,
            base_url=os.environ.get("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/"),
        )

    # DeepSeek
    deepseek_key = os.environ.get("DEEPSEEK_API_KEY")
    if deepseek_key:
        return LLMProviderConfig(
            provider="deepseek",
            model_name=os.environ.get("DEEPSEEK_MODEL", "deepseek-chat"),
            api_key=deepseek_key,
            base_url=os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
        )

    return None


# ==================== 统一调用接口 ====================


def call_llm(
    output_schema: type[T] | None = None,
    **kwargs,
) -> ResilientChatModel:
    """
    统一的 LLM 获取接口

    返回带弹性能力的 LLM 实例：
    - 超时控制（默认 120s）
    - 自动重试（可重试错误最多 3 次）
    - 熔断保护（连续失败 5 次后熔断 60s）
    - Token 使用量追踪

    Args:
        output_schema: Pydantic 模型类，启用 structured output
        **kwargs: temperature, max_tokens 等额外参数

    Returns:
        ResilientChatModel 实例
    """
    _init_llm_cache()

    # 获取配置
    config = _get_llm_config()
    if not config:
        raise ValueError(
            "LLM 未配置！请通过以下方式配置：\n"
            "1. datapillar_configure(llm={...})\n"
            "2. 环境变量 OPENAI_API_KEY 等"
        )

    temperature = kwargs.get("temperature")
    max_tokens = kwargs.get("max_tokens")
    schema_key = f"{output_schema.__module__}.{output_schema.__qualname__}" if output_schema else None

    # 缓存 key 包含所有影响实例的参数
    cache_key = (
        config.provider,
        config.model_name,
        config.api_key,
        config.base_url,
        config.enable_thinking,
        temperature,
        max_tokens,
        schema_key,
    )

    # 双重检查锁定
    if cache_key in _llm_instance_cache:
        return _llm_instance_cache[cache_key]

    with _llm_instance_cache_lock:
        if cache_key in _llm_instance_cache:
            return _llm_instance_cache[cache_key]

        # 创建 LLM
        llm = LLMFactory.create_chat_model(config)
        logger.info(f"创建 LLM 实例: provider={config.provider}, model={config.model_name}")

        # 绑定参数
        bind_kwargs = {}
        if temperature is not None:
            bind_kwargs["temperature"] = temperature
        if max_tokens is not None:
            bind_kwargs["max_tokens"] = max_tokens

        if bind_kwargs and hasattr(llm, "bind"):
            llm = llm.bind(**bind_kwargs)

        # 应用 structured output
        if output_schema is not None:
            llm = llm.with_structured_output(output_schema, method="function_calling")

        # 包装为弹性模型
        resilient_llm = ResilientChatModel(
            llm,
            provider=config.provider,
            model_name=config.model_name,
            config_json=config.config_json,
        )

        _cleanup_llm_cache_if_needed()
        _llm_instance_cache[cache_key] = resilient_llm
        return resilient_llm


def clear_llm_cache() -> None:
    """清空 LLM 实例缓存（测试用）"""
    global _llm_instance_cache
    with _llm_instance_cache_lock:
        _llm_instance_cache.clear()
