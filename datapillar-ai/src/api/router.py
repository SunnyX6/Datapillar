# -*- coding: utf-8 -*-
"""
API 路由聚合
"""

from fastapi import APIRouter

from src.agent.router import router as agent_router
from src.knowledge_graph.router import router as kg_router

api_router = APIRouter()

# 注册 Agent 路由
api_router.include_router(agent_router, prefix="/agent", tags=["Agent"])

# 注册知识图谱路由
api_router.include_router(kg_router, prefix="/knowledge", tags=["知识图谱"])
