"""
LLM 统一调用层 - 基于 LangChain 统一接口

支持 OpenAI、Claude、智谱GLM、OpenRouter、Ollama

特性：
- 统一接口，屏蔽模型差异
- 内置弹性机制（超时 + 重试 + 熔断）
- 语义缓存
- Token 使用量追踪
"""

import asyncio
import json
import logging
import threading
import uuid
from typing import Any, TypeVar, cast

from langchain_core.globals import set_llm_cache
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import BaseMessage
from langchain_core.runnables import Runnable
from pydantic import BaseModel

from src.infrastructure.llm.llm_cache import create_llm_cache
from src.infrastructure.llm.model_manager import model_manager
from src.infrastructure.llm.usage_tracker import (
    estimate_cost_usd,
    estimate_usage,
    extract_usage,
    parse_pricing,
)
from src.infrastructure.resilience import (
    CircuitBreakerError,
    ErrorClassifier,
    get_circuit_breaker,
    get_resilience_config,
    with_retry,
)

logger = logging.getLogger(__name__)


# ==================== 全局 LLM 缓存（精确匹配）====================
_llm_cache_instance = create_llm_cache()
if _llm_cache_instance is not None:
    set_llm_cache(_llm_cache_instance)
    logger.info("LLM 缓存已启用（精确匹配，Redis 存储）")
else:
    logger.info("LLM 缓存已禁用")

T = TypeVar('T', bound=BaseModel)

# ==================== LLM 实例缓存 ====================
_llm_cache: dict[tuple, Any] = {}
_llm_cache_lock = threading.Lock()  # 线程锁，防止并发创建


# ==================== 弹性包装器 ====================


class ResilientChatModel:
    """
    弹性 LLM 包装器

    包装 LangChain ChatModel，添加：
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
        self._config = get_resilience_config()
        self._circuit_breaker = get_circuit_breaker("llm")
        # 模型元信息（用于 usage 追踪）
        self._provider = provider
        self._model_name = model_name
        self._config_json = config_json
        self._pricing = parse_pricing(config_json) if config_json else None

    @property
    def timeout(self) -> float:
        return self._config.llm_timeout_seconds

    async def ainvoke(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """
        异步调用 LLM（带弹性保护）

        自动处理：
        - 超时（使用配置的 llm_timeout_seconds）
        - 重试（可重试错误自动重试）
        - 熔断（连续失败时快速失败）
        - Token 使用量追踪
        """
        return await self._invoke_with_resilience(input, config, **kwargs)

    @with_retry()
    async def _invoke_with_resilience(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """带重试的调用（内部方法）"""
        # 熔断检查
        if not await self._circuit_breaker.allow_request():
            raise CircuitBreakerError("llm", "LLM 服务熔断中")

        try:
            # 超时控制
            result = await asyncio.wait_for(
                self._llm.ainvoke(input, config, **kwargs),
                timeout=self.timeout,
            )
            await self._circuit_breaker.record_success()

            # 异步追踪 usage（fire-and-forget，不阻塞主流程）
            asyncio.create_task(self._track_usage_async(input, result))

            return result

        except TimeoutError:
            await self._circuit_breaker.record_failure()
            raise TimeoutError(f"LLM 调用超时（{self.timeout}s）") from None

        except Exception as e:
            # 只有可重试错误才记录到熔断器
            if ErrorClassifier.is_retryable(e):
                await self._circuit_breaker.record_failure()
            raise

    async def _track_usage_async(self, input: Any, result: Any) -> None:
        """
        异步追踪 Token 使用量（fire-and-forget）

        不阻塞主流程，DB 异常只记录日志。
        """
        try:
            from src.infrastructure.repository.system.ai_model import LlmUsage

            # 提取真实 usage（处理多种返回格式）
            usage = extract_usage(result)

            # 如果 result 是 dict（with_structured_output + include_raw=True 的返回）
            # 尝试从 raw 字段提取
            if usage is None and isinstance(result, dict):
                raw = result.get("raw")
                if raw:
                    usage = extract_usage(raw)

            # 仍然拿不到，尝试估算
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

            # 计算费用
            cost = estimate_cost_usd(usage=usage, pricing=self._pricing)

            # 构建记录
            record = {
                "user_id": "system",  # 默认值，可从上下文覆盖
                "session_id": "default",  # 默认值，可从上下文覆盖
                "module": "llm_client",
                "agent_id": "unknown",  # 默认值，可从上下文覆盖
                "provider": self._provider or "unknown",
                "model_name": self._model_name or "unknown",
                "run_id": str(uuid.uuid4()),
                "parent_run_id": None,
                "prompt_tokens": usage.prompt_tokens,
                "completion_tokens": usage.completion_tokens,
                "total_tokens": usage.total_tokens,
                "estimated": usage.estimated,
                "prompt_cost_usd": float(cost.prompt_cost_usd) if cost else None,
                "completion_cost_usd": float(cost.completion_cost_usd) if cost else None,
                "total_cost_usd": float(cost.total_cost_usd) if cost else None,
                "raw_usage_json": (
                    json.dumps(usage.raw_usage, ensure_ascii=False) if usage.raw_usage else None
                ),
            }

            # 写入数据库
            LlmUsage.upsert_usage(record=record)
            logger.debug(
                f"Usage 已记录: {self._model_name}, "
                f"tokens={usage.total_tokens}, estimated={usage.estimated}"
            )

        except Exception as e:
            # DB 异常不影响主流程，只记录日志
            logger.warning(f"Usage 追踪失败（不影响主流程）: {e}")

    def invoke(
        self,
        input: list[BaseMessage] | Any,
        config: dict | None = None,
        **kwargs,
    ) -> Any:
        """同步调用（兼容接口，内部使用异步）"""
        import asyncio

        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            loop = None

        if loop and loop.is_running():
            # 在异步环境中，创建新任务
            import concurrent.futures

            with concurrent.futures.ThreadPoolExecutor() as executor:
                future = executor.submit(asyncio.run, self.ainvoke(input, config, **kwargs))
                return future.result()
        else:
            return asyncio.run(self.ainvoke(input, config, **kwargs))

    def bind(self, **kwargs) -> "ResilientChatModel":
        """绑定参数，返回新的弹性包装器"""
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
        """绑定工具，返回新的弹性包装器"""
        if hasattr(self._llm, "bind_tools"):
            bound = self._llm.bind_tools(tools, **kwargs)
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
        """绑定结构化输出，返回新的弹性包装器"""
        if hasattr(self._llm, "with_structured_output"):
            method = kwargs.get("method", "function_calling")
            bound = self._llm.with_structured_output(schema, **kwargs)

            # GLM 使用 json_mode 时需要通过 extra_body 传递 response_format 参数
            if self._provider and self._provider.lower() == "glm" and method == "json_mode":
                logger.info("GLM json_mode: 通过 extra_body 绑定 response_format")
                bound = bound.bind(extra_body={"response_format": {"type": "json_object"}})

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
    """LLM 工厂类 - 从数据库读取配置，返回原生 SDK 客户端"""

    @staticmethod
    def create_chat_model(model_id: int | None = None) -> Any:
        """
        创建 LangChain ChatModel 实例

        Args:
            model_id: 指定模型ID，如果为None则使用默认Chat模型

        Returns:
            LangChain ChatModel 实例
        """
        if model_id:
            model = model_manager.model_by_id(model_id)
        else:
            model = model_manager.default_chat_model()

        if not model:
            raise ValueError("未找到可用的 Chat 模型配置，请在数据库中配置")

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
            # GLM 使用 ChatOpenAI 兼容层调用，而非 ChatZhipuAI
            # 原因：ChatZhipuAI 的 with_structured_output 只支持 tool_choice="auto"，
            # 导致模型可能不调用工具或返回类型不守约（如嵌套对象被字符串化）。
            # ChatOpenAI 支持 tool_choice=tool_name 强制调用，GLM API 兼容 OpenAI 格式。
            from langchain_openai import ChatOpenAI as _ChatOpenAI

            ChatOpenAI = cast(Any, _ChatOpenAI)
            base_url = (model.base_url or "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
            if not base_url.endswith("/"):
                base_url = f"{base_url}/"

            return ChatOpenAI(
                api_key=model.api_key,
                base_url=base_url,
                model=model.model_name,
                streaming=False,
                # 关闭 GLM "思考链"，避免 reasoning_content 抢占 token 造成 content 为空/截断
                extra_body={"thinking": {"type": "disabled"}},
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
            raise ValueError(f"不支持的 Chat 模型提供商: {provider}")


# ==================== 统一调用接口 ====================


def call_llm(
    model_id: int | None = None,
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
        model_id: 模型ID（None=使用默认模型）
        output_schema: Pydantic 模型类，启用 structured output
        **kwargs: temperature, max_tokens 等额外参数

    Returns:
        ResilientChatModel 实例，调用 ainvoke/invoke 自动享有弹性保护
    """
    temperature = kwargs.get("temperature")
    max_tokens = kwargs.get("max_tokens")
    schema_name = output_schema.__name__ if output_schema else None
    cache_key = (model_id, temperature, max_tokens, schema_name)

    # 双重检查锁定模式（Double-Checked Locking）
    # 第一次检查（无锁，快速路径）
    if cache_key in _llm_cache:
        return _llm_cache[cache_key]

    # 获取锁后再次检查（防止并发创建）
    with _llm_cache_lock:
        # 第二次检查（有锁，确保只创建一次）
        if cache_key in _llm_cache:
            return _llm_cache[cache_key]

        # 获取模型配置（用于 usage 追踪）
        model = (
            model_manager.model_by_id(model_id) if model_id else model_manager.default_chat_model()
        )

        if not model:
            raise ValueError("未找到可用的 Chat 模型配置，请在数据库中配置")

        # 创建基础 LLM
        llm = LLMFactory.create_chat_model(model_id)
        logger.info(
            f"创建 LLM 实例: model_id={model_id}, provider={model.provider}, "
            f"model={model.model_name}, temp={temperature}, schema={schema_name}"
        )

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

        # 包装为弹性模型（传入模型元信息用于 usage 追踪）
        resilient_llm = ResilientChatModel(
            llm,
            provider=model.provider,
            model_name=model.model_name,
            config_json=model.config_json,
        )

        _llm_cache[cache_key] = resilient_llm
        return resilient_llm
