"""
事件系统单元测试

测试模块：
- datapillar_oneagentic.events.bus
- datapillar_oneagentic.events.types
"""

import asyncio
import time

import pytest

from datapillar_oneagentic.events import (
    EventBus,
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
    SessionStartedEvent,
    SessionCompletedEvent,
)
from datapillar_oneagentic.events.base import BaseEvent
from datapillar_oneagentic.events.types import (
    LLMCallStartedEvent,
    LLMCallCompletedEvent,
    LLMStreamChunkEvent,
    DelegationStartedEvent,
    DelegationCompletedEvent,
)


class TestEventTypes:
    """事件类型测试"""

    def test_agent_started_event(self):
        """测试 AgentStartedEvent"""
        event = AgentStartedEvent(
            agent_id="analyst",
            agent_name="分析师",
            session_id="session_001",
            query="分析数据",
        )

        assert event.agent_id == "analyst"
        assert event.agent_name == "分析师"
        assert event.session_id == "session_001"
        assert event.query == "分析数据"

    def test_agent_completed_event(self):
        """测试 AgentCompletedEvent"""
        event = AgentCompletedEvent(
            agent_id="analyst",
            agent_name="分析师",
            session_id="session_001",
            result={"summary": "分析完成"},
            duration_ms=1500.0,
        )

        assert event.agent_id == "analyst"
        assert event.result == {"summary": "分析完成"}
        assert event.duration_ms == 1500.0

    def test_agent_failed_event(self):
        """测试 AgentFailedEvent"""
        event = AgentFailedEvent(
            agent_id="analyst",
            agent_name="分析师",
            session_id="session_001",
            error="连接超时",
            error_type="TimeoutError",
        )

        assert event.agent_id == "analyst"
        assert event.error == "连接超时"
        assert event.error_type == "TimeoutError"

    def test_tool_called_event(self):
        """测试 ToolCalledEvent"""
        event = ToolCalledEvent(
            agent_id="analyst",
            tool_name="search_tables",
            tool_input={"keyword": "用户"},
        )

        assert event.agent_id == "analyst"
        assert event.tool_name == "search_tables"
        assert event.tool_input == {"keyword": "用户"}

    def test_tool_completed_event(self):
        """测试 ToolCompletedEvent"""
        event = ToolCompletedEvent(
            agent_id="analyst",
            tool_name="search_tables",
            tool_output=["users", "orders"],
            duration_ms=200.0,
        )

        assert event.tool_name == "search_tables"
        assert event.tool_output == ["users", "orders"]
        assert event.duration_ms == 200.0

    def test_tool_failed_event(self):
        """测试 ToolFailedEvent"""
        event = ToolFailedEvent(
            agent_id="analyst",
            tool_name="search_tables",
            error="数据库连接失败",
        )

        assert event.tool_name == "search_tables"
        assert event.error == "数据库连接失败"

    def test_llm_call_started_event(self):
        """测试 LLMCallStartedEvent"""
        event = LLMCallStartedEvent(
            agent_id="analyst",
            model="gpt-4o",
            message_count=5,
        )

        assert event.agent_id == "analyst"
        assert event.model == "gpt-4o"
        assert event.message_count == 5

    def test_llm_call_completed_event(self):
        """测试 LLMCallCompletedEvent"""
        event = LLMCallCompletedEvent(
            agent_id="analyst",
            model="gpt-4o",
            input_tokens=100,
            output_tokens=50,
            duration_ms=1200.0,
        )

        assert event.model == "gpt-4o"
        assert event.input_tokens == 100
        assert event.output_tokens == 50
        assert event.duration_ms == 1200.0

    def test_llm_stream_chunk_event(self):
        """测试 LLMStreamChunkEvent"""
        event = LLMStreamChunkEvent(
            agent_id="analyst",
            chunk="Hello",
            is_final=False,
        )

        assert event.chunk == "Hello"
        assert event.is_final is False

    def test_delegation_started_event(self):
        """测试 DelegationStartedEvent"""
        event = DelegationStartedEvent(
            from_agent_id="analyst",
            to_agent_id="developer",
            task="生成代码",
            is_a2a=False,
        )

        assert event.from_agent_id == "analyst"
        assert event.to_agent_id == "developer"
        assert event.task == "生成代码"
        assert event.is_a2a is False

    def test_delegation_completed_event(self):
        """测试 DelegationCompletedEvent"""
        event = DelegationCompletedEvent(
            from_agent_id="analyst",
            to_agent_id="developer",
            result="代码已生成",
            duration_ms=3000.0,
        )

        assert event.result == "代码已生成"
        assert event.duration_ms == 3000.0

    def test_session_started_event(self):
        """测试 SessionStartedEvent"""
        event = SessionStartedEvent(
            session_id="session_001",
            user_id="user_001",
            query="创建用户宽表",
        )

        assert event.session_id == "session_001"
        assert event.user_id == "user_001"
        assert event.query == "创建用户宽表"

    def test_session_completed_event(self):
        """测试 SessionCompletedEvent"""
        event = SessionCompletedEvent(
            session_id="session_001",
            user_id="user_001",
            result={"status": "success"},
            duration_ms=5000.0,
            agent_count=3,
            tool_count=5,
        )

        assert event.session_id == "session_001"
        assert event.duration_ms == 5000.0
        assert event.agent_count == 3
        assert event.tool_count == 5


class TestEventBus:
    """EventBus 事件总线测试"""

    @pytest.fixture
    def scoped_bus(self):
        """使用作用域隔离的事件总线"""
        with event_bus.scoped_handlers():
            yield event_bus

    def test_event_bus_is_singleton(self):
        """测试事件总线是单例"""
        bus1 = EventBus()
        bus2 = EventBus()

        assert bus1 is bus2

    def test_register_sync_handler(self, scoped_bus):
        """测试注册同步处理器"""
        received_events = []

        def handler(source, event):
            received_events.append(event)

        scoped_bus.register(AgentStartedEvent, handler)

        assert scoped_bus.handler_count(AgentStartedEvent) == 1

    def test_register_async_handler(self, scoped_bus):
        """测试注册异步处理器"""
        received_events = []

        async def handler(source, event):
            received_events.append(event)

        scoped_bus.register(AgentStartedEvent, handler)

        assert scoped_bus.handler_count(AgentStartedEvent) == 1

    def test_emit_sync_event(self, scoped_bus):
        """测试发送事件到同步处理器"""
        received_events = []

        def handler(source, event):
            received_events.append(event)

        scoped_bus.register(AgentStartedEvent, handler)

        event = AgentStartedEvent(agent_id="test", agent_name="测试")
        scoped_bus.emit(self, event)

        time.sleep(0.1)

        assert len(received_events) == 1
        assert received_events[0].agent_id == "test"

    @pytest.mark.asyncio
    async def test_aemit_async_event(self, scoped_bus):
        """测试异步发送事件"""
        received_events = []

        async def handler(source, event):
            received_events.append(event)

        scoped_bus.register(AgentCompletedEvent, handler)

        event = AgentCompletedEvent(
            agent_id="test",
            agent_name="测试",
            result="done",
        )
        await scoped_bus.aemit(self, event)

        assert len(received_events) == 1
        assert received_events[0].result == "done"

    def test_on_decorator(self, scoped_bus):
        """测试 @on 装饰器"""
        received_events = []

        @scoped_bus.on(ToolCalledEvent)
        def handler(source, event):
            received_events.append(event)

        event = ToolCalledEvent(
            agent_id="test",
            tool_name="search",
            tool_input={"q": "test"},
        )
        scoped_bus.emit(self, event)

        time.sleep(0.1)

        assert len(received_events) == 1

    def test_unregister_handler(self, scoped_bus):
        """测试注销处理器"""

        def handler(source, event):
            pass

        scoped_bus.register(AgentStartedEvent, handler)
        assert scoped_bus.handler_count(AgentStartedEvent) == 1

        scoped_bus.unregister(AgentStartedEvent, handler)
        assert scoped_bus.handler_count(AgentStartedEvent) == 0

    def test_multiple_handlers(self, scoped_bus):
        """测试多个处理器"""
        results = []

        def handler1(source, event):
            results.append("handler1")

        def handler2(source, event):
            results.append("handler2")

        scoped_bus.register(AgentStartedEvent, handler1)
        scoped_bus.register(AgentStartedEvent, handler2)

        event = AgentStartedEvent(agent_id="test", agent_name="测试")
        scoped_bus.emit(self, event)

        time.sleep(0.1)

        assert len(results) == 2
        assert "handler1" in results
        assert "handler2" in results

    def test_handler_count_total(self, scoped_bus):
        """测试总处理器数量"""

        def handler1(source, event):
            pass

        def handler2(source, event):
            pass

        scoped_bus.register(AgentStartedEvent, handler1)
        scoped_bus.register(ToolCalledEvent, handler2)

        assert scoped_bus.handler_count() == 2

    def test_handler_count_by_type(self, scoped_bus):
        """测试按类型统计处理器数量"""

        def handler1(source, event):
            pass

        def handler2(source, event):
            pass

        scoped_bus.register(AgentStartedEvent, handler1)
        scoped_bus.register(AgentStartedEvent, handler2)
        scoped_bus.register(ToolCalledEvent, handler1)

        assert scoped_bus.handler_count(AgentStartedEvent) == 2
        assert scoped_bus.handler_count(ToolCalledEvent) == 1

    def test_clear_handlers(self, scoped_bus):
        """测试清空处理器"""

        def handler(source, event):
            pass

        scoped_bus.register(AgentStartedEvent, handler)
        scoped_bus.register(ToolCalledEvent, handler)

        assert scoped_bus.handler_count() > 0

        scoped_bus.clear()

        assert scoped_bus.handler_count() == 0

    def test_scoped_handlers(self):
        """测试作用域隔离"""

        def outer_handler(source, event):
            pass

        event_bus.register(SessionStartedEvent, outer_handler)
        outer_count = event_bus.handler_count(SessionStartedEvent)

        with event_bus.scoped_handlers():

            def inner_handler(source, event):
                pass

            event_bus.register(SessionStartedEvent, inner_handler)
            inner_count = event_bus.handler_count(SessionStartedEvent)

            assert inner_count == 1

        restored_count = event_bus.handler_count(SessionStartedEvent)
        assert restored_count == outer_count

    def test_handler_error_does_not_break_other_handlers(self, scoped_bus):
        """测试处理器错误不影响其他处理器"""
        results = []

        def error_handler(source, event):
            raise ValueError("handler error")

        def success_handler(source, event):
            results.append("success")

        scoped_bus.register(AgentStartedEvent, error_handler)
        scoped_bus.register(AgentStartedEvent, success_handler)

        event = AgentStartedEvent(agent_id="test", agent_name="测试")
        scoped_bus.emit(self, event)

        time.sleep(0.2)

        assert "success" in results


class TestBaseEvent:
    """BaseEvent 基类测试"""

    def test_base_event_timestamp(self):
        """测试事件时间戳"""
        event = AgentStartedEvent(agent_id="test", agent_name="测试")

        assert hasattr(event, "timestamp")
        assert event.timestamp is not None

    def test_event_default_values(self):
        """测试事件默认值"""
        event = AgentStartedEvent()

        assert event.agent_id == ""
        assert event.agent_name == ""
        assert event.session_id == ""
        assert event.query == ""
