# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage Sink 配置模型

Sink 端负责接收事件并写入 Neo4j
- queue: Sink 端二次保护机制（缓冲队列）
- neo4j: Neo4j 写入配置

retry、rate_limit、filter 等配置由 Producer 端（Flink/Spark/Airflow）在 openlineage.yml 中配置
"""

from typing import Any

from pydantic import BaseModel, Field


class QueueConfig(BaseModel):
    """队列配置 - Sink 端二次保护"""

    max_size: int = Field(default=10000, ge=100, le=1000000, description="最大队列大小")
    batch_size: int = Field(default=100, ge=1, le=1000, description="批量处理大小")
    flush_interval_seconds: float = Field(
        default=5.0, ge=0.1, le=60.0, description="刷新间隔（秒）"
    )


class Neo4jConfig(BaseModel):
    """Neo4j 写入配置"""

    batch_size: int = Field(default=50, ge=1, le=500, description="批量写入大小")
    max_concurrent: int = Field(default=10, ge=1, le=50, description="最大并发写入数")


class OpenLineageSinkConfig(BaseModel):
    """
    OpenLineage Sink 配置

    Sink 端负责接收事件并写入 Neo4j
    """

    graceful_shutdown_timeout: float = Field(
        default=30.0, ge=5.0, le=120.0, description="优雅关闭超时"
    )
    queue: QueueConfig = Field(default_factory=QueueConfig)
    neo4j: Neo4jConfig = Field(default_factory=Neo4jConfig)

    @classmethod
    def from_settings(cls, settings: Any) -> "OpenLineageSinkConfig":
        """从 Dynaconf settings 创建配置"""
        sink_config = getattr(settings, "openlineage_sink", {})
        if not sink_config:
            return cls()

        return cls(
            graceful_shutdown_timeout=sink_config.get("graceful_shutdown_timeout", 30.0),
            queue=QueueConfig(**sink_config.get("queue", {})),
            neo4j=Neo4jConfig(**sink_config.get("neo4j", {})),
        )
