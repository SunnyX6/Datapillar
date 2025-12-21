"""
API 路由聚合

统一注册所有模块的 API 路由
"""

from fastapi import APIRouter

from src.modules.openlineage import router as lineage_router
from src.modules.knowledge import router as knowledge_router
from src.modules.etl import router as etl_router

api_router = APIRouter()

# 注册 OpenLineage Sink 模块（血缘采集）
api_router.include_router(
    lineage_router,
    prefix="/v1/lineage",
    tags=["OpenLineage Sink - 血缘采集"],
)

# 注册 Knowledge 模块（知识图谱）
api_router.include_router(
    knowledge_router,
    prefix="/knowledge",
    tags=["Knowledge - 知识图谱"],
)

# 注册 ETL 模块（工作流生成）
api_router.include_router(
    etl_router,
    prefix="/agent",
    tags=["ETL - 智能工作流"],
)
