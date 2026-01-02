# -*- coding: utf-8 -*-
"""
ETL SSE 流管理器

目标：
- ETL 多智能体输出以 SSE/JSON 事件流方式推送
- 支持断线重连后的事件重放（基于 Last-Event-ID）
- 明确区分：
  - transport resume：断线续传（Last-Event-ID / seq）
  - orchestrator resume_value：人机交互 interrupt 的继续执行
"""

from __future__ import annotations

import asyncio
import json
import time
from dataclasses import dataclass, field
from typing import Any, AsyncGenerator

import logging

from starlette.requests import Request

from src.modules.etl.orchestrator import EtlOrchestrator
from src.modules.etl.schemas.sse_msg import SseEvent

logger = logging.getLogger(__name__)


_SENTINEL = object()


def _now_ms() -> int:
    return int(time.time() * 1000)


@dataclass(slots=True)
class StreamRecord:
    seq: int
    payload: dict[str, Any]
    created_at_ms: int


@dataclass
class _SessionRun:
    user_id: str
    session_id: str
    created_at_ms: int = field(default_factory=_now_ms)
    last_activity_ms: int = field(default_factory=_now_ms)
    next_seq: int = 1
    buffer: list[StreamRecord] = field(default_factory=list)
    subscribers: set[asyncio.Queue[StreamRecord | object]] = field(default_factory=set)
    completed: bool = False
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class EtlStreamManager:
    def __init__(
        self,
        *,
        buffer_size: int = 2000,
        subscriber_queue_size: int = 500,
        session_ttl_seconds: int = 60 * 60,
    ):
        self._runs: dict[tuple[str, str], _SessionRun] = {}
        self._buffer_size = max(100, buffer_size)
        self._subscriber_queue_size = max(50, subscriber_queue_size)
        self._session_ttl_seconds = max(60, session_ttl_seconds)

    def _key(self, user_id: str, session_id: str) -> tuple[str, str]:
        return user_id, session_id

    def _cleanup_expired(self) -> None:
        now_ms = _now_ms()
        expire_before_ms = now_ms - self._session_ttl_seconds * 1000
        expired_keys = [
            key for key, run in self._runs.items()
            if run.last_activity_ms < expire_before_ms
        ]
        for key in expired_keys:
            self._runs.pop(key, None)

    async def _emit(self, run: _SessionRun, payload: dict[str, Any]) -> None:
        async with run.lock:
            seq = run.next_seq
            run.next_seq += 1
            run.last_activity_ms = _now_ms()

            record = StreamRecord(seq=seq, payload=payload, created_at_ms=_now_ms())
            run.buffer.append(record)
            if len(run.buffer) > self._buffer_size:
                run.buffer = run.buffer[-self._buffer_size:]

            dead_subscribers: list[asyncio.Queue[StreamRecord | object]] = []
            for queue in run.subscribers:
                try:
                    queue.put_nowait(record)
                except asyncio.QueueFull:
                    dead_subscribers.append(queue)

            for queue in dead_subscribers:
                run.subscribers.discard(queue)
                try:
                    queue.put_nowait(_SENTINEL)
                except Exception:
                    pass

    async def _complete(self, run: _SessionRun) -> None:
        async with run.lock:
            run.completed = True
            run.last_activity_ms = _now_ms()
            for queue in list(run.subscribers):
                try:
                    queue.put_nowait(_SENTINEL)
                except Exception:
                    pass

    async def _run_orchestrator_stream(
        self,
        *,
        orchestrator: EtlOrchestrator,
        user_input: str | None,
        session_id: str,
        user_id: str,
        resume_value: Any | None,
    ) -> None:
        run = self._runs[self._key(user_id, session_id)]
        try:
            async for msg in orchestrator.stream(
                user_input=user_input or "",
                session_id=session_id,
                user_id=user_id,
                resume_value=resume_value,
            ):
                await self._emit(run, msg)
        except Exception as exc:
            logger.error("ETL SSE 推送失败: %s", exc, exc_info=True)
            await self._emit(
                run,
                SseEvent.error_event(message="执行失败", detail=str(exc)).to_dict(),
            )
        finally:
            await self._complete(run)

    async def start(
        self,
        *,
        orchestrator: EtlOrchestrator,
        user_input: str,
        session_id: str,
        user_id: str,
    ) -> None:
        self._cleanup_expired()
        key = self._key(user_id, session_id)

        run = _SessionRun(user_id=user_id, session_id=session_id)
        self._runs[key] = run

        asyncio.create_task(
            self._run_orchestrator_stream(
                orchestrator=orchestrator,
                user_input=user_input,
                session_id=session_id,
                user_id=user_id,
                resume_value=None,
            )
        )

    async def continue_from_interrupt(
        self,
        *,
        orchestrator: EtlOrchestrator,
        session_id: str,
        user_id: str,
        resume_value: Any,
    ) -> None:
        self._cleanup_expired()
        key = self._key(user_id, session_id)
        run = self._runs.get(key)
        if run is None:
            run = _SessionRun(user_id=user_id, session_id=session_id)
            self._runs[key] = run

        async with run.lock:
            run.completed = False
            run.last_activity_ms = _now_ms()

        asyncio.create_task(
            self._run_orchestrator_stream(
                orchestrator=orchestrator,
                user_input=None,
                session_id=session_id,
                user_id=user_id,
                resume_value=resume_value,
            )
        )

    async def subscribe(
        self,
        *,
        request: Request,
        session_id: str,
        user_id: str,
        last_event_id: int | None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        self._cleanup_expired()
        key = self._key(user_id, session_id)
        run = self._runs.get(key)
        if run is None:
            run = _SessionRun(user_id=user_id, session_id=session_id)
            self._runs[key] = run

        queue: asyncio.Queue[StreamRecord | object] = asyncio.Queue(maxsize=self._subscriber_queue_size)
        async with run.lock:
            run.subscribers.add(queue)
            run.last_activity_ms = _now_ms()
            replay = list(run.buffer)
            is_completed = run.completed

        last_sent_seq = last_event_id or 0

        try:
            # 先重放历史（Last-Event-ID 之后）
            for record in replay:
                if record.seq <= last_sent_seq:
                    continue
                last_sent_seq = record.seq
                yield {
                    "id": str(record.seq),
                    "data": json.dumps(record.payload, ensure_ascii=False),
                }

            # 如果已经完成且没有新数据，直接结束
            if is_completed:
                return

            # 再消费实时事件
            while True:
                if await request.is_disconnected():
                    break

                try:
                    item = await asyncio.wait_for(queue.get(), timeout=5)
                except asyncio.TimeoutError:
                    async with run.lock:
                        if run.completed:
                            break
                    continue

                if item is _SENTINEL:
                    break

                record = item
                if record.seq <= last_sent_seq:
                    continue
                last_sent_seq = record.seq

                yield {
                    "id": str(record.seq),
                    "data": json.dumps(record.payload, ensure_ascii=False),
                }
        finally:
            async with run.lock:
                run.subscribers.discard(queue)
                run.last_activity_ms = _now_ms()


etl_stream_manager = EtlStreamManager()
