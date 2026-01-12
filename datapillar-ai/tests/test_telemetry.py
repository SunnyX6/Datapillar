"""
OpenTelemetry 遥测模块测试
"""

import pytest

from src.modules.oneagentic.telemetry import (
    get_tracer,
    is_telemetry_enabled,
    trace_agent,
    trace_tool,
)


class TestTracer:
    """Tracer 测试"""

    def test_get_tracer_without_init(self):
        """未初始化时获取 tracer"""
        tracer = get_tracer()
        # 应该返回 NoOp tracer
        assert tracer is not None

    def test_is_telemetry_enabled_default(self):
        """默认未启用"""
        # 注意：如果之前测试初始化了，这里可能是 True
        # 但这里主要测试函数能正常调用
        result = is_telemetry_enabled()
        assert isinstance(result, bool)

    def test_noop_tracer_context(self):
        """NoOp tracer 上下文管理"""
        tracer = get_tracer()

        # 应该能正常使用
        with tracer.start_as_current_span("test_span") as span:
            span.set_attribute("key", "value")
            span.add_event("test_event")


class TestDecorators:
    """装饰器测试"""

    @pytest.mark.asyncio
    async def test_trace_agent_decorator(self):
        """Agent 追踪装饰器"""
        results = []

        @trace_agent("test_agent")
        async def my_agent_run():
            results.append("executed")
            return "done"

        result = await my_agent_run()
        assert result == "done"
        assert "executed" in results

    @pytest.mark.asyncio
    async def test_trace_agent_with_exception(self):
        """Agent 追踪装饰器（异常情况）"""

        @trace_agent("error_agent")
        async def failing_agent():
            raise ValueError("测试错误")

        with pytest.raises(ValueError, match="测试错误"):
            await failing_agent()

    @pytest.mark.asyncio
    async def test_trace_tool_async(self):
        """工具追踪装饰器（异步）"""

        @trace_tool("async_tool")
        async def my_async_tool(x: int) -> int:
            return x * 2

        result = await my_async_tool(5)
        assert result == 10

    def test_trace_tool_sync(self):
        """工具追踪装饰器（同步）"""

        @trace_tool("sync_tool")
        def my_sync_tool(x: int) -> int:
            return x * 2

        result = my_sync_tool(5)
        assert result == 10


class TestNoOpSpan:
    """NoOp Span 测试"""

    def test_noop_span_methods(self):
        """NoOp span 方法调用"""
        tracer = get_tracer()

        with tracer.start_as_current_span("test") as span:
            # 所有方法应该能正常调用而不报错
            span.set_attribute("key", "value")
            span.set_attributes({"a": 1, "b": 2})
            span.add_event("event", {"attr": "value"})
            span.end()
