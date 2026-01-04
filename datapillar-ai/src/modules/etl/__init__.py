"""
ETL 模块 - 智能 ETL 工作流生成

路由: /api/ai/etl
"""

from __future__ import annotations

from typing import TYPE_CHECKING

__all__ = ["router"]

if TYPE_CHECKING:
    from fastapi import APIRouter

    from src.modules.etl.api import router as router


def __getattr__(name: str):
    if name == "router":
        from src.modules.etl.api import router

        return router
    raise AttributeError(name)
