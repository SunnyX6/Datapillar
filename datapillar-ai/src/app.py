# @author Sunny
# @date 2026-01-27

"""FastAPI 应用启动入口。"""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from src.api.health import health_router
from src.api.router import api_router
from src.bootstrap import create_lifespan
from src.shared.auth.middleware import AuthMiddleware
from src.shared.config import settings
from src.shared.config.logging import setup_logging
from src.shared.exception import register_exception_handlers


def create_app() -> FastAPI:
    """创建 FastAPI 应用。"""
    setup_logging()

    app = FastAPI(
        title="Datapillar AI",
        description="AI 工作流生成服务",
        version="3.0.0",
        lifespan=create_lifespan(settings=settings),
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.add_middleware(AuthMiddleware)
    register_exception_handlers(app)

    app.include_router(api_router, prefix="/api/ai")
    app.include_router(health_router)

    @app.get("/api/ai/openapi.json", include_in_schema=False)
    async def ai_openapi_all() -> JSONResponse:
        return JSONResponse(content=app.openapi())

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "src.app:app",
        host="0.0.0.0",  # noqa: S104
        port=7003,
        reload=False,
    )
