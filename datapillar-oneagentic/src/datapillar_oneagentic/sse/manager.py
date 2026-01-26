"""
SSE stream manager.

Goals:
- Stream multi-agent output as SSE/JSON events
- Replay events after reconnect (based on Last-Event-ID)
- Support human-in-the-loop interruptions (interrupt/resume)

Architecture:
- Orchestrator.stream() supports:
  1. New session or continued chat (query provided)
  2. Interrupt resume (resume_value provided)
  3. Client decides how to resume based on interrupt events
- StreamManager is responsible for:
  - Managing subscribers (multi-client)
  - Event buffering and replay
  - Reconnect handling
  - User-initiated abort

Example:
```python
from datapillar_oneagentic.sse import StreamManager
from datapillar_oneagentic.core.types import SessionKey

# Create manager
stream_manager = StreamManager()

# Build SessionKey
key = SessionKey(namespace="etl_team", session_id="session123")

# Case 1: new session or continued chat
await stream_manager.chat(
    orchestrator=orchestrator,
    query="Please help me query...",
    key=key,
)

# Case 2: resume interrupt (user answers the agent's question)
await stream_manager.chat(
    orchestrator=orchestrator,
    query=None,  # optional as context
    key=key,
    resume_value="Yes, please continue",  # user response to interrupt
)

# Case 3: user aborts
await stream_manager.abort(key=key)

# Subscribe to the stream
async for event in stream_manager.subscribe(
    request=request,
    key=key,
    last_event_id=None,
):
    yield event
```
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from collections.abc import AsyncGenerator
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any, Protocol

from pydantic import BaseModel

from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.utils.time import now_ms

if TYPE_CHECKING:
    from starlette.requests import Request

logger = logging.getLogger(__name__)


_SENTINEL = object()


def _json_serializer(obj: Any) -> Any:
    """Custom JSON serializer for Pydantic models."""
    if isinstance(obj, BaseModel):
        return obj.model_dump()
    raise TypeError(f"Object of type {type(obj).__name__} is not JSON serializable")


class OrchestratorProtocol(Protocol):
    """
    Orchestrator protocol.

    stream() supports:
    1. New session/continued chat: query provided, resume_value empty
    2. Interrupt resume: resume_value provided
    3. Continued chat: query provided with existing session state
    """

    async def stream(
        self,
        *,
        query: str | None = None,
        key: SessionKey,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        Stream execution.

        Args:
            query: User input (new or continued chat)
            key: SessionKey (namespace + session_id)
            resume_value: Resume value from interrupt (user response)

        Returns:
            SSE event stream
        """
        ...


@dataclass(slots=True)
class StreamRecord:
    """Stream record."""

    seq: int
    payload: dict[str, Any]
    created_at_ms: int


@dataclass
class _SessionRun:
    """
    Session run state.

    Concepts:
    - Session/Thread: full conversation history (persisted by LangGraph Checkpoint)
    - Run: a single graph execution (interruptible)

    Interrupts affect only the current run, not the session.
    """

    key: SessionKey
    created_at_ms: int = field(default_factory=now_ms)
    last_activity_ms: int = field(default_factory=now_ms)
    next_seq: int = 1
    buffer: list[StreamRecord] = field(default_factory=list)
    subscribers: set[asyncio.Queue[StreamRecord | object]] = field(default_factory=set)
    completed: bool = False
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    running_task: asyncio.Task | None = None


class StreamManager:
    """
    SSE stream manager.

    Features:
    - Manage SSE stream lifecycle
    - Support multiple subscribers
    - Reconnect and replay
    - Session expiration cleanup
    """

    def __init__(
        self,
        *,
        buffer_size: int = 2000,
        subscriber_queue_size: int = 500,
        session_ttl_seconds: int = 60 * 60,
    ):
        """
        Initialize the stream manager.

        Args:
            buffer_size: Event buffer size
            subscriber_queue_size: Subscriber queue size
            session_ttl_seconds: Session TTL in seconds
        """
        self._runs: dict[str, _SessionRun] = {}
        self._buffer_size = max(100, buffer_size)
        self._subscriber_queue_size = max(50, subscriber_queue_size)
        self._session_ttl_seconds = max(60, session_ttl_seconds)

    def _cleanup_expired(self) -> None:
        """Clean up expired sessions."""
        current_ms = now_ms()
        expire_before_ms = current_ms - self._session_ttl_seconds * 1000
        expired_keys = [
            key for key, run in self._runs.items() if run.last_activity_ms < expire_before_ms
        ]
        for key in expired_keys:
            self._runs.pop(key, None)

    async def _emit(self, run: _SessionRun, payload: dict[str, Any]) -> None:
        """Emit an event to all subscribers."""
        async with run.lock:
            seq = run.next_seq
            run.next_seq += 1
            run.last_activity_ms = now_ms()

            record = StreamRecord(seq=seq, payload=payload, created_at_ms=now_ms())
            run.buffer.append(record)
            if len(run.buffer) > self._buffer_size:
                run.buffer = run.buffer[-self._buffer_size :]

            dead_subscribers: list[asyncio.Queue[StreamRecord | object]] = []
            for queue in run.subscribers:
                try:
                    queue.put_nowait(record)
                except asyncio.QueueFull:
                    dead_subscribers.append(queue)

            for queue in dead_subscribers:
                run.subscribers.discard(queue)
                with contextlib.suppress(Exception):
                    queue.put_nowait(_SENTINEL)

    async def _complete(self, run: _SessionRun) -> None:
        """Mark the run as completed."""
        async with run.lock:
            run.completed = True
            run.last_activity_ms = now_ms()
            for queue in list(run.subscribers):
                with contextlib.suppress(Exception):
                    queue.put_nowait(_SENTINEL)

    async def _run_orchestrator_stream(
        self,
        *,
        orchestrator: OrchestratorProtocol,
        query: str | None,
        key: SessionKey,
        resume_value: Any | None,
    ) -> None:
        """
        Run the orchestrator stream.

        Orchestrator.stream() yields SSE events and StreamManager forwards
        them to subscribers.

        Args:
            orchestrator: Orchestrator instance
            query: User input (new or continued chat)
            key: SessionKey
            resume_value: Resume value from interrupt

        Interrupt handling:
        - CancelledError indicates a user-initiated abort
        """
        run = self._runs[str(key)]

        try:
            async for msg in orchestrator.stream(
                query=query,
                key=key,
                resume_value=resume_value,
            ):
                await self._emit(run, msg)
        except asyncio.CancelledError:
            logger.info(f"Run cancelled by user: key={key}")
            raise
        except Exception as exc:
            logger.error("SSE emit failed: %s", exc, exc_info=True)
        finally:
            await self._complete(run)

    async def chat(
        self,
        *,
        orchestrator: OrchestratorProtocol,
        query: str | None,
        key: SessionKey,
        resume_value: Any | None = None,
    ) -> None:
        """
        Unified chat entrypoint.

        Scenarios:
        - resume_value provided: interrupt resume
        - query provided: user message

        Interrupt behavior:
        - If a previous run is active, it is aborted first
        - Only the run is aborted; session history is preserved
        """
        self._cleanup_expired()
        storage_key = str(key)
        run = self._runs.get(storage_key)

        if run is None:
            run = _SessionRun(key=key)
            self._runs[storage_key] = run
        else:
            async with run.lock:
                if run.running_task and not run.running_task.done():
                    logger.info(f"Cancel previous run: key={key}")
                    run.running_task.cancel()
                    with contextlib.suppress(asyncio.CancelledError):
                        await run.running_task

                run.completed = False
                run.last_activity_ms = now_ms()
                run.buffer.clear()
                run.next_seq = 1

        run.running_task = asyncio.create_task(
            self._run_orchestrator_stream(
                orchestrator=orchestrator,
                query=query,
                key=key,
                resume_value=resume_value,
            )
        )

    async def subscribe(
        self,
        *,
        request: Request,
        key: SessionKey,
        last_event_id: int | None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        Subscribe to the SSE stream.

        Args:
            request: HTTP request (disconnect detection)
            key: SessionKey
            last_event_id: Last event ID (for reconnect)

        Yields:
            SSE event dict {"id": str, "data": str}
        """
        self._cleanup_expired()
        storage_key = str(key)
        run = self._runs.get(storage_key)
        if run is None:
            run = _SessionRun(key=key)
            self._runs[storage_key] = run

        queue: asyncio.Queue[StreamRecord | object] = asyncio.Queue(
            maxsize=self._subscriber_queue_size
        )
        async with run.lock:
            run.subscribers.add(queue)
            run.last_activity_ms = now_ms()
            replay = list(run.buffer)
            is_completed = run.completed

        last_sent_seq = last_event_id or 0

        try:
            for record in replay:
                if record.seq <= last_sent_seq:
                    continue
                last_sent_seq = record.seq
                yield {
                    "id": str(record.seq),
                    "data": json.dumps(
                        record.payload, ensure_ascii=False, default=_json_serializer
                    ),
                }

            if is_completed:
                return

            while True:
                if await request.is_disconnected():
                    break

                try:
                    item = await asyncio.wait_for(queue.get(), timeout=5)
                except TimeoutError:
                    async with run.lock:
                        if run.completed:
                            break
                    continue

                if item is _SENTINEL:
                    break

                if not isinstance(item, StreamRecord):
                    continue
                record = item
                if record.seq <= last_sent_seq:
                    continue
                last_sent_seq = record.seq

                yield {
                    "id": str(record.seq),
                    "data": json.dumps(
                        record.payload, ensure_ascii=False, default=_json_serializer
                    ),
                }
        finally:
            async with run.lock:
                run.subscribers.discard(queue)
                run.last_activity_ms = now_ms()

    def clear_session(self, *, key: SessionKey) -> bool:
        """
        Clear session buffer.

        Returns:
            True if existed and cleared, False otherwise
        """
        storage_key = str(key)
        existed = storage_key in self._runs
        self._runs.pop(storage_key, None)
        return existed

    async def abort(self, *, key: SessionKey) -> bool:
        """
        Abort the current run.

        Only the run is aborted; session history is preserved.
        Checkpoint stores the next field (unfinished node) for resume.

        Returns:
            True if aborted, False if no running run
        """
        storage_key = str(key)
        run = self._runs.get(storage_key)

        if run is None:
            logger.warning(f"Abort failed: session not found: {key}")
            return False

        async with run.lock:
            if run.running_task is None or run.running_task.done():
                logger.info(f"Abort skipped: no running run: {key}")
                return False

            logger.info(f"Abort run: key={key}")
            run.running_task.cancel()

        with contextlib.suppress(asyncio.CancelledError):
            await run.running_task

        return True
