# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
FastAPI 应用入口（使用 Repository 模式）
"""

from __future__ import annotations

import gzip
import logging
from contextlib import asynccontextmanager

from datapillar_oneagentic import Datapillar
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from src.shared.config.logging import setup_logging

logger = logging.getLogger(__name__)

from src.api.router import api_router
from src.infrastructure.database import AsyncNeo4jClient, MySQLClient, Neo4jClient, RedisClient
from src.infrastructure.database.gravitino import GravitinoDBClient
from src.modules.knowledge.embedding_processor import (
    get_embedding_processor as get_knowledge_embedding_processor,
)
from src.modules.openlineage.core.embedding_processor import get_embedding_processor
from src.modules.openlineage.core.event_processor import get_event_processor
from src.modules.openlineage.core.sql_summary_processor import get_sql_summary_processor
from src.shared.auth.middleware import AuthMiddleware
from src.shared.config import settings
from src.shared.config.exceptions import AuthenticationError, AuthorizationError, BaseError
from src.shared.config.nacos_client import NacosRuntime, bootstrap_nacos
from src.shared.web import ApiResponse


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
            (k, v)
            for k, v in scope["headers"]
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
        nacos_runtime: NacosRuntime | None = None
        event_processor = None
        embedding_processor = None
        knowledge_embedding_processor = None
        sql_summary_processor = None

        nacos_runtime = await bootstrap_nacos(settings)
        app.state.nacos_runtime = nacos_runtime

        logger.info("=" * 60)
        logger.info("Datapillar AI - 启动中...")
        logger.info(f"环境: {nacos_runtime.config.namespace}")
        logger.info(f"Neo4j URI: {settings.neo4j_uri}")
        logger.info(f"MySQL: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")
        logger.info("=" * 60)

        etl_teams: dict[int, Datapillar] = {}
        try:
            # 运行期依赖（必须在 Nacos 配置注入后初始化）
            event_processor = get_event_processor()
            embedding_processor = get_embedding_processor()
            knowledge_embedding_processor = get_knowledge_embedding_processor()
            sql_summary_processor = get_sql_summary_processor()

            app.state.event_processor = event_processor
            app.state.embedding_processor = embedding_processor
            app.state.knowledge_embedding_processor = knowledge_embedding_processor
            app.state.sql_summary_processor = sql_summary_processor

            await nacos_runtime.register_service(port=settings.app_port)
            # 初始化连接池（全局单例，自动管理）
            logger.info("初始化 MySQL 连接池...")
            MySQLClient.get_engine()  # 触发连接池初始化

            logger.info("初始化 Neo4j 连接池...")
            Neo4jClient.get_driver()  # 触发连接池初始化

            logger.info("初始化 Redis 连接池...")
            await RedisClient.get_instance()
            if not await RedisClient.ping():
                raise RuntimeError("Redis 连接验证失败")
            logger.info("Redis 连接验证通过")

            logger.info("初始化 Gravitino 数据库连接...")
            GravitinoDBClient.get_engine()  # 触发连接池初始化

            # 启动 EventProcessor
            logger.info("启动 EventProcessor...")
            await event_processor.start(paused=False)

            # 启动 EmbeddingProcessor
            logger.info("启动 EmbeddingProcessor...")
            await embedding_processor.start()

            # 启动 Knowledge EmbeddingProcessor
            logger.info("启动 Knowledge EmbeddingProcessor...")
            await knowledge_embedding_processor.start()

            # 启动 SQLSummaryProcessor
            logger.info("启动 SQLSummaryProcessor...")
            await sql_summary_processor.start()

            # ETL 智能团队按租户懒加载
            app.state.etl_teams = etl_teams

            logger.info("FastAPI 应用启动完成")
            yield

        finally:
            logger.info("Datapillar AI - 关闭中...")

            # 停止处理器
            logger.info("停止 EventProcessor...")
            if event_processor is not None:
                await event_processor.stop()

            logger.info("停止 EmbeddingProcessor...")
            if embedding_processor is not None:
                await embedding_processor.stop()

            logger.info("停止 Knowledge EmbeddingProcessor...")
            if knowledge_embedding_processor is not None:
                await knowledge_embedding_processor.stop()

            logger.info("停止 SQLSummaryProcessor...")
            if sql_summary_processor is not None:
                await sql_summary_processor.stop()

            # 关闭连接池
            await RedisClient.close()
            await AsyncNeo4jClient.close()
            Neo4jClient.close()
            MySQLClient.close()
            GravitinoDBClient.close()
            logger.info("所有连接池已关闭")
            if nacos_runtime is not None:
                await nacos_runtime.deregister_service()
                await nacos_runtime.shutdown()

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
        return JSONResponse(
            status_code=401,
            content=ApiResponse.error(
                request=request,
                status=401,
                code="UNAUTHORIZED",
                message=exc.message,
            ),
        )

    @app.exception_handler(AuthorizationError)
    async def authz_error_handler(request: Request, exc: AuthorizationError):
        return JSONResponse(
            status_code=403,
            content=ApiResponse.error(
                request=request,
                status=403,
                code="FORBIDDEN",
                message=exc.message,
            ),
        )

    @app.exception_handler(BaseError)
    async def base_error_handler(request: Request, exc: BaseError):
        logger.error(f"业务异常: {exc.message}", exc_info=True)
        return JSONResponse(
            status_code=500,
            content=ApiResponse.error(
                request=request,
                status=500,
                code="INTERNAL_ERROR",
                message=exc.message,
            ),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=422,
            content=ApiResponse.error(
                request=request,
                status=422,
                code="VALIDATION_ERROR",
                message="请求参数校验失败",
            ),
        )

    @app.exception_handler(HTTPException)
    async def http_error_handler(request: Request, exc: HTTPException):
        status_code = exc.status_code
        if status_code == 400:
            code = "INVALID_ARGUMENT"
        elif status_code == 401:
            code = "UNAUTHORIZED"
        elif status_code == 403:
            code = "FORBIDDEN"
        elif status_code == 404:
            code = "RESOURCE_NOT_FOUND"
        elif status_code == 409:
            code = "DUPLICATE_RESOURCE"
        elif status_code == 422:
            code = "VALIDATION_ERROR"
        elif status_code == 503:
            code = "SERVICE_UNAVAILABLE"
        else:
            code = "INTERNAL_ERROR"
        message = str(exc.detail) if exc.detail else "请求失败"
        return JSONResponse(
            status_code=status_code,
            content=ApiResponse.error(
                request=request,
                status=status_code,
                code=code,
                message=message,
            ),
        )

    @app.exception_handler(Exception)
    async def global_error_handler(request: Request, exc: Exception):
        logger.exception(f"未捕获异常: {exc}")
        return JSONResponse(
            status_code=500,
            content=ApiResponse.error(
                request=request,
                status=500,
                code="INTERNAL_ERROR",
                message="服务器内部错误",
            ),
        )

    # 注册 API 路由
    app.include_router(api_router, prefix="/api")

    # 健康检查
    @app.get("/health")
    async def health_check(request: Request):
        """健康检查（使用连接池）"""
        neo4j_connected = False
        mysql_connected = False
        redis_connected = False

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

        # 检查 Redis 连接
        try:
            redis_connected = await RedisClient.ping()
        except Exception as e:
            logger.warning(f"Redis 健康检查失败: {e}")

        all_ok = neo4j_connected and mysql_connected and redis_connected
        nacos_runtime: NacosRuntime | None = getattr(request.app.state, "nacos_runtime", None)
        environment = nacos_runtime.config.namespace if nacos_runtime else "unknown"

        return {
            "status": "ok" if all_ok else "degraded",
            "service": "datapillar-ai",
            "environment": environment,
            "connections": {
                "neo4j": neo4j_connected,
                "mysql": mysql_connected,
                "redis": redis_connected,
            },
        }

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    host = "0.0.0.0"
    port = 7003
    reload_enabled = False

    uvicorn.run(
        "src.app:app",
        host=host,
        port=port,
        reload=reload_enabled,
    )
