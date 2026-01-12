"""
事件总线测试
"""

import asyncio

import pytest

from src.modules.oneagentic.events import (
    AgentCompletedEvent,
    AgentStartedEvent,
    BaseEvent,
    EventBus,
    ToolCalledEvent,
    event_bus,
)


class TestBaseEvent:
    """基础事件测试"""

    def test_event_creation(self):
        """事件创建"""
        event = BaseEvent()
        assert event.event_id
        assert event.timestamp
        assert event.event_type == "BaseEvent"

    def test_event_to_dict(self):
        """事件序列化"""
        event = BaseEvent(metadata={"key": "value"})
        data = event.to_dict()
        assert "event_id" in data
        assert "event_type" in data
        assert data["metadata"] == {"key": "value"}


class TestAgentEvents:
    """Agent 事件测试"""

    def test_agent_started_event(self):
        """Agent 开始事件"""
        event = AgentStartedEvent(
            agent_id="analyst",
            agent_name="分析师",
            session_id="session123",
            query="帮我分析数据",
        )
        assert event.agent_id == "analyst"
        assert event.agent_name == "分析师"
        assert event.event_type == "AgentStartedEvent"

    def test_agent_completed_event(self):
        """Agent 完成事件"""
        event = AgentCompletedEvent(
            agent_id="analyst",
            agent_name="分析师",
            result={"success": True},
            duration_ms=1500.5,
        )
        assert event.result == {"success": True}
        assert event.duration_ms == 1500.5


class TestEventBus:
    """事件总线测试"""

    def test_singleton(self):
        """单例模式"""
        bus1 = EventBus()
        bus2 = EventBus()
        assert bus1 is bus2
        assert bus1 is event_bus

    def test_register_sync_handler(self):
        """注册同步处理器"""
        with event_bus.scoped_handlers():
            results = []

            @event_bus.on(AgentStartedEvent)
            def handler(source, event):
                results.append(event.agent_id)

            assert event_bus.handler_count(AgentStartedEvent) == 1

            # 发送事件
            event_bus.emit(
                self,
                AgentStartedEvent(agent_id="test_agent", agent_name="测试"),
            )

            # 等待处理完成
            import time

            time.sleep(0.1)

            assert "test_agent" in results

    def test_register_async_handler(self):
        """注册异步处理器"""
        with event_bus.scoped_handlers():
            results = []

            @event_bus.on(AgentStartedEvent)
            async def handler(source, event):
                await asyncio.sleep(0.01)
                results.append(event.agent_id)

            assert event_bus.handler_count(AgentStartedEvent) == 1

            # 发送事件
            event_bus.emit(
                self,
                AgentStartedEvent(agent_id="async_agent", agent_name="异步测试"),
            )

            # 等待处理完成
            import time

            time.sleep(0.2)

            assert "async_agent" in results

    def test_multiple_handlers(self):
        """多个处理器"""
        with event_bus.scoped_handlers():
            results = []

            @event_bus.on(ToolCalledEvent)
            def handler1(source, event):
                results.append(f"sync:{event.tool_name}")

            @event_bus.on(ToolCalledEvent)
            async def handler2(source, event):
                results.append(f"async:{event.tool_name}")

            assert event_bus.handler_count(ToolCalledEvent) == 2

            event_bus.emit(
                self,
                ToolCalledEvent(tool_name="search_tables"),
            )

            import time

            time.sleep(0.2)

            assert "sync:search_tables" in results
            assert "async:search_tables" in results

    def test_scoped_handlers(self):
        """作用域隔离"""
        initial_count = event_bus.handler_count()

        with event_bus.scoped_handlers():

            @event_bus.on(AgentStartedEvent)
            def temp_handler(source, event):
                pass

            assert event_bus.handler_count(AgentStartedEvent) == 1

        # 作用域结束后恢复
        assert event_bus.handler_count() == initial_count

    def test_unregister(self):
        """注销处理器"""
        with event_bus.scoped_handlers():

            def handler(source, event):
                pass

            event_bus.register(AgentStartedEvent, handler)
            assert event_bus.handler_count(AgentStartedEvent) == 1

            event_bus.unregister(AgentStartedEvent, handler)
            assert event_bus.handler_count(AgentStartedEvent) == 0


class TestEventBusAsync:
    """异步事件总线测试"""

    @pytest.mark.asyncio
    async def test_aemit(self):
        """异步发送事件"""
        with event_bus.scoped_handlers():
            results = []

            @event_bus.on(AgentCompletedEvent)
            async def handler(source, event):
                results.append(event.agent_id)

            await event_bus.aemit(
                self,
                AgentCompletedEvent(agent_id="async_emit_test"),
            )

            assert "async_emit_test" in results
