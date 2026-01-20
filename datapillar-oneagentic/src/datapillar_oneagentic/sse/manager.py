"""
SSE 流管理器

目标：
- 多智能体输出以 SSE/JSON 事件流方式推送
- 支持断线重连后的事件重放（基于 Last-Event-ID）
- 支持人机交互（interrupt/resume）

SSE 架构说明：
- Orchestrator.stream() 支持三种场景：
  1. 新会话/续聊：query 不为空
  2. interrupt 恢复：resume_value 不为空
  3. 客户端需要根据 interrupt 事件决定如何恢复
- StreamManager 负责：
  - 管理订阅者（多客户端）
  - 事件缓冲和重放
  - 断线重连
  - 用户主动打断（abort）

使用示例：
```python
from datapillar_oneagentic.sse import StreamManager
from datapillar_oneagentic.core.types import SessionKey

# 创建管理器
stream_manager = StreamManager()

# 构建 SessionKey
key = SessionKey(namespace="etl_team", session_id="session123")

# 场景 1: 新会话或续聊
await stream_manager.chat(
    orchestrator=orchestrator,
    query="请帮我查询...",
    key=key,
)

# 场景 2: 恢复 interrupt（用户回答 Agent 的问题）
await stream_manager.chat(
    orchestrator=orchestrator,
    query=None,  # 可选，作为上下文
    key=key,
    resume_value="是的，我确认继续",  # 用户对 interrupt 的回答
)

# 场景 3: 用户主动打断
await stream_manager.abort(key=key)

# 订阅流
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
    """自定义 JSON 序列化器，处理 Pydantic 模型"""
    if isinstance(obj, BaseModel):
        return obj.model_dump()
    raise TypeError(f"Object of type {type(obj).__name__} is not JSON serializable")


class OrchestratorProtocol(Protocol):
    """
    Orchestrator 协议

    stream() 方法支持三种场景：
    1. 新会话/续聊：query 不为空，resume_value 为空
    2. interrupt 恢复：resume_value 不为空
    3. 纯续聊：query 不为空，已有会话状态
    """

    async def stream(
        self,
        *,
        query: str | None = None,
        key: SessionKey,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        参数：
        - query: 用户输入（新问题或续聊内容）
        - key: SessionKey（namespace + session_id）
        - resume_value: interrupt 恢复值（用户对 interrupt 的回答）

        返回：
        - SSE 事件流
        """
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
        self._runs: dict[str, _SessionRun] = {}
        self._buffer_size = max(100, buffer_size)
        self._subscriber_queue_size = max(50, subscriber_queue_size)
        self._session_ttl_seconds = max(60, session_ttl_seconds)

    def _cleanup_expired(self) -> None:
        """清理过期会话"""
        current_ms = now_ms()
        expire_before_ms = current_ms - self._session_ttl_seconds * 1000
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
        """标记会话完成"""
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
        运行编排器流

        Orchestrator.stream() 直接 yield SSE 事件，
        StreamManager 将这些事件发送给订阅者。

        参数：
        - orchestrator: 编排器实例
        - query: 用户输入（新问题或续聊）
        - key: SessionKey
        - resume_value: interrupt 恢复值

        打断处理：
        - CancelledError 表示用户主动打断
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
            logger.info(f"Run 被用户打断: key={key}")
            raise
        except Exception as exc:
            logger.error("SSE 推送失败: %s", exc, exc_info=True)
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
        统一的聊天入口

        场景：
        - resume_value 不为空：interrupt 恢复
        - query 不为空：用户消息

        打断机制：
        - 如果有旧 run 正在执行，会先打断它
        - 打断的是 run，不是 session（对话历史保留）
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
                    logger.info(f"打断旧 run: key={key}")
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
        订阅 SSE 流

        参数：
        - request: HTTP 请求（用于检测断开）
        - key: SessionKey
        - last_event_id: 上次事件 ID（用于断线重连）

        生成：
        - SSE 事件字典 {"id": str, "data": str}
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
        清理会话缓冲

        返回：
        - True: 存在并已清理
        - False: 不存在
        """
        storage_key = str(key)
        existed = storage_key in self._runs
        self._runs.pop(storage_key, None)
        return existed

    async def abort(self, *, key: SessionKey) -> bool:
        """
        打断当前 run

        打断的是 run（当前执行），不是 session（对话历史）。
        checkpoint 保存 next 字段（未完成的节点），下次可从断点恢复。

        返回：
        - True: 成功打断
        - False: 没有正在运行的 run
        """
        storage_key = str(key)
        run = self._runs.get(storage_key)

        if run is None:
            logger.warning(f"Abort 失败：session 不存在: {key}")
            return False

        async with run.lock:
            if run.running_task is None or run.running_task.done():
                logger.info(f"Abort 跳过：没有正在运行的 run: {key}")
                return False

            logger.info(f"Abort run: key={key}")
            run.running_task.cancel()

        with contextlib.suppress(asyncio.CancelledError):
            await run.running_task

        return True
