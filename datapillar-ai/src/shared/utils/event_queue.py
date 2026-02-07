# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
异步事件队列

提供：
- 事件缓冲
- 批量处理
- 背压控制
- 暂停/恢复
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
    """队列统计信息"""

    total_received: int = 0
    total_processed: int = 0
    total_failed: int = 0
    current_size: int = 0
    last_flush_time: datetime | None = None


@dataclass
class QueueConfig:
    """队列配置"""

    max_size: int = 10000
    batch_size: int = 100
    flush_interval_seconds: float = 5.0


class AsyncEventQueue:
    """
    异步事件队列

    提供：
    - 事件缓冲：当写入速度跟不上接收速度时缓冲事件
    - 批量处理：积累一定数量后批量写入，提高效率
    - 背压控制：队列满时拒绝新事件，防止内存溢出
    - 暂停/恢复：支持在同步期间暂停消费
    """

    def __init__(self, config: QueueConfig | None = None):
        self.config = config or QueueConfig()
        self._queue: asyncio.Queue[Any] = asyncio.Queue(maxsize=self.config.max_size)
        self._stats = QueueStats()
        self._running = False
        self._paused = False
        self._pause_event = asyncio.Event()
        self._pause_event.set()  # 初始状态为非暂停
        self._processor_task: asyncio.Task | None = None
        self._processor: Callable[[list[Any]], Any] | None = None

    @property
    def stats(self) -> QueueStats:
        """获取队列统计"""
        self._stats.current_size = self._queue.qsize()
        return self._stats

    async def put(self, event: Any) -> bool:
        """
        添加事件到队列

        Returns:
            True 如果成功添加，False 如果队列已满
        """
        try:
            self._queue.put_nowait(event)
            self._stats.total_received += 1
            return True
        except asyncio.QueueFull:
            logger.warning("事件队列已满，丢弃事件")
            return False

    async def put_wait(self, event: Any, timeout: float = 5.0) -> bool:
        """
        添加事件到队列（等待模式）

        Args:
            event: 事件
            timeout: 超时时间（秒）

        Returns:
            True 如果成功添加，False 如果超时
        """
        try:
            await asyncio.wait_for(self._queue.put(event), timeout=timeout)
            self._stats.total_received += 1
            return True
        except TimeoutError:
            logger.warning("事件队列添加超时")
            return False

    async def get_batch(self, max_size: int | None = None) -> list[Any]:
        """
        获取一批事件

        Args:
            max_size: 最大批量大小，默认使用配置值

        Returns:
            事件列表
        """
        batch_size = max_size or self.config.batch_size
        batch: list[Any] = []

        # 至少等待一个事件
        try:
            event = await asyncio.wait_for(
                self._queue.get(), timeout=self.config.flush_interval_seconds
            )
            batch.append(event)
        except TimeoutError:
            return batch

        # 非阻塞获取更多事件
        while len(batch) < batch_size:
            try:
                event = self._queue.get_nowait()
                batch.append(event)
            except asyncio.QueueEmpty:
                break

        return batch

    def set_processor(self, processor: Callable[[list[Any]], Any]) -> None:
        """设置批量处理器"""
        self._processor = processor

    @property
    def is_paused(self) -> bool:
        """是否处于暂停状态"""
        return self._paused

    def pause(self) -> None:
        """暂停队列消费（事件仍可入队，但不会被处理）"""
        if not self._paused:
            self._paused = True
            self._pause_event.clear()
            logger.info("事件队列消费已暂停")

    def resume(self) -> None:
        """恢复队列消费"""
        if self._paused:
            self._paused = False
            self._pause_event.set()
            logger.info("事件队列消费已恢复")

    async def start(self) -> None:
        """启动队列处理"""
        if self._running:
            return

        self._running = True
        if self._processor:
            self._processor_task = asyncio.create_task(self._process_loop())
            logger.info("事件队列处理器已启动")

    async def stop(self, timeout: float = 30.0) -> None:
        """停止队列处理"""
        self._running = False

        if self._processor_task:
            # 等待处理完剩余事件
            try:
                await asyncio.wait_for(self._drain(), timeout=timeout)
            except TimeoutError:
                logger.warning(f"队列排空超时，剩余 {self._queue.qsize()} 个事件未处理")

            self._processor_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._processor_task

        logger.info("事件队列处理器已停止")

    async def _process_loop(self) -> None:
        """处理循环"""
        while self._running:
            try:
                # 等待非暂停状态
                await self._pause_event.wait()

                batch = await self.get_batch()
                if batch and self._processor:
                    try:
                        await self._processor(batch)
                        self._stats.total_processed += len(batch)
                        self._stats.last_flush_time = datetime.now()
                    except Exception as e:
                        self._stats.total_failed += len(batch)
                        logger.error(f"批量处理失败: {e}")
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"处理循环异常: {e}")

    async def _drain(self) -> None:
        """排空队列"""
        while not self._queue.empty() and self._processor:
            batch = await self.get_batch()
            if batch:
                try:
                    await self._processor(batch)
                    self._stats.total_processed += len(batch)
                except Exception as e:
                    self._stats.total_failed += len(batch)
                    logger.error(f"排空时处理失败: {e}")
