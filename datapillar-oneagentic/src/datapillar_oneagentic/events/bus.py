"""
事件总线

提供全局事件发布/订阅机制。

特点：
- 单例模式
- 支持同步/异步处理器
- 线程安全
- 作用域隔离（用于测试）
"""

from __future__ import annotations

import asyncio
import atexit
import logging
import threading
from collections.abc import Callable, Generator
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from typing import Any, Final, TypeVar

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
    全局事件总线

    单例模式，管理事件的发布和订阅。

    使用示例：
    ```python
    from datapillar_oneagentic.events import event_bus, AgentStartedEvent

    # 注册处理器
    @event_bus.on(AgentStartedEvent)
    def on_agent_started(source, event):
        print(f"Agent {event.agent_name} started")

    # 发送事件
    event_bus.emit(self, AgentStartedEvent(agent_id="analyst", agent_name="分析师"))
    ```
    """

    _instance: EventBus | None = None
    _instance_lock: threading.RLock = threading.RLock()

    def __new__(cls) -> EventBus:
        """单例模式"""
        if cls._instance is None:
            with cls._instance_lock:
                if cls._instance is None:
                    instance = super().__new__(cls)
                    instance._initialize()
                    cls._instance = instance
        return cls._instance

    def _initialize(self) -> None:
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

        # 异步事件循环
        self._loop = asyncio.new_event_loop()
        self._loop_thread = threading.Thread(
            target=self._run_loop,
            name="EventBusLoop",
            daemon=True,
        )
        self._loop_thread.start()

    def _run_loop(self) -> None:
        """运行异步事件循环"""
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

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

    def emit(self, source: Any, event: BaseEvent) -> None:
        """发送事件"""
        if self._shutting_down:
            return

        event_type = type(event)

        with self._lock:
            sync_handlers = set(self._sync_handlers.get(event_type, set()))
            async_handlers = set(self._async_handlers.get(event_type, set()))

        # 执行同步处理器
        for handler in sync_handlers:
            try:
                self._executor.submit(self._call_sync_handler, handler, source, event)
            except Exception as e:
                logger.error(f"提交同步处理器失败: {handler.__name__}, 错误: {e}")

        # 执行异步处理器
        if async_handlers:
            asyncio.run_coroutine_threadsafe(
                self._call_async_handlers(source, event, async_handlers),
                self._loop,
            )

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
        coros = [handler(source, event) for handler in handlers]
        results = await asyncio.gather(*coros, return_exceptions=True)

        for handler, result in zip(handlers, results, strict=False):
            if isinstance(result, Exception):
                logger.error(
                    f"异步处理器错误: {getattr(handler, '__name__', handler)}, 错误: {result}"
                )

    async def aemit(self, source: Any, event: BaseEvent) -> None:
        """异步发送事件"""
        if self._shutting_down:
            return

        event_type = type(event)

        with self._lock:
            sync_handlers = set(self._sync_handlers.get(event_type, set()))
            async_handlers = set(self._async_handlers.get(event_type, set()))

        # 执行同步处理器
        for handler in sync_handlers:
            try:
                handler(source, event)
            except Exception as e:
                logger.error(f"同步处理器错误: {handler.__name__}, 错误: {e}")

        # 执行异步处理器
        await self._call_async_handlers(source, event, async_handlers)

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

        if hasattr(self, "_loop") and self._loop and not self._loop.is_closed():
            self._loop.call_soon_threadsafe(self._loop.stop)
            if hasattr(self, "_loop_thread"):
                self._loop_thread.join(timeout=5)
            self._loop.close()

        if hasattr(self, "_executor"):
            self._executor.shutdown(wait=wait)

        with self._lock:
            self._sync_handlers.clear()
            self._async_handlers.clear()


# 全局单例
event_bus: Final[EventBus] = EventBus()

# 程序退出时关闭
atexit.register(event_bus.shutdown)
