# @author Sunny
# @date 2026-02-20

"""FastAPI life cycle management."""

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
    """Create app lifespan."""

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
    logger.info("Datapillar AI - Starting...")
    logger.info("environment:%s", resources.nacos_runtime.config.namespace)
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

    logger.info("initialization MySQL connection pool...")
    MySQLClient.get_engine()

    logger.info("initialization Neo4j connection pool...")
    Neo4jClient.get_driver()

    logger.info("initialization Redis connection pool...")
    await RedisClient.get_instance()
    if not await RedisClient.ping():
        raise RuntimeError("Redis Connection verification failed")
    logger.info("Redis Connection verification passed")

    logger.info("initialization Gravitino Database connection...")
    GravitinoDBClient.get_engine()

    logger.info("FastAPI Application startup completed")


async def _shutdown(resources: RuntimeResources) -> None:
    logger.info("Datapillar AI - Closed...")

    await RedisClient.close()
    await AsyncNeo4jClient.close()
    Neo4jClient.close()
    MySQLClient.close()
    GravitinoDBClient.close()
    logger.info("All connection pools are closed")

    if resources.nacos_runtime is not None:
        await resources.nacos_runtime.deregister_service()
        await resources.nacos_runtime.shutdown()
