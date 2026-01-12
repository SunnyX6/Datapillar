"""
自动埋点

通过事件总线自动追踪 Agent/Tool/LLM 调用。
"""

from __future__ import annotations

import functools
import logging
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
)
from datapillar_oneagentic.telemetry.tracer import get_tracer, is_telemetry_enabled

logger = logging.getLogger(__name__)

T = TypeVar("T")

# 活跃的 span 映射（用于关联开始/结束事件）
_active_spans: dict[str, Any] = {}


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

    logger.info("OpenTelemetry 自动埋点已启用")


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
    _active_spans[f"agent:{event.event_id}"] = span


def _on_agent_completed(source: Any, event: AgentCompletedEvent) -> None:
    """Agent 完成"""
    # 查找对应的 span（通过 agent_id 匹配最近的）
    span_key = None
    for key in list(_active_spans.keys()):
        if key.startswith("agent:"):
            span_key = key
            break

    if span_key:
        span = _active_spans.pop(span_key)
        span.set_attribute("duration_ms", event.duration_ms)
        span.end()


def _on_agent_failed(source: Any, event: AgentFailedEvent) -> None:
    """Agent 失败"""
    span_key = None
    for key in list(_active_spans.keys()):
        if key.startswith("agent:"):
            span_key = key
            break

    if span_key:
        span = _active_spans.pop(span_key)
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
    _active_spans[f"tool:{event.event_id}"] = span


def _on_tool_completed(source: Any, event: ToolCompletedEvent) -> None:
    """工具调用完成"""
    span_key = None
    for key in list(_active_spans.keys()):
        if key.startswith("tool:"):
            span_key = key
            break

    if span_key:
        span = _active_spans.pop(span_key)
        span.set_attribute("duration_ms", event.duration_ms)
        span.end()


def _on_tool_failed(source: Any, event: ToolFailedEvent) -> None:
    """工具调用失败"""
    span_key = None
    for key in list(_active_spans.keys()):
        if key.startswith("tool:"):
            span_key = key
            break

    if span_key:
        span = _active_spans.pop(span_key)
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
    _active_spans[f"llm:{event.event_id}"] = span


def _on_llm_completed(source: Any, event: LLMCallCompletedEvent) -> None:
    """LLM 调用完成"""
    span_key = None
    for key in list(_active_spans.keys()):
        if key.startswith("llm:"):
            span_key = key
            break

    if span_key:
        span = _active_spans.pop(span_key)
        span.set_attributes({
            "llm.input_tokens": event.input_tokens,
            "llm.output_tokens": event.output_tokens,
            "duration_ms": event.duration_ms,
        })
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
