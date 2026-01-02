"""
ETL 模块 - 智能 ETL 工作流生成

路由: /api/ai/etl
"""

from __future__ import annotations

__all__ = ["router", "EtlOrchestrator", "create_etl_orchestrator"]


def __getattr__(name: str):
    if name == "router":
        from src.modules.etl.api import router

        return router
    if name in {"EtlOrchestrator", "create_etl_orchestrator"}:
        from src.modules.etl.orchestrator import EtlOrchestrator, create_etl_orchestrator

        return {"EtlOrchestrator": EtlOrchestrator, "create_etl_orchestrator": create_etl_orchestrator}[name]
    raise AttributeError(name)
