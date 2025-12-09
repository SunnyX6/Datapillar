# -*- coding: utf-8 -*-
"""
FastAPI 应用入口（使用 Repository 模式）
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import logging

from src.config.logging import setup_logging

logger = logging.getLogger(__name__)

from src.agent.orchestrator import Orchestrator, create_orchestrator
from src.config.exceptions import BaseError, AuthenticationError, AuthorizationError
from src.api.router import api_router
from src.config import settings
from src.config.connection import Neo4jClient, AsyncNeo4jClient, RedisClient, MySQLClient
from src.auth.middleware import AuthMiddleware


def create_app() -> FastAPI:
    """创建 FastAPI 应用"""
    setup_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        logger.info("=" * 60)
        logger.info("Datapillar AI - 启动中...")
        logger.info(f"环境: {settings.get('ENV_FOR_DYNACONF', 'development')}")
        logger.info(f"Neo4j URI: {settings.neo4j_uri}")
        logger.info(f"MySQL: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")
        logger.info("=" * 60)

        orchestrator: Orchestrator | None = None
        try:
            # 初始化连接池（全局单例，自动管理）
            logger.info("初始化 MySQL 连接池...")
            MySQLClient.get_engine()  # 触发连接池初始化

            logger.info("初始化 Neo4j 连接池...")
            Neo4jClient.get_driver()  # 触发连接池初始化

            logger.info("初始化 Redis 连接池...")
            redis_client = await RedisClient.get_instance()

            # 创建 Orchestrator
            orchestrator = await create_orchestrator(redis_client)
            app.state.orchestrator = orchestrator

            logger.info("FastAPI 应用启动完成")
            yield

        finally:
            logger.info("Datapillar AI - 关闭中...")
            if orchestrator:
                logger.info("Orchestrator 已释放")

            # 关闭连接池
            await RedisClient.close()
            await AsyncNeo4jClient.close()
            Neo4jClient.close()
            MySQLClient.close()
            logger.info("所有连接池已关闭")

    app = FastAPI(
        title="Datapillar AI",
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
        """健康检查（使用连接池）"""
        neo4j_connected = False
        mysql_connected = False

        # 检查 Neo4j 连接
        try:
            driver = Neo4jClient.get_driver()
            driver.verify_connectivity()
            neo4j_connected = True
        except Exception as e:
            logger.warning(f"Neo4j 健康检查失败: {e}")

        # 检查 MySQL 连接
        try:
            from sqlalchemy import text
            with MySQLClient.get_engine().connect() as conn:
                conn.execute(text("SELECT 1"))
            mysql_connected = True
        except Exception as e:
            logger.warning(f"MySQL 健康检查失败: {e}")

        all_ok = neo4j_connected and mysql_connected

        return {
            "status": "ok" if all_ok else "degraded",
            "service": "datapillar-ai",
            "environment": settings.get("ENV_FOR_DYNACONF", "development"),
            "connections": {
                "neo4j": neo4j_connected,
                "mysql": mysql_connected,
            }
        }

    return app


app = create_app()
