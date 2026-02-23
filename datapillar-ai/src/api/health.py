# @author Sunny
# @date 2026-02-20

"""健康检查路由。"""

from __future__ import annotations

import logging

from fastapi import APIRouter, Request
from sqlalchemy import text

from src.infrastructure.database import MySQLClient, Neo4jClient, RedisClient
from src.shared.config.nacos_client import NacosRuntime

logger = logging.getLogger(__name__)

health_router = APIRouter()


@health_router.get("/health")
async def health_check(request: Request) -> dict[str, object]:
    """健康检查（使用连接池）。"""
    neo4j_connected = False
    mysql_connected = False
    redis_connected = False

    try:
        driver = Neo4jClient.get_driver()
        driver.verify_connectivity()
        neo4j_connected = True
    except Exception as exc:
        logger.warning("Neo4j 健康检查失败: %s", exc)

    try:
        with MySQLClient.get_engine().connect() as conn:
            conn.execute(text("SELECT 1"))
        mysql_connected = True
    except Exception as exc:
        logger.warning("MySQL 健康检查失败: %s", exc)

    try:
        redis_connected = await RedisClient.ping()
    except Exception as exc:
        logger.warning("Redis 健康检查失败: %s", exc)

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
