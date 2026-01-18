"""
事件总线

提供事件发布/订阅机制。

特点：
- 支持同步/异步处理器
- 线程安全
- 作用域隔离（用于测试）
"""

from __future__ import annotations

import asyncio
import logging
import threading
from collections.abc import Callable, Generator
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from typing import Any, TypeVar

from datapillar_oneagentic.events.base import BaseEvent

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseEvent)

# 处理器类型
SyncHandler = Callable[[Any, BaseEvent], None]
AsyncHandler = Callable[[Any, BaseEvent], Any]
Handler = SyncHandler | AsyncHandler


def _is_async_handler(handler: Handler) -> bool:
    """检查是否是异步处理器"""
    return asyncio.iscoroutinefunction(handler)


class EventBus:
    """
    事件总线

    使用示例：
    ```python
    from datapillar_oneagentic.events import EventBus, AgentStartedEvent

    event_bus = EventBus()

    @event_bus.on(AgentStartedEvent)
    def on_agent_started(source, event):
        print(f"Agent {event.agent_name} started")

    # 发送事件
    await event_bus.emit(self, AgentStartedEvent(agent_id="analyst", agent_name="分析师"))
    ```
    """

    def __init__(self) -> None:
        """初始化"""
        self._lock = threading.RLock()
        self._sync_handlers: dict[type[BaseEvent], set[SyncHandler]] = {}
        self._async_handlers: dict[type[BaseEvent], set[AsyncHandler]] = {}
        self._shutting_down = False

        # 线程池用于同步处理器
        self._executor = ThreadPoolExecutor(
            max_workers=5,
            thread_name_prefix="EventBusSync",
        )

    def on(
        self,
        event_type: type[T],
    ) -> Callable[[Handler], Handler]:
        """装饰器：注册事件处理器"""

        def decorator(handler: Handler) -> Handler:
            self.register(event_type, handler)
            return handler

        return decorator

    def register(
        self,
        event_type: type[BaseEvent],
        handler: Handler,
    ) -> None:
        """注册事件处理器"""
        with self._lock:
            if _is_async_handler(handler):
                if event_type not in self._async_handlers:
                    self._async_handlers[event_type] = set()
                self._async_handlers[event_type].add(handler)
            else:
                if event_type not in self._sync_handlers:
                    self._sync_handlers[event_type] = set()
                self._sync_handlers[event_type].add(handler)

    def unregister(
        self,
        event_type: type[BaseEvent],
        handler: Handler,
    ) -> None:
        """注销事件处理器"""
        with self._lock:
            if _is_async_handler(handler):
                if event_type in self._async_handlers:
                    self._async_handlers[event_type].discard(handler)
            else:
                if event_type in self._sync_handlers:
                    self._sync_handlers[event_type].discard(handler)

    def _call_sync_handler(
        self,
        handler: SyncHandler,
        source: Any,
        event: BaseEvent,
    ) -> None:
        """调用同步处理器"""
        try:
            handler(source, event)
        except Exception as e:
            logger.error(f"同步处理器错误: {handler.__name__}, 错误: {e}")

    async def _call_async_handlers(
        self,
        source: Any,
        event: BaseEvent,
        handlers: set[AsyncHandler],
    ) -> None:
        """调用异步处理器"""
        # 转为 list 保证顺序一致（set 无序，两次迭代顺序可能不同）
        handlers_list = list(handlers)
        coros = [handler(source, event) for handler in handlers_list]
        results = await asyncio.gather(*coros, return_exceptions=True)

        for handler, result in zip(handlers_list, results, strict=False):
            if isinstance(result, Exception):
                logger.error(
                    f"异步处理器错误: {getattr(handler, '__name__', handler)}, 错误: {result}"
                )

    async def emit(self, source: Any, event: BaseEvent) -> None:
        """发送事件"""
        if self._shutting_down:
            return

        event_type = type(event)

        with self._lock:
            sync_handlers = set(self._sync_handlers.get(event_type, set()))
            async_handlers = set(self._async_handlers.get(event_type, set()))

        loop = asyncio.get_running_loop()

        # 执行同步处理器（在线程池中执行，避免阻塞主事件循环）
        sync_tasks = [
            loop.run_in_executor(
                self._executor,
                self._call_sync_handler,
                handler,
                source,
                event,
            )
            for handler in sync_handlers
        ]

        # 并发执行同步和异步处理器
        all_tasks: list[Any] = sync_tasks
        if async_handlers:
            all_tasks.append(self._call_async_handlers(source, event, async_handlers))

        if all_tasks:
            await asyncio.gather(*all_tasks, return_exceptions=True)

    @contextmanager
    def scoped_handlers(self) -> Generator[None, Any, None]:
        """作用域隔离（用于测试）"""
        with self._lock:
            prev_sync = dict(self._sync_handlers)
            prev_async = dict(self._async_handlers)
            self._sync_handlers = {}
            self._async_handlers = {}

        try:
            yield
        finally:
            with self._lock:
                self._sync_handlers = prev_sync
                self._async_handlers = prev_async

    def clear(self) -> None:
        """清空所有处理器"""
        with self._lock:
            self._sync_handlers.clear()
            self._async_handlers.clear()

    def handler_count(self, event_type: type[BaseEvent] | None = None) -> int:
        """获取处理器数量"""
        with self._lock:
            if event_type is None:
                sync_count = sum(len(h) for h in self._sync_handlers.values())
                async_count = sum(len(h) for h in self._async_handlers.values())
                return sync_count + async_count

            sync_count = len(self._sync_handlers.get(event_type, set()))
            async_count = len(self._async_handlers.get(event_type, set()))
            return sync_count + async_count

    def shutdown(self, wait: bool = True) -> None:
        """关闭事件总线"""
        with self._lock:
            self._shutting_down = True

        if hasattr(self, "_executor"):
            self._executor.shutdown(wait=wait)

        with self._lock:
            self._sync_handlers.clear()
            self._async_handlers.clear()
