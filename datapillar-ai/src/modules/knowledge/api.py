# -*- coding: utf-8 -*-
"""
知识图谱 API 路由（使用 Repository 模式）
"""

from typing import Any
from fastapi import APIRouter, Request
import logging

logger = logging.getLogger(__name__)

from src.modules.knowledge.schemas import GraphSearchRequest
from src.modules.knowledge.service import KnowledgeGraphService

router = APIRouter()


def _get_service() -> KnowledgeGraphService:
    """获取知识图谱服务（不再需要传递 client）"""
    return KnowledgeGraphService()


@router.get("/initial")
async def get_initial_graph(
    request: Request,
    limit: int = 500,
):
    """获取初始图数据（非 SSE，一次性 JSON 返回）"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    service = _get_service()
    safe_limit = min(max(limit, 1), 2000)
    logger.info(f"[KG] 获取初始图数据: user={current_user.username}, limit={safe_limit}")

    graph = service.get_initial_graph(limit=safe_limit)
    return graph.model_dump()


@router.post("/search")
async def search_graph(
    request: Request,
    payload: GraphSearchRequest,
):
    """搜索知识图谱（非 SSE，一次性 JSON 返回）"""
    # 中间件已验证，直接从 request.state 获取当前用户
    current_user = request.state.current_user
    service = _get_service()
    logger.info(f"[KG] 搜索: user={current_user.username}, query={payload.query}")
    result: dict[str, Any] = service.search_by_text(
        query=payload.query,
        top_k=payload.top_k,
        search_type="hybrid",
    )
    return result
