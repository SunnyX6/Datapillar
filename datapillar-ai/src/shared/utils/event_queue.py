# @author Sunny
# @date 2026-01-27

"""
Asynchronous event queue

provide：
- event buffer
- Batch processing
- Back pressure control
- pause/restore
"""

import asyncio
import contextlib
import logging
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any

logger = logging.getLogger(__name__)


@dataclass
class QueueStats:
    """Queue statistics"""

    total_received: int = 0
    total_processed: int = 0
    total_failed: int = 0
    current_size: int = 0
    last_flush_time: datetime | None = None


@dataclass
class QueueConfig:
    """Queue configuration"""

    max_size: int = 10000
    batch_size: int = 100
    flush_interval_seconds: float = 5.0


class AsyncEventQueue:
    """
    Asynchronous event queue

    provide：
    - event buffer：Buffering events when write speed cannot keep up with receive speed
    - Batch processing：Write in batches after accumulating a certain amount，Improve efficiency
    - Back pressure control：Reject new events when queue is full，Prevent memory overflow
    - pause/restore：Supports pausing consumption during synchronization
    """

    def __init__(self, config: QueueConfig | None = None):
        self.config = config or QueueConfig()
        self._queue: asyncio.Queue[Any] = asyncio.Queue(maxsize=self.config.max_size)
        self._stats = QueueStats()
        self._running = False
        self._paused = False
        self._pause_event = asyncio.Event()
        self._pause_event.set()  # The initial state is non-paused
        self._processor_task: asyncio.Task | None = None
        self._processor: Callable[[list[Any]], Any] | None = None

    @property
    def stats(self) -> QueueStats:
        """Get queue statistics"""
        self._stats.current_size = self._queue.qsize()
        return self._stats

    async def put(self, event: Any) -> bool:
        """
        Add event to queue

        Returns:
            True If added successfully，False If the queue is full
        """
        try:
            self._queue.put_nowait(event)
            self._stats.total_received += 1
            return True
        except asyncio.QueueFull:
            logger.warning("Event queue is full，discard event")
            return False

    async def put_wait(self, event: Any, timeout: float = 5.0) -> bool:
        """
        Add event to queue（wait mode）

        Args:
            event: event
            timeout: timeout（seconds）

        Returns:
            True If added successfully，False if timeout
        """
        try:
            await asyncio.wait_for(self._queue.put(event), timeout=timeout)
            self._stats.total_received += 1
            return True
        except TimeoutError:
            logger.warning("Add timeout to event queue")
            return False

    async def get_batch(self, max_size: int | None = None) -> list[Any]:
        """
        Get a batch of events

        Args:
            max_size: Maximum batch size，Use configuration values by default

        Returns:
            event list
        """
        batch_size = max_size or self.config.batch_size
        batch: list[Any] = []

        # Wait for at least one event
        try:
            event = await asyncio.wait_for(
                self._queue.get(), timeout=self.config.flush_interval_seconds
            )
            batch.append(event)
        except TimeoutError:
            return batch

        # Get more events non-blockingly
        while len(batch) < batch_size:
            try:
                event = self._queue.get_nowait()
                batch.append(event)
            except asyncio.QueueEmpty:
                break

        return batch

    def set_processor(self, processor: Callable[[list[Any]], Any]) -> None:
        """Set up batch processor"""
        self._processor = processor

    @property
    def is_paused(self) -> bool:
        """Is it in paused state?"""
        return self._paused

    def pause(self) -> None:
        """Pause queue consumption（Events can still be queued，but will not be processed）"""
        if not self._paused:
            self._paused = True
            self._pause_event.clear()
            logger.info("Event queue consumption has been paused")

    def resume(self) -> None:
        """Resume queue consumption"""
        if self._paused:
            self._paused = False
            self._pause_event.set()
            logger.info("Event queue consumption has resumed")

    async def start(self) -> None:
        """Start queue processing"""
        if self._running:
            return

        self._running = True
        if self._processor:
            self._processor_task = asyncio.create_task(self._process_loop())
            logger.info("Event queue handler started")

    async def stop(self, timeout: float = 30.0) -> None:
        """Stop queue processing"""
        self._running = False

        if self._processor_task:
            # Wait for remaining events to be processed
            try:
                await asyncio.wait_for(self._drain(), timeout=timeout)
            except TimeoutError:
                logger.warning(
                    f"Queue emptying timeout，Remaining {self._queue.qsize()} events unhandled"
                )

            self._processor_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._processor_task

        logger.info("The event queue handler has stopped")

    async def _process_loop(self) -> None:
        """processing loop"""
        while self._running:
            try:
                # Wait for non-paused state
                await self._pause_event.wait()

                batch = await self.get_batch()
                if batch and self._processor:
                    try:
                        await self._processor(batch)
                        self._stats.total_processed += len(batch)
                        self._stats.last_flush_time = datetime.now()
                    except Exception as e:
                        self._stats.total_failed += len(batch)
                        logger.error(f"Batch processing failed: {e}")
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Handling loop exceptions: {e}")

    async def _drain(self) -> None:
        """empty queue"""
        while not self._queue.empty() and self._processor:
            batch = await self.get_batch()
            if batch:
                try:
                    await self._processor(batch)
                    self._stats.total_processed += len(batch)
                except Exception as e:
                    self._stats.total_failed += len(batch)
                    logger.error(f"Processing failed while draining: {e}")
