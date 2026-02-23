# @author Sunny
# @date 2026-02-19

"""LLM Playground API。"""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, Request
from sse_starlette.sse import EventSourceResponse

from src.modules.llm.schemas import PlaygroundChatRequest
from src.modules.llm.service import llm_playground_service
from src.shared.exception import ExceptionMapper

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post(
    "/chat",
    response_class=EventSourceResponse,
    responses={
        200: {
            "description": "Playground SSE 输出（思考流 + 正文流）",
            "content": {
                "text/event-stream": {
                    "schema": {
                        "type": "string",
                        "example": (
                            "event: reasoning_delta\\n"
                            "data: {\"delta\":\"思考片段\"}\\n\\n"
                            "event: delta\\n"
                            "data: {\"delta\":\"回答片段\"}\\n\\n"
                            "event: done\\n"
                            "data: {\"content\":\"最终回答\",\"reasoning\":\"完整思考\"}\\n\\n"
                        ),
                    }
                }
            },
        }
    },
)
async def playground_chat(request: Request, payload: PlaygroundChatRequest) -> EventSourceResponse:
    """Playground 聊天（SSE）。"""
    current_user = request.state.current_user

    async def event_stream():
        text_chunks: list[str] = []
        thinking_chunks: list[str] = []
        try:
            async for delta in llm_playground_service.stream_chat(
                tenant_id=current_user.tenant_id,
                tenant_code=current_user.tenant_code,
                payload=payload,
            ):
                if delta.thinking_delta:
                    thinking_chunks.append(delta.thinking_delta)
                    yield {
                        "event": "reasoning_delta",
                        "data": json.dumps({"delta": delta.thinking_delta}, ensure_ascii=False),
                    }
                if delta.text_delta:
                    text_chunks.append(delta.text_delta)
                    yield {
                        "event": "delta",
                        "data": json.dumps({"delta": delta.text_delta}, ensure_ascii=False),
                    }

            yield {
                "event": "done",
                "data": json.dumps(
                    {
                        "content": "".join(text_chunks),
                        "reasoning": "".join(thinking_chunks),
                    },
                    ensure_ascii=False,
                ),
            }
        except Exception as exc:  # pragma: no cover - SSE 防御分支
            detail = ExceptionMapper.resolve(exc)
            if detail.server_error:
                logger.error("Playground 模型调用失败: %s", exc, exc_info=True)
            else:
                logger.warning("Playground 请求异常: %s", detail.message)
            yield {
                "event": "error",
                "data": json.dumps(
                    {
                        "code": detail.error_code,
                        "type": detail.error_type,
                        "message": detail.message,
                    },
                    ensure_ascii=False,
                ),
            }

    return EventSourceResponse(
        event_stream(),
        ping=15,
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
