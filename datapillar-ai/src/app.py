# -*- coding: utf-8 -*-
"""
FastAPI 应用入口（使用 Repository 模式）
"""

from __future__ import annotations

from contextlib import asynccontextmanager
import gzip

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
import logging

from src.shared.config.logging import setup_logging

logger = logging.getLogger(__name__)

from src.modules.etl.orchestrator import EtlOrchestrator, create_etl_orchestrator
from src.shared.config.exceptions import BaseError, AuthenticationError, AuthorizationError
from src.api.router import api_router
from src.shared.config import settings
from src.infrastructure.database import Neo4jClient, AsyncNeo4jClient, RedisClient, MySQLClient
from src.shared.auth.middleware import AuthMiddleware


class GzipRequestMiddleware:
    """解压 Gzip 请求体的 ASGI 中间件"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        # 检查是否是 gzip 编码
        headers = dict(scope.get("headers", []))
        content_encoding = headers.get(b"content-encoding", b"").decode().lower()

        if content_encoding != "gzip":
            await self.app(scope, receive, send)
            return

        # 收集请求体
        body_parts = []
        while True:
            message = await receive()
            body_parts.append(message.get("body", b""))
            if not message.get("more_body", False):
                break

        # 解压 gzip
        compressed_body = b"".join(body_parts)
        try:
            decompressed_body = gzip.decompress(compressed_body)
        except Exception as e:
            logger.warning(f"Gzip 解压失败: {e}")
            decompressed_body = compressed_body

        # 修改 headers，移除 content-encoding，更新 content-length
        new_headers = [
            (k, v) for k, v in scope["headers"]
            if k.lower() not in (b"content-encoding", b"content-length")
        ]
        new_headers.append((b"content-length", str(len(decompressed_body)).encode()))

        new_scope = {**scope, "headers": new_headers}

        # 创建新的 receive 函数
        body_sent = False

        async def new_receive():
            nonlocal body_sent
            if not body_sent:
                body_sent = True
                return {"type": "http.request", "body": decompressed_body, "more_body": False}
            return {"type": "http.disconnect"}

        await self.app(new_scope, new_receive, send)


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
            await RedisClient.get_instance()

            # 创建 Orchestrator（使用内存 checkpoint）
            orchestrator = await create_etl_orchestrator()
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

    # Gzip 请求解压中间件
    app.add_middleware(GzipRequestMiddleware)

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
    app.include_router(api_router, prefix="/api")

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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "src.app:app",
        host=settings.app_host,
        port=settings.app_port,
        reload=settings.debug,
    )
