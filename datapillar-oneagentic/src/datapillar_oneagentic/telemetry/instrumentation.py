"""
自动埋点

通过事件总线自动追踪 Agent/Tool/LLM 调用。
"""

from __future__ import annotations

import functools
import logging
import threading
import time
from typing import Any, Callable, TypeVar

from datapillar_oneagentic.events import (
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
    LLMCallStartedEvent,
    LLMCallCompletedEvent,
    LLMCallFailedEvent,
)
from datapillar_oneagentic.telemetry.tracer import get_tracer, is_telemetry_enabled

logger = logging.getLogger(__name__)

T = TypeVar("T")

# 活跃的 span 映射：key = "{type}:{event_id}", value = (span, created_at)
_active_spans: dict[str, tuple[Any, float]] = {}
_active_spans_lock = threading.Lock()

# span 超时时间（秒），超过此时间未关闭的 span 会被清理
_SPAN_TIMEOUT_SECONDS = 300  # 5 分钟


def _cleanup_stale_spans() -> None:
    """清理超时的 span（防止内存泄漏）"""
    current_time = time.time()
    stale_keys = []

    with _active_spans_lock:
        for key, (span, created_at) in list(_active_spans.items()):
            if current_time - created_at > _SPAN_TIMEOUT_SECONDS:
                stale_keys.append(key)

        for key in stale_keys:
            span, _ = _active_spans.pop(key)
            try:
                span.set_attribute("timeout", True)
                span.end()
            except Exception:
                pass

    if stale_keys:
        logger.warning(f"清理 {len(stale_keys)} 个超时的 span: {stale_keys}")


def _add_span(key: str, span: Any) -> None:
    """添加 span 到映射"""
    with _active_spans_lock:
        _active_spans[key] = (span, time.time())

    # 每次添加时检查是否需要清理（简单策略）
    if len(_active_spans) > 100:
        _cleanup_stale_spans()


def _pop_span(key: str) -> Any | None:
    """从映射中取出并移除 span"""
    with _active_spans_lock:
        entry = _active_spans.pop(key, None)
        if entry:
            return entry[0]  # 返回 span
    return None


def instrument_events() -> None:
    """
    注册事件处理器进行自动埋点

    调用此函数后，所有通过事件总线发送的 Agent/Tool/LLM 事件
    都会自动创建 OpenTelemetry span。
    """
    if not is_telemetry_enabled():
        logger.info("遥测未启用，跳过自动埋点")
        return

    # Agent 事件
    event_bus.register(AgentStartedEvent, _on_agent_started)
    event_bus.register(AgentCompletedEvent, _on_agent_completed)
    event_bus.register(AgentFailedEvent, _on_agent_failed)

    # Tool 事件
    event_bus.register(ToolCalledEvent, _on_tool_called)
    event_bus.register(ToolCompletedEvent, _on_tool_completed)
    event_bus.register(ToolFailedEvent, _on_tool_failed)

    # LLM 事件
    event_bus.register(LLMCallStartedEvent, _on_llm_started)
    event_bus.register(LLMCallCompletedEvent, _on_llm_completed)
    event_bus.register(LLMCallFailedEvent, _on_llm_failed)

    logger.info("OpenTelemetry 自动埋点已启用")


def uninstrument_events() -> None:
    """注销事件处理器"""
    event_bus.unregister(AgentStartedEvent, _on_agent_started)
    event_bus.unregister(AgentCompletedEvent, _on_agent_completed)
    event_bus.unregister(AgentFailedEvent, _on_agent_failed)
    event_bus.unregister(ToolCalledEvent, _on_tool_called)
    event_bus.unregister(ToolCompletedEvent, _on_tool_completed)
    event_bus.unregister(ToolFailedEvent, _on_tool_failed)
    event_bus.unregister(LLMCallStartedEvent, _on_llm_started)
    event_bus.unregister(LLMCallCompletedEvent, _on_llm_completed)
    event_bus.unregister(LLMCallFailedEvent, _on_llm_failed)

    # 清理所有活跃的 span
    with _active_spans_lock:
        for key, (span, _) in list(_active_spans.items()):
            try:
                span.end()
            except Exception:
                pass
        _active_spans.clear()

    logger.info("OpenTelemetry 自动埋点已禁用")


def _on_agent_started(source: Any, event: AgentStartedEvent) -> None:
    """Agent 开始"""
    tracer = get_tracer()
    span = tracer.start_span(f"agent.{event.agent_id}")
    span.set_attributes({
        "agent.id": event.agent_id,
        "agent.name": event.agent_name,
        "session.id": event.session_id,
        "query": event.query[:200] if event.query else "",
    })
    _add_span(f"agent:{event.event_id}", span)


def _on_agent_completed(source: Any, event: AgentCompletedEvent) -> None:
    """Agent 完成"""
    span = _pop_span(f"agent:{event.event_id}")
    if span:
        span.set_attribute("duration_ms", event.duration_ms)
        span.end()


def _on_agent_failed(source: Any, event: AgentFailedEvent) -> None:
    """Agent 失败"""
    span = _pop_span(f"agent:{event.event_id}")
    if span:
        span.set_attribute("error", True)
        span.set_attribute("error.message", event.error)
        span.set_attribute("error.type", event.error_type)

        try:
            from opentelemetry.trace import StatusCode
            span.set_status(StatusCode.ERROR, event.error)
        except ImportError:
            pass

        span.end()


def _on_tool_called(source: Any, event: ToolCalledEvent) -> None:
    """工具调用开始"""
    tracer = get_tracer()
    span = tracer.start_span(f"tool.{event.tool_name}")
    span.set_attributes({
        "tool.name": event.tool_name,
        "agent.id": event.agent_id,
    })
    _add_span(f"tool:{event.event_id}", span)


def _on_tool_completed(source: Any, event: ToolCompletedEvent) -> None:
    """工具调用完成"""
    span = _pop_span(f"tool:{event.event_id}")
    if span:
        span.set_attribute("duration_ms", event.duration_ms)
        span.end()


def _on_tool_failed(source: Any, event: ToolFailedEvent) -> None:
    """工具调用失败"""
    span = _pop_span(f"tool:{event.event_id}")
    if span:
        span.set_attribute("error", True)
        span.set_attribute("error.message", event.error)

        try:
            from opentelemetry.trace import StatusCode
            span.set_status(StatusCode.ERROR, event.error)
        except ImportError:
            pass

        span.end()


def _on_llm_started(source: Any, event: LLMCallStartedEvent) -> None:
    """LLM 调用开始"""
    tracer = get_tracer()
    span = tracer.start_span(f"llm.{event.model or 'call'}")
    span.set_attributes({
        "llm.model": event.model,
        "agent.id": event.agent_id,
        "llm.message_count": event.message_count,
    })
    _add_span(f"llm:{event.event_id}", span)


def _on_llm_completed(source: Any, event: LLMCallCompletedEvent) -> None:
    """LLM 调用完成"""
    span = _pop_span(f"llm:{event.event_id}")
    if span:
        span.set_attributes({
            "llm.input_tokens": event.input_tokens,
            "llm.output_tokens": event.output_tokens,
            "duration_ms": event.duration_ms,
        })
        span.end()


def _on_llm_failed(source: Any, event: LLMCallFailedEvent) -> None:
    """LLM 调用失败"""
    span = _pop_span(f"llm:{event.event_id}")
    if span:
        span.set_attribute("error", True)
        span.set_attribute("error.message", event.error)
        span.set_attribute("duration_ms", event.duration_ms)

        try:
            from opentelemetry.trace import StatusCode
            span.set_status(StatusCode.ERROR, event.error)
        except ImportError:
            pass

        span.end()


# === 装饰器 ===


def trace_agent(name: str | None = None):
    """
    装饰器：追踪 Agent 方法

    使用示例：
    ```python
    class MyAgent:
        @trace_agent("my_agent")
        async def run(self, ctx):
            ...
    ```
    """

    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        span_name = name or func.__name__

        @functools.wraps(func)
        async def wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span(f"agent.{span_name}") as span:
                start_time = time.time()
                try:
                    result = await func(*args, **kwargs)
                    span.set_attribute("duration_ms", (time.time() - start_time) * 1000)
                    return result
                except Exception as e:
                    span.set_attribute("error", True)
                    span.set_attribute("error.message", str(e))
                    span.record_exception(e)
                    raise

        return wrapper

    return decorator


def trace_tool(name: str | None = None):
    """
    装饰器：追踪工具函数

    使用示例：
    ```python
    @trace_tool("search_tables")
    async def search_tables(query: str):
        ...
    ```
    """

    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        span_name = name or func.__name__

        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span(f"tool.{span_name}") as span:
                start_time = time.time()
                try:
                    result = await func(*args, **kwargs)
                    span.set_attribute("duration_ms", (time.time() - start_time) * 1000)
                    return result
                except Exception as e:
                    span.set_attribute("error", True)
                    span.set_attribute("error.message", str(e))
                    span.record_exception(e)
                    raise

        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span(f"tool.{span_name}") as span:
                start_time = time.time()
                try:
                    result = func(*args, **kwargs)
                    span.set_attribute("duration_ms", (time.time() - start_time) * 1000)
                    return result
                except Exception as e:
                    span.set_attribute("error", True)
                    span.set_attribute("error.message", str(e))
                    span.record_exception(e)
                    raise

        import asyncio
        if asyncio.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper

    return decorator
