"""
Unified LLM invocation layer.

Supports OpenAI, Anthropic, GLM, DeepSeek, OpenRouter, and Ollama.

Features:
- Unified interface across providers
- Resilience (timeouts + retries + circuit breaker)
- Optional caching
- Token usage tracking
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
from datapillar_oneagentic.providers.llm.vendor_cache import (
    VendorCacheManager,
    VendorCachePolicy,
    apply_vendor_cache,
    build_openai_extra_body,
)
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import (
    from_langchain,
    is_langchain_message,
    is_langchain_list,
    to_langchain,
)
from datapillar_oneagentic.messages.adapters.langchain_patch import apply_zhipuai_patch
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

# ==================== GLM thinking mode patch ====================

# Apply GLM thinking patch on module load.
apply_zhipuai_patch()

T = TypeVar("T", bound=BaseModel)

def extract_thinking(message: Any) -> str | None:
    """
    Extract thinking content from an LLM message.

    Unified across providers:
    - GLM/DeepSeek: additional_kwargs.reasoning_content
    - Claude: thinking blocks in content (normalized to metadata in adapters)
    """
    if isinstance(message, Message):
        reasoning = message.metadata.get("reasoning_content")
        if isinstance(reasoning, str) and reasoning:
            return reasoning
        return None

    if hasattr(message, "metadata"):
        metadata = getattr(message, "metadata", None)
        if isinstance(metadata, dict):
            reasoning = metadata.get("reasoning_content")
            if isinstance(reasoning, str) and reasoning:
                return reasoning

    if not hasattr(message, "additional_kwargs"):
        return None

    additional_kwargs = getattr(message, "additional_kwargs", None)
    if isinstance(additional_kwargs, dict):
        reasoning = additional_kwargs.get("reasoning_content")
        if reasoning:
            return reasoning

    return None


def _normalize_llm_result(result: Any) -> Any:
    if is_langchain_message(result):
        return from_langchain(result)
    if is_langchain_list(result):
        return from_langchain(result)
    if isinstance(result, dict):
        raw = result.get("raw")
        if is_langchain_message(raw):
            result = dict(result)
            result["raw"] = from_langchain(raw)
        return result
    return result


def _bind_extra_body(llm: Any, extra_body: dict[str, Any]) -> Any:
    if not hasattr(llm, "bind"):
        return llm
    existing = {}
    if hasattr(llm, "kwargs"):
        raw = llm.kwargs.get("extra_body")
        if isinstance(raw, dict):
            existing = raw
    merged = {**existing, **extra_body}
    return llm.bind(extra_body=merged)


# ==================== LLM config data types ====================


@dataclass
class LLMProviderConfig:
    """LLM provider configuration."""

    provider: str
    model_name: str
    api_key: str
    base_url: str | None = None
    enable_thinking: bool = False
    thinking_budget_tokens: int | None = None


# ==================== Resilient wrapper ====================


class ResilientChatModel:
    """
    Resilient LLM wrapper.

    Wraps a LangChain ChatModel with:
    - Rate limiting (RPM + concurrency)
    - Timeout control
    - Automatic retries (retryable errors)
    - Circuit breaker protection
    - Token usage tracking
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
        vendor_cache: VendorCachePolicy | None = None,
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
        self._vendor_cache = vendor_cache

    @property
    def timeout(self) -> float:
        return self._timeout_seconds

    def with_event_context(
        self,
        *,
        agent_id: str | None,
        key: SessionKey | None,
    ) -> "ResilientChatModel":
        """Bind event context (agent + session)."""
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
            vendor_cache=self._vendor_cache,
        )

    async def ainvoke(
        self,
        input: Messages,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """Async LLM call with resilience."""
        if not isinstance(input, Messages):
            raise TypeError("LLM calls only accept Messages")
        if self._rate_limit_manager is None:
            return await self._invoke_with_resilience(input, config, **kwargs)

        async with self._rate_limit_manager.acquire(self._provider):
            return await self._invoke_with_resilience(input, config, **kwargs)

    async def _invoke_with_resilience(
        self,
        input: Messages,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """Invoke with retry logic (internal)."""
        retries = self._retry_config.max_retries
        last_error: Exception | None = None
        message_count = len(input)
        if self._vendor_cache:
            input = apply_vendor_cache(input, self._vendor_cache)
        langchain_input = to_langchain(input)

        for attempt in range(retries + 1):
            if self._circuit_breaker and not await self._circuit_breaker.allow_request():
                raise LLMError(
                    "LLM service circuit is open",
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
                    self._llm.ainvoke(langchain_input, config, **kwargs),
                    timeout=self.timeout,
                )
                # LangChain with_structured_output(include_raw=True) returns a dict:
                # {"raw": LangChain message, "parsed": ..., "parsing_error": ...}
                # Parsing failures are logged by the parser only on final failure.
                if self._circuit_breaker:
                    await self._circuit_breaker.record_success()
                normalized = _normalize_llm_result(result)
                asyncio.create_task(self._track_usage_async(normalized, start_time=start_time))
                return normalized

            except TimeoutError as e:
                await self._emit_llm_failed(e, start_time=start_time)
                last_error = LLMError(
                    f"LLM call timed out ({self.timeout}s)",
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
                elif ExceptionMapper.is_context_exceeded(e):
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
                f"[Retry] Attempt {attempt + 1}/{retries} failed; "
                f"retrying in {delay:.2f}s | provider={self._provider} | error={last_error}"
            )
            await asyncio.sleep(delay)

        raise last_error or RuntimeError("Retry exhausted unexpectedly")

    async def _track_usage_async(self, result: Any, *, start_time: float) -> None:
        """Track token usage asynchronously and emit events."""
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
            logger.warning(f"Usage tracking failed (non-blocking): {e}")

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
        input: Messages,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """Sync call (compatibility API)."""
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
        """Bind parameters."""
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
                vendor_cache=self._vendor_cache,
            )
        return self

    def bind_tools(self, tools: list, **kwargs) -> "ResilientChatModel":
        """Bind tools (preserve thinking config)."""
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
                vendor_cache=self._vendor_cache,
            )
        return self

    def with_structured_output(
        self,
        schema: type[BaseModel],
        **kwargs,
    ) -> "ResilientChatModel":
        """Bind structured output."""
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
                vendor_cache=self._vendor_cache,
            )
        return self

    def __getattr__(self, name: str) -> Any:
        """Proxy attribute access to the underlying LLM."""
        return getattr(self._llm, name)


# ==================== LLM factory ====================


class LLMFactory:
    """LLM factory for building provider instances."""

    @staticmethod
    def create_chat_model(config: LLMProviderConfig) -> Any:
        """Create a LangChain ChatModel instance."""
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

        raise ValueError(f"Unsupported LLM provider: {provider}")


# ==================== Provider ====================


class LLMProvider:
    """LLM provider (team-scoped)."""

    _CACHE_MAX_SIZE = 50

    def __init__(self, config: LLMConfig, *, event_bus: EventBus | None = None) -> None:
        if not config.is_configured():
            raise ValueError("LLM is not configured; cannot create LLMProvider")

        self._config = config
        self._event_bus = event_bus
        self._rate_limit_manager = RateLimitManager(config.rate_limit)
        self._circuit_breakers = CircuitBreakerRegistry(config.circuit_breaker)
        self._vendor_cache = VendorCacheManager()
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

    def _cleanup_cache(self) -> None:
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
        Get a resilient LLM instance.
        """
        temperature = kwargs.get("temperature", self._config.temperature)
        max_tokens = kwargs.get("max_tokens")
        schema_key = f"{output_schema.__module__}.{output_schema.__qualname__}" if output_schema else None

        provider_config = self._build_provider_config()
        vendor_policy = self._vendor_cache.get_policy(provider_config.provider)
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
            vendor_policy.cache_key() if vendor_policy else None,
        )

        if cache_key in self._instance_cache:
            return self._instance_cache[cache_key]

        with self._cache_lock:
            if cache_key in self._instance_cache:
                return self._instance_cache[cache_key]

            llm = LLMFactory.create_chat_model(provider_config)
            logger.info(
                f"LLM instance created: provider={provider_config.provider}, "
                f"model={provider_config.model_name}"
            )

            bind_kwargs = {}
            if temperature is not None:
                bind_kwargs["temperature"] = temperature
            if max_tokens is not None:
                bind_kwargs["max_tokens"] = max_tokens
            if bind_kwargs and hasattr(llm, "bind"):
                llm = llm.bind(**bind_kwargs)

            extra_body = build_openai_extra_body(vendor_policy)
            if extra_body:
                llm = _bind_extra_body(llm, extra_body)

            resilient_llm = ResilientChatModel(
                llm,
                provider=provider_config.provider,
                model_name=provider_config.model_name,
                event_bus=self._event_bus,
                rate_limit_manager=self._rate_limit_manager,
                circuit_breaker=self._circuit_breakers.get("llm"),
                timeout_seconds=self._config.timeout_seconds,
                retry_config=self._config.retry,
                vendor_cache=vendor_policy,
            )

            self._cleanup_cache()
            self._instance_cache[cache_key] = resilient_llm
            return resilient_llm

    def clear_cache(self) -> None:
        """Clear LLM instance cache (tests)."""
        with self._cache_lock:
            self._instance_cache.clear()

    def __call__(self, output_schema: type[T] | None = None, **kwargs) -> ResilientChatModel:
        return self.get_llm(output_schema=output_schema, **kwargs)
