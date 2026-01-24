"""
LLM 统一调用层

支持 OpenAI、Anthropic、GLM、DeepSeek、OpenRouter、Ollama

特性：
- 统一接口，屏蔽模型差异
- 内置弹性机制（超时 + 重试 + 熔断）
- 可选缓存
- Token 使用量追踪
"""

from __future__ import annotations

import asyncio
import logging
import threading
import time
from dataclasses import dataclass
from typing import Any, TypeVar, cast

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

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.events import (
    EventBus,
    LLMCallCompletedEvent,
    LLMCallFailedEvent,
    LLMCallStartedEvent,
)
from datapillar_oneagentic.providers.llm.config import LLMConfig, RetryConfig
from datapillar_oneagentic.providers.llm.llm_cache import create_llm_cache
from datapillar_oneagentic.providers.llm.rate_limiter import RateLimitManager
from datapillar_oneagentic.providers.llm.usage_tracker import extract_usage
from datapillar_oneagentic.exception import (
    CircuitBreaker,
    CircuitBreakerRegistry,
    ExceptionMapper,
    LLMError,
    LLMErrorCategory,
    LLMErrorClassifier,
    RecoveryAction,
    calculate_retry_delay,
)

logger = logging.getLogger(__name__)

# ==================== GLM Thinking 模式 Monkey-Patch ====================


def _patched_convert_delta_to_message_chunk(
    dct: dict[str, Any], default_class: type[BaseMessageChunk]
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
    """
    role = dct.get("role")
    content = dct.get("content", "")
    additional_kwargs: dict[str, Any] = {}

    tool_calls = dct.get("tool_calls")
    if tool_calls is not None:
        additional_kwargs["tool_calls"] = tool_calls

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


def _patched_convert_dict_to_message(dct: dict[str, Any]) -> BaseMessage:
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
        additional_kwargs: dict[str, Any] = {}
        tool_calls = dct.get("tool_calls")
        if tool_calls is not None:
            additional_kwargs["tool_calls"] = tool_calls
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


# 模块加载时自动应用 GLM thinking patch
_patch_zhipuai_thinking()

T = TypeVar("T", bound=BaseModel)

def extract_thinking(message: Any) -> str | None:
    """
    从 LLM 消息中提取思考内容

    统一处理多模型格式：
    - GLM/DeepSeek: additional_kwargs.reasoning_content
    - Claude: content 中的 thinking blocks
    """
    if not hasattr(message, "additional_kwargs"):
        return None

    additional_kwargs = getattr(message, "additional_kwargs", None)
    if isinstance(additional_kwargs, dict):
        reasoning = additional_kwargs.get("reasoning_content")
        if reasoning:
            return reasoning

    content = getattr(message, "content", None)
    if isinstance(content, list):
        thinking_parts = []
        for block in content:
            if isinstance(block, dict) and block.get("type") == "thinking":
                thinking_parts.append(block.get("thinking", ""))
        if thinking_parts:
            return "\n".join(thinking_parts)

    return None


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
    """

    def __init__(
        self,
        llm: BaseChatModel | Runnable,
        *,
        provider: str | None = None,
        model_name: str | None = None,
        event_bus: EventBus | None = None,
        event_agent_id: str | None = None,
        event_key: SessionKey | None = None,
        rate_limit_manager: RateLimitManager | None = None,
        circuit_breaker: CircuitBreaker | None = None,
        timeout_seconds: float | None = None,
        retry_config: RetryConfig | None = None,
    ):
        self._llm = llm
        self._provider = provider or "unknown"
        self._model_name = model_name
        self._event_bus = event_bus
        self._event_agent_id = event_agent_id
        self._event_key = event_key
        self._rate_limit_manager = rate_limit_manager
        self._circuit_breaker = circuit_breaker
        self._timeout_seconds = timeout_seconds or 120.0
        self._retry_config = retry_config or RetryConfig()

    @property
    def timeout(self) -> float:
        return self._timeout_seconds

    def with_event_context(
        self,
        *,
        agent_id: str | None,
        key: SessionKey | None,
    ) -> "ResilientChatModel":
        """绑定事件上下文（Agent + Session）"""
        return ResilientChatModel(
            self._llm,
            provider=self._provider,
            model_name=self._model_name,
            event_bus=self._event_bus,
            event_agent_id=agent_id,
            event_key=key,
            rate_limit_manager=self._rate_limit_manager,
            circuit_breaker=self._circuit_breaker,
            timeout_seconds=self._timeout_seconds,
            retry_config=self._retry_config,
        )

    async def ainvoke(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """异步调用 LLM（带弹性保护）"""
        if self._rate_limit_manager is None:
            return await self._invoke_with_resilience(input, config, **kwargs)

        async with self._rate_limit_manager.acquire(self._provider):
            return await self._invoke_with_resilience(input, config, **kwargs)

    async def _invoke_with_resilience(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """带重试的调用（内部方法）"""
        retries = self._retry_config.max_retries
        last_error: Exception | None = None
        message_count = len(input) if isinstance(input, list) else 0

        for attempt in range(retries + 1):
            if self._circuit_breaker and not await self._circuit_breaker.allow_request():
                raise LLMError(
                    "LLM 服务熔断中",
                    category=LLMErrorCategory.CIRCUIT_OPEN,
                    action=RecoveryAction.CIRCUIT_BREAK,
                    provider=self._provider,
                    model=self._model_name,
                )

            start_time = time.time()
            if self._event_bus is not None:
                await self._event_bus.emit(
                    self,
                    LLMCallStartedEvent(
                        agent_id=self._event_agent_id or "",
                        key=self._event_key,
                        model=self._model_name or "",
                        message_count=message_count,
                    ),
                )

            try:
                result = await asyncio.wait_for(
                    self._llm.ainvoke(input, config, **kwargs),
                    timeout=self.timeout,
                )
                # LangChain 的 with_structured_output(include_raw=True) 会返回 dict：
                # {"raw": BaseMessage, "parsed": ..., "parsing_error": ...}
                # 解析失败的日志由解析器在最终失败时输出，避免兜底成功时误报。
                if self._circuit_breaker:
                    await self._circuit_breaker.record_success()
                asyncio.create_task(self._track_usage_async(result, start_time=start_time))
                return result

            except TimeoutError as e:
                await self._emit_llm_failed(e, start_time=start_time)
                last_error = LLMError(
                    f"LLM 调用超时（{self.timeout}s）",
                    category=LLMErrorCategory.TIMEOUT,
                    action=RecoveryAction.RETRY,
                    provider=self._provider,
                    model=self._model_name,
                    original=e,
                )
                if self._circuit_breaker:
                    await self._circuit_breaker.record_failure()
                if attempt >= retries:
                    raise last_error from None

            except Exception as e:
                if isinstance(e, LLMError):
                    await self._emit_llm_failed(e, start_time=start_time)
                    last_error = e
                    if e.action == RecoveryAction.RETRY:
                        if self._circuit_breaker:
                            await self._circuit_breaker.record_failure()
                        if attempt >= retries:
                            raise
                    else:
                        raise
                elif ExceptionMapper.is_context_length_exceeded(e):
                    await self._emit_llm_failed(e, start_time=start_time)
                    raise LLMError(
                        str(e),
                        category=LLMErrorCategory.CONTEXT,
                        action=RecoveryAction.FAIL_FAST,
                        provider=self._provider,
                        model=self._model_name,
                        original=e,
                    ) from e
                else:
                    await self._emit_llm_failed(e, start_time=start_time)
                    category, action = LLMErrorClassifier.classify(e)
                    last_error = LLMError(
                        str(e),
                        category=category,
                        action=action,
                        provider=self._provider,
                        model=self._model_name,
                        original=e,
                    )
                    if action == RecoveryAction.RETRY:
                        if self._circuit_breaker:
                            await self._circuit_breaker.record_failure()
                        if attempt >= retries:
                            raise last_error from None
                    else:
                        raise last_error from None

            delay = calculate_retry_delay(self._retry_config, attempt)
            logger.warning(
                f"[Retry] 第 {attempt + 1}/{retries} 次失败，"
                f"{delay:.2f}s 后重试 | provider={self._provider} | error={last_error}"
            )
            await asyncio.sleep(delay)

        raise last_error or RuntimeError("Retry exhausted unexpectedly")

    async def _track_usage_async(self, result: Any, *, start_time: float) -> None:
        """异步追踪 Token 使用量，发出事件通知使用者"""
        if self._event_bus is None:
            return

        try:
            usage = extract_usage(result)

            if usage is None and isinstance(result, dict):
                raw = result.get("raw")
                if raw:
                    usage = extract_usage(raw)

            input_tokens = usage.input_tokens if usage else 0
            output_tokens = usage.output_tokens if usage else 0
            cached_tokens = 0
            if usage:
                cached_tokens = usage.cached_tokens or 0
                if cached_tokens == 0:
                    cached_tokens = usage.cache_read_tokens or 0
            duration_ms = (time.time() - start_time) * 1000
            logger.info(
                "LLM usage | model=%s input=%s output=%s cached=%s duration_ms=%.0f",
                self._model_name or "",
                input_tokens,
                output_tokens,
                cached_tokens,
                duration_ms,
            )

            await self._event_bus.emit(
                self,
                LLMCallCompletedEvent(
                    agent_id=self._event_agent_id or "",
                    key=self._event_key,
                    model=self._model_name or "",
                    input_tokens=input_tokens,
                    output_tokens=output_tokens,
                    cached_tokens=cached_tokens,
                    duration_ms=duration_ms,
                ),
            )

        except Exception as e:
            logger.warning(f"Usage 追踪失败（不影响主流程）: {e}")

    async def _emit_llm_failed(self, error: Exception, *, start_time: float) -> None:
        if self._event_bus is None:
            return
        duration_ms = (time.time() - start_time) * 1000
        await self._event_bus.emit(
            self,
            LLMCallFailedEvent(
                agent_id=self._event_agent_id or "",
                key=self._event_key,
                model=self._model_name or "",
                error=str(error),
                duration_ms=duration_ms,
            ),
        )

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
                event_bus=self._event_bus,
                event_agent_id=self._event_agent_id,
                event_key=self._event_key,
                rate_limit_manager=self._rate_limit_manager,
                circuit_breaker=self._circuit_breaker,
                timeout_seconds=self._timeout_seconds,
                retry_config=self._retry_config,
            )
        return self

    def bind_tools(self, tools: list, **kwargs) -> "ResilientChatModel":
        """绑定工具（保留原有的 thinking 等参数）"""
        if hasattr(self._llm, "bind_tools"):
            original_kwargs = {}
            if hasattr(self._llm, "kwargs"):
                original_kwargs = dict(self._llm.kwargs)

            bound = self._llm.bind_tools(tools, **kwargs)

            if "thinking" in original_kwargs and hasattr(bound, "bind"):
                bound = bound.bind(thinking=original_kwargs["thinking"])

            return ResilientChatModel(
                bound,
                provider=self._provider,
                model_name=self._model_name,
                event_bus=self._event_bus,
                event_agent_id=self._event_agent_id,
                event_key=self._event_key,
                rate_limit_manager=self._rate_limit_manager,
                circuit_breaker=self._circuit_breaker,
                timeout_seconds=self._timeout_seconds,
                retry_config=self._retry_config,
            )
        return self

    def with_structured_output(
        self,
        schema: type[BaseModel],
        **kwargs,
    ) -> "ResilientChatModel":
        """绑定结构化输出"""
        if hasattr(self._llm, "with_structured_output"):
            bound = self._llm.with_structured_output(schema, **kwargs)

            return ResilientChatModel(
                bound,
                provider=self._provider,
                model_name=self._model_name,
                event_bus=self._event_bus,
                event_agent_id=self._event_agent_id,
                event_key=self._event_key,
                rate_limit_manager=self._rate_limit_manager,
                circuit_breaker=self._circuit_breaker,
                timeout_seconds=self._timeout_seconds,
                retry_config=self._retry_config,
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

        if provider in ("claude", "anthropic"):
            from langchain_anthropic import ChatAnthropic as _ChatAnthropic
            ChatAnthropic = cast(Any, _ChatAnthropic)
            llm = ChatAnthropic(
                api_key=config.api_key,
                base_url=config.base_url if config.base_url else None,
                model=config.model_name,
                streaming=False,
            )
            if config.enable_thinking:
                thinking_config = {"type": "enabled"}
                if config.thinking_budget_tokens:
                    thinking_config["budget_tokens"] = config.thinking_budget_tokens
                return llm.bind(thinking=thinking_config)
            return llm

        if provider == "glm":
            from langchain_community.chat_models import ChatZhipuAI
            llm = ChatZhipuAI(
                zhipuai_api_key=config.api_key,
                model_name=config.model_name,
                streaming=False,
            )
            thinking_type = "enabled" if config.enable_thinking else "disabled"
            return llm.bind(thinking={"type": thinking_type})

        if provider == "deepseek":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            llm = ChatOpenAI(
                api_key=config.api_key,
                base_url=config.base_url or "https://api.deepseek.com/v1",
                model=config.model_name,
                streaming=False,
            )
            if config.enable_thinking:
                return llm.bind(extra_body={"enable_thinking": False})
            return llm

        if provider == "openrouter":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            return ChatOpenAI(
                api_key=config.api_key,
                base_url=config.base_url or "https://openrouter.ai/api/v1",
                model=config.model_name,
                streaming=False,
            )

        if provider == "ollama":
            from langchain_openai import ChatOpenAI as _ChatOpenAI
            ChatOpenAI = cast(Any, _ChatOpenAI)
            return ChatOpenAI(
                api_key="ollama",
                base_url=config.base_url or "http://localhost:11434/v1",
                model=config.model_name,
                streaming=False,
            )

        raise ValueError(f"不支持的 LLM 提供商: {provider}")


# ==================== Provider ====================


class LLMProvider:
    """LLM Provider（团队内使用）"""

    _CACHE_MAX_SIZE = 50

    def __init__(self, config: LLMConfig, *, event_bus: EventBus | None = None) -> None:
        if not config.is_configured():
            raise ValueError("LLM 未配置，无法创建 LLMProvider")

        self._config = config
        self._event_bus = event_bus
        self._rate_limit_manager = RateLimitManager(config.rate_limit)
        self._circuit_breakers = CircuitBreakerRegistry(config.circuit_breaker)
        self._instance_cache: dict[tuple, ResilientChatModel] = {}
        self._cache_lock = threading.Lock()

        cache_instance = create_llm_cache(config.cache)
        if cache_instance is not None:
            set_llm_cache(cache_instance)

    def _build_provider_config(self) -> LLMProviderConfig:
        return LLMProviderConfig(
            provider=self._config.provider,
            model_name=self._config.model,
            api_key=self._config.api_key,
            base_url=self._config.base_url,
            enable_thinking=self._config.enable_thinking,
            thinking_budget_tokens=self._config.thinking_budget_tokens,
        )

    def _cleanup_cache_if_needed(self) -> None:
        if len(self._instance_cache) < self._CACHE_MAX_SIZE:
            return
        keys = list(self._instance_cache.keys())
        for key in keys[: len(keys) // 2]:
            self._instance_cache.pop(key, None)

    def get_llm(
        self,
        output_schema: type[T] | None = None,
        **kwargs,
    ) -> ResilientChatModel:
        """
        获取带弹性能力的 LLM 实例
        """
        temperature = kwargs.get("temperature", self._config.temperature)
        max_tokens = kwargs.get("max_tokens")
        schema_key = f"{output_schema.__module__}.{output_schema.__qualname__}" if output_schema else None

        provider_config = self._build_provider_config()
        cache_key = (
            provider_config.provider,
            provider_config.model_name,
            provider_config.api_key,
            provider_config.base_url,
            provider_config.enable_thinking,
            provider_config.thinking_budget_tokens,
            temperature,
            max_tokens,
            schema_key,
        )

        if cache_key in self._instance_cache:
            return self._instance_cache[cache_key]

        with self._cache_lock:
            if cache_key in self._instance_cache:
                return self._instance_cache[cache_key]

            llm = LLMFactory.create_chat_model(provider_config)
            logger.info(
                f"创建 LLM 实例: provider={provider_config.provider}, "
                f"model={provider_config.model_name}"
            )

            bind_kwargs = {}
            if temperature is not None:
                bind_kwargs["temperature"] = temperature
            if max_tokens is not None:
                bind_kwargs["max_tokens"] = max_tokens
            if bind_kwargs and hasattr(llm, "bind"):
                llm = llm.bind(**bind_kwargs)

            resilient_llm = ResilientChatModel(
                llm,
                provider=provider_config.provider,
                model_name=provider_config.model_name,
                event_bus=self._event_bus,
                rate_limit_manager=self._rate_limit_manager,
                circuit_breaker=self._circuit_breakers.get("llm"),
                timeout_seconds=self._config.timeout_seconds,
                retry_config=self._config.retry,
            )

            self._cleanup_cache_if_needed()
            self._instance_cache[cache_key] = resilient_llm
            return resilient_llm

    def clear_cache(self) -> None:
        """清空 LLM 实例缓存（测试用）"""
        with self._cache_lock:
            self._instance_cache.clear()

    def __call__(self, output_schema: type[T] | None = None, **kwargs) -> ResilientChatModel:
        return self.get_llm(output_schema=output_schema, **kwargs)
