"""
SSE 流管理器

目标：
- 多智能体输出以 SSE/JSON 事件流方式推送
- 支持断线重连后的事件重放（基于 Last-Event-ID）
- 明确区分：
  - transport resume：断线续传（Last-Event-ID / seq）
  - orchestrator resume_value：人机交互 interrupt 的继续执行

SSE 架构说明：
- Orchestrator.stream() 使用 LangGraph 的 astream_events 获取所有事件
- Orchestrator.stream() 直接 yield SSE 事件（dict 格式）
- StreamManager 负责：
  - 管理订阅者（多客户端）
  - 事件缓冲和重放
  - 断线重连

使用示例：
```python
from datapillar_oneagentic.sse import StreamManager

# 创建管理器
stream_manager = StreamManager()

# 启动流
await stream_manager.chat(
    orchestrator=orchestrator,
    user_query="请帮我查询...",
    session_id="session123",
    user_id="user456",
)

# 订阅流
async for event in stream_manager.subscribe(
    request=request,
    session_id="session123",
    user_id="user456",
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
import time
from collections.abc import AsyncGenerator
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any, Protocol

from pydantic import BaseModel

from datapillar_oneagentic.sse.event import SseEvent

if TYPE_CHECKING:
    from starlette.requests import Request

logger = logging.getLogger(__name__)


_SENTINEL = object()


def _now_ms() -> int:
    return int(time.time() * 1000)


def _json_serializer(obj: Any) -> Any:
    """自定义 JSON 序列化器，处理 Pydantic 模型"""
    if isinstance(obj, BaseModel):
        return obj.model_dump()
    raise TypeError(f"Object of type {type(obj).__name__} is not JSON serializable")


class OrchestratorProtocol(Protocol):
    """
    Orchestrator 协议

    stream() 方法直接 yield SSE 事件（dict 格式），
    SSE 事件由 Orchestrator 内部通过 astream_events 获取并转换。
    """

    async def stream(
        self,
        *,
        query: str,
        session_id: str,
        user_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """流式执行，直接 yield SSE 事件"""
        ...


@dataclass(slots=True)
class StreamRecord:
    """流记录"""

    seq: int
    payload: dict[str, Any]
    created_at_ms: int


@dataclass
class _SessionRun:
    """
    会话运行状态

    概念区分：
    - Session/Thread: 整个对话历史（由 LangGraph Checkpoint 持久化）
    - Run: 一次图执行（可被打断）

    打断只影响当前 run，不影响 session。
    """

    user_id: str
    session_id: str
    created_at_ms: int = field(default_factory=_now_ms)
    last_activity_ms: int = field(default_factory=_now_ms)
    next_seq: int = 1
    buffer: list[StreamRecord] = field(default_factory=list)
    subscribers: set[asyncio.Queue[StreamRecord | object]] = field(default_factory=set)
    completed: bool = False
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    running_task: asyncio.Task | None = None


class StreamManager:
    """
    SSE 流管理器

    功能：
    - 管理 SSE 流的生命周期
    - 支持多订阅者
    - 支持断线重连和事件重放
    - 会话过期清理
    """

    def __init__(
        self,
        *,
        buffer_size: int = 2000,
        subscriber_queue_size: int = 500,
        session_ttl_seconds: int = 60 * 60,
    ):
        """
        初始化流管理器

        参数：
        - buffer_size: 事件缓冲区大小
        - subscriber_queue_size: 订阅者队列大小
        - session_ttl_seconds: 会话过期时间（秒）
        """
        self._runs: dict[tuple[str, str], _SessionRun] = {}
        self._buffer_size = max(100, buffer_size)
        self._subscriber_queue_size = max(50, subscriber_queue_size)
        self._session_ttl_seconds = max(60, session_ttl_seconds)

    def _key(self, user_id: str, session_id: str) -> tuple[str, str]:
        return user_id, session_id

    def _cleanup_expired(self) -> None:
        """清理过期会话"""
        now_ms = _now_ms()
        expire_before_ms = now_ms - self._session_ttl_seconds * 1000
        expired_keys = [
            key for key, run in self._runs.items() if run.last_activity_ms < expire_before_ms
        ]
        for key in expired_keys:
            self._runs.pop(key, None)

    async def _emit(self, run: _SessionRun, payload: dict[str, Any]) -> None:
        """发送事件到所有订阅者"""
        async with run.lock:
            seq = run.next_seq
            run.next_seq += 1
            run.last_activity_ms = _now_ms()

            record = StreamRecord(seq=seq, payload=payload, created_at_ms=_now_ms())
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
        """标记会话完成"""
        async with run.lock:
            run.completed = True
            run.last_activity_ms = _now_ms()
            for queue in list(run.subscribers):
                with contextlib.suppress(Exception):
                    queue.put_nowait(_SENTINEL)

    async def _run_orchestrator_stream(
        self,
        *,
        orchestrator: OrchestratorProtocol,
        query: str | None,
        session_id: str,
        user_id: str,
        resume_value: Any | None,
    ) -> None:
        """
        运行编排器流

        Orchestrator.stream() 直接 yield SSE 事件，
        StreamManager 将这些事件发送给订阅者。

        打断处理：
        - CancelledError 表示用户主动打断
        - 发送 aborted 事件通知前端
        """
        run = self._runs[self._key(user_id, session_id)]

        try:
            async for msg in orchestrator.stream(
                query=query or "",
                session_id=session_id,
                user_id=user_id,
            ):
                await self._emit(run, msg)
        except asyncio.CancelledError:
            logger.info(f"Run 被用户打断: session={session_id}")

            await self._emit(
                run,
                SseEvent.aborted_event(message="已停止").to_dict(),
            )
            raise
        except Exception as exc:
            logger.error("SSE 推送失败: %s", exc, exc_info=True)
            await self._emit(
                run,
                SseEvent.error_event(message="执行失败", detail=str(exc)).to_dict(),
            )
        finally:
            await self._complete(run)

    async def chat(
        self,
        *,
        orchestrator: OrchestratorProtocol,
        query: str | None,
        session_id: str,
        user_id: str,
        resume_value: Any | None = None,
    ) -> None:
        """
        统一的聊天入口

        场景：
        - resume_value 不为空：interrupt 恢复
        - query 不为空：用户消息

        打断机制：
        - 如果有旧 run 正在执行，会先打断它
        - 打断的是 run，不是 session（对话历史保留）
        """
        self._cleanup_expired()
        key = self._key(user_id, session_id)
        run = self._runs.get(key)

        if run is None:
            run = _SessionRun(user_id=user_id, session_id=session_id)
            self._runs[key] = run
        else:
            async with run.lock:
                if run.running_task and not run.running_task.done():
                    logger.info(f"打断旧 run: session={session_id}")
                    run.running_task.cancel()
                    try:
                        await run.running_task
                    except asyncio.CancelledError:
                        pass

                run.completed = False
                run.last_activity_ms = _now_ms()
                run.buffer.clear()
                run.next_seq = 1

        run.running_task = asyncio.create_task(
            self._run_orchestrator_stream(
                orchestrator=orchestrator,
                query=query,
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
        """
        订阅 SSE 流

        参数：
        - request: HTTP 请求（用于检测断开）
        - session_id: 会话 ID
        - user_id: 用户 ID
        - last_event_id: 上次事件 ID（用于断线重连）

        生成：
        - SSE 事件字典 {"id": str, "data": str}
        """
        self._cleanup_expired()
        key = self._key(user_id, session_id)
        run = self._runs.get(key)
        if run is None:
            run = _SessionRun(user_id=user_id, session_id=session_id)
            self._runs[key] = run

        queue: asyncio.Queue[StreamRecord | object] = asyncio.Queue(
            maxsize=self._subscriber_queue_size
        )
        async with run.lock:
            run.subscribers.add(queue)
            run.last_activity_ms = _now_ms()
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
                run.last_activity_ms = _now_ms()

    def clear_session(self, *, user_id: str, session_id: str) -> bool:
        """
        清理会话缓冲

        返回：
        - True: 存在并已清理
        - False: 不存在
        """
        key = self._key(user_id, session_id)
        existed = key in self._runs
        self._runs.pop(key, None)
        return existed

    async def abort(self, *, user_id: str, session_id: str) -> bool:
        """
        打断当前 run

        打断的是 run（当前执行），不是 session（对话历史）。
        checkpoint 保存 next 字段（未完成的节点），下次可从断点恢复。

        返回：
        - True: 成功打断
        - False: 没有正在运行的 run
        """
        key = self._key(user_id, session_id)
        run = self._runs.get(key)

        if run is None:
            logger.warning(f"Abort 失败：session 不存在: {session_id}")
            return False

        async with run.lock:
            if run.running_task is None or run.running_task.done():
                logger.info(f"Abort 跳过：没有正在运行的 run: {session_id}")
                return False

            logger.info(f"Abort run: session={session_id}")
            run.running_task.cancel()

        try:
            await run.running_task
        except asyncio.CancelledError:
            pass

        return True
