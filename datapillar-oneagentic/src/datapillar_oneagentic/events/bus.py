# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Event bus.

Provides publish/subscribe for events.

Features:
- Sync/async handlers
- Thread-safe
- Scoped isolation (for tests)
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

# Handler types
SyncHandler = Callable[[Any, BaseEvent], None]
AsyncHandler = Callable[[Any, BaseEvent], Any]
Handler = SyncHandler | AsyncHandler


def _is_async_handler(handler: Handler) -> bool:
    """Return True if the handler is async."""
    return asyncio.iscoroutinefunction(handler)


class EventBus:
    """
    Event bus.

    Example:
    ```python
    from datapillar_oneagentic.events import EventBus, AgentStartedEvent

    event_bus = EventBus()

    @event_bus.on(AgentStartedEvent)
    def on_agent_started(source, event):
        print(f"Agent {event.agent_name} started")

    # Emit an event
    await event_bus.emit(self, AgentStartedEvent(agent_id="analyst", agent_name="Analyst"))
    ```
    """

    def __init__(self) -> None:
        """Initialize the bus."""
        self._lock = threading.RLock()
        self._sync_handlers: dict[type[BaseEvent], set[SyncHandler]] = {}
        self._async_handlers: dict[type[BaseEvent], set[AsyncHandler]] = {}
        self._shutting_down = False

        # Thread pool for sync handlers.
        self._executor = ThreadPoolExecutor(
            max_workers=5,
            thread_name_prefix="EventBusSync",
        )

    def on(
        self,
        event_type: type[T],
    ) -> Callable[[Handler], Handler]:
        """Decorator: register an event handler."""

        def decorator(handler: Handler) -> Handler:
            self.register(event_type, handler)
            return handler

        return decorator

    def register(
        self,
        event_type: type[BaseEvent],
        handler: Handler,
    ) -> None:
        """Register an event handler."""
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
        """Unregister an event handler."""
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
        """Invoke a sync handler."""
        try:
            handler(source, event)
        except Exception as e:
            logger.error(f"Sync handler error: {handler.__name__}, error={e}")

    async def _call_async_handlers(
        self,
        source: Any,
        event: BaseEvent,
        handlers: set[AsyncHandler],
    ) -> None:
        """Invoke async handlers."""
        # Convert to list for stable order (set iteration is not deterministic).
        handlers_list = list(handlers)
        coros = [handler(source, event) for handler in handlers_list]
        results = await asyncio.gather(*coros, return_exceptions=True)

        for handler, result in zip(handlers_list, results, strict=False):
            if isinstance(result, Exception):
                logger.error(
                    f"Async handler error: {getattr(handler, '__name__', handler)}, error={result}"
                )

    async def emit(self, source: Any, event: BaseEvent) -> None:
        """Emit an event."""
        if self._shutting_down:
            return

        event_type = type(event)

        with self._lock:
            sync_handlers = set(self._sync_handlers.get(event_type, set()))
            async_handlers = set(self._async_handlers.get(event_type, set()))

        loop = asyncio.get_running_loop()

        # Run sync handlers in a thread pool to avoid blocking the event loop.
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

        # Run sync and async handlers concurrently.
        all_tasks: list[Any] = sync_tasks
        if async_handlers:
            all_tasks.append(self._call_async_handlers(source, event, async_handlers))

        if all_tasks:
            await asyncio.gather(*all_tasks, return_exceptions=True)

    @contextmanager
    def scoped_handlers(self) -> Generator[None, Any, None]:
        """Scoped isolation (for tests)."""
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
        """Clear all handlers."""
        with self._lock:
            self._sync_handlers.clear()
            self._async_handlers.clear()

    def handler_count(self, event_type: type[BaseEvent] | None = None) -> int:
        """Return the handler count."""
        with self._lock:
            if event_type is None:
                sync_count = sum(len(h) for h in self._sync_handlers.values())
                async_count = sum(len(h) for h in self._async_handlers.values())
                return sync_count + async_count

            sync_count = len(self._sync_handlers.get(event_type, set()))
            async_count = len(self._async_handlers.get(event_type, set()))
            return sync_count + async_count

    def shutdown(self, wait: bool = True) -> None:
        """Shutdown the event bus."""
        with self._lock:
            self._shutting_down = True

        if hasattr(self, "_executor"):
            self._executor.shutdown(wait=wait)

        with self._lock:
            self._sync_handlers.clear()
            self._async_handlers.clear()
