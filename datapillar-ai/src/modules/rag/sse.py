# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 任务 SSE 事件中心."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncGenerator
from typing import Any


class JobEventHub:
    def __init__(self) -> None:
        self._subscribers: dict[int, set[asyncio.Queue[dict[str, Any]]]] = {}

    async def subscribe(
        self,
        job_id: int,
        *,
        initial_event: dict[str, Any] | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        self._subscribers.setdefault(job_id, set()).add(queue)

        try:
            if initial_event is not None:
                yield initial_event
            while True:
                payload = await queue.get()
                if payload.get("_stop"):
                    break
                yield payload
        finally:
            subscribers = self._subscribers.get(job_id)
            if subscribers:
                subscribers.discard(queue)
                if not subscribers:
                    self._subscribers.pop(job_id, None)

    async def publish(self, job_id: int, payload: dict[str, Any]) -> None:
        queues = list(self._subscribers.get(job_id, set()))
        for queue in queues:
            queue.put_nowait(payload)

    async def close(self, job_id: int) -> None:
        queues = list(self._subscribers.get(job_id, set()))
        for queue in queues:
            queue.put_nowait({"_stop": True})


job_event_hub = JobEventHub()
