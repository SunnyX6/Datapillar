# @author Sunny
# @date 2026-02-20

"""FastAPI 生命周期管理。"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Any

from datapillar_oneagentic import Datapillar
from fastapi import FastAPI

from src.infrastructure.database import AsyncNeo4jClient, MySQLClient, Neo4jClient, RedisClient
from src.infrastructure.database.gravitino import GravitinoDBClient
from src.shared.config.nacos_client import NacosRuntime, bootstrap_nacos

logger = logging.getLogger(__name__)


@dataclass
class RuntimeResources:
    nacos_runtime: NacosRuntime | None = None


def create_lifespan(*, settings: Any):
    """创建应用 lifespan。"""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        resources = RuntimeResources()
        etl_teams: dict[int, Datapillar] = {}

        try:
            await _startup(app=app, settings=settings, resources=resources, etl_teams=etl_teams)
            yield
        finally:
            await _shutdown(resources)

    return lifespan


async def _startup(
    *,
    app: FastAPI,
    settings: Any,
    resources: RuntimeResources,
    etl_teams: dict[int, Datapillar],
) -> None:
    resources.nacos_runtime = await bootstrap_nacos(settings)
    app.state.nacos_runtime = resources.nacos_runtime

    logger.info("=" * 60)
    logger.info("Datapillar AI - 启动中...")
    logger.info("环境: %s", resources.nacos_runtime.config.namespace)
    logger.info("Neo4j URI: %s", settings.neo4j_uri)
    logger.info(
        "MySQL: %s:%s/%s",
        settings.mysql_host,
        settings.mysql_port,
        settings.mysql_database,
    )
    logger.info("=" * 60)

    app.state.etl_teams = etl_teams

    await resources.nacos_runtime.register_service(port=settings.app_port)

    logger.info("初始化 MySQL 连接池...")
    MySQLClient.get_engine()

    logger.info("初始化 Neo4j 连接池...")
    Neo4jClient.get_driver()

    logger.info("初始化 Redis 连接池...")
    await RedisClient.get_instance()
    if not await RedisClient.ping():
        raise RuntimeError("Redis 连接验证失败")
    logger.info("Redis 连接验证通过")

    logger.info("初始化 Gravitino 数据库连接...")
    GravitinoDBClient.get_engine()

    logger.info("FastAPI 应用启动完成")


async def _shutdown(resources: RuntimeResources) -> None:
    logger.info("Datapillar AI - 关闭中...")

    await RedisClient.close()
    await AsyncNeo4jClient.close()
    Neo4jClient.close()
    MySQLClient.close()
    GravitinoDBClient.close()
    logger.info("所有连接池已关闭")

    if resources.nacos_runtime is not None:
        await resources.nacos_runtime.deregister_service()
        await resources.nacos_runtime.shutdown()
