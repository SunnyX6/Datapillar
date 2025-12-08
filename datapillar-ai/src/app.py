# -*- coding: utf-8 -*-
"""
FastAPI 应用入口
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import logging

from src.core.logging import setup_logging

logger = logging.getLogger(__name__)

from src.agent.orchestrator import Orchestrator, create_orchestrator
from src.core.exceptions import BaseError, AuthenticationError, AuthorizationError
from src.api.router import api_router
from src.core.config import settings
from src.core.database import Neo4jClient, RedisClient, MySQLClient
from src.tools.agent_tools import init_tools
from src.auth.middleware import AuthMiddleware


def create_app() -> FastAPI:
    """创建 FastAPI 应用"""
    setup_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        logger.info("=" * 60)
        logger.info("Data Builder AI - 启动中...")
        logger.info(f"Neo4j URI: {settings.neo4j_uri}")
        logger.info(f"Neo4j Database: {settings.neo4j_database}")
        logger.info("=" * 60)

        neo4j_client = Neo4jClient()
        mysql_client = MySQLClient()
        redis_client: RedisClient | None = None
        orchestrator: Orchestrator | None = None
        try:
            neo4j_client.connect()
            mysql_client.connect()
            redis_client = RedisClient()
            await redis_client.connect()

            # 初始化工具层依赖
            init_tools(neo4j_client, mysql_client)

            orchestrator = await create_orchestrator(redis_client)
            app.state.neo4j_client = neo4j_client
            app.state.mysql_client = mysql_client
            app.state.redis_client = redis_client
            app.state.orchestrator = orchestrator
            logger.info("FastAPI 应用启动完成")
            yield
        finally:
            logger.info("Data Builder AI - 关闭中...")
            if orchestrator:
                logger.info("Orchestrator 已释放")
            if redis_client:
                await redis_client.close()
            mysql_client.close()
            neo4j_client.close()

    app = FastAPI(
        title="Data Builder AI",
        description="AI 工作流生成服务",
        version="3.0.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 全局认证中间件
    app.add_middleware(AuthMiddleware)

    # 全局异常处理器
    @app.exception_handler(AuthenticationError)
    async def auth_error_handler(request: Request, exc: AuthenticationError):
        return JSONResponse(status_code=401, content={"error": exc.message})

    @app.exception_handler(AuthorizationError)
    async def authz_error_handler(request: Request, exc: AuthorizationError):
        return JSONResponse(status_code=403, content={"error": exc.message})

    @app.exception_handler(BaseError)
    async def base_error_handler(request: Request, exc: BaseError):
        logger.error(f"业务异常: {exc.message}", exc_info=True)
        return JSONResponse(status_code=500, content={"error": exc.message})

    @app.exception_handler(Exception)
    async def global_error_handler(request: Request, exc: Exception):
        logger.exception(f"未捕获异常: {exc}")
        return JSONResponse(status_code=500, content={"error": "服务器内部错误"})

    # 注册 API 路由
    app.include_router(api_router)

    # 健康检查
    @app.get("/health")
    async def health_check():
        neo4j_client = getattr(app.state, "neo4j_client", None)
        connected = False
        if neo4j_client:
            try:
                neo4j_client.execute_query("RETURN 1 AS test")
                connected = True
            except Exception as e:
                logger.warning(f"Neo4j 健康检查失败: {e}")

        return {
            "status": "ok" if connected else "degraded",
            "service": "data-builder-ai",
            "neo4jConnected": connected,
        }

    return app


app = create_app()
