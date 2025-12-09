# -*- coding: utf-8 -*-
"""
知识图谱 API 路由（使用 Repository 模式）
"""

from typing import AsyncGenerator
from fastapi import APIRouter, Request
import logging

from sse_starlette.sse import EventSourceResponse

logger = logging.getLogger(__name__)

from src.knowledge_graph.schemas import GraphSearchRequest
from src.knowledge_graph.service import KnowledgeGraphService

router = APIRouter()


def _get_service() -> KnowledgeGraphService:
    """获取知识图谱服务（不再需要传递 client）"""
    return KnowledgeGraphService()


@router.get("/initial")
async def get_initial_graph(
    request: Request,
    limit: int = 500,
):
    """流式获取初始图数据（SSE + MsgPack）"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    service = _get_service()
    logger.info(f"[KG] 流式获取初始图数据: user={current_user.username}, limit={limit}")

    async def event_generator() -> AsyncGenerator[dict[str, str], None]:
        async for event in service.stream_initial_graph(limit=min(limit, 2000)):
            yield {"event": event.event_type.value, "data": event.model_dump_json()}

    return EventSourceResponse(
        event_generator(),
        ping=15,
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )


@router.post("/search")
async def search_graph(
    request: Request,
    payload: GraphSearchRequest,
):
    """流式搜索知识图谱（SSE + MsgPack）"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    service = _get_service()
    logger.info(f"[KG] 流式搜索: user={current_user.username}, query={payload.query}")

    async def event_generator() -> AsyncGenerator[dict[str, str], None]:
        async for event in service.stream_search(
            query=payload.query,
            top_k=payload.top_k,
            search_type="hybrid",
        ):
            yield {"event": event.event_type.value, "data": event.model_dump_json()}

    return EventSourceResponse(
        event_generator(),
        ping=15,
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )
