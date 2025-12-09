# -*- coding: utf-8 -*-
"""
配置模块

包含:
- settings: 项目环境配置（使用 dynaconf，支持多环境）
- models: LLM 模型配置管理
- connection: 数据库连接池管理
- exceptions: 全局异常
- logging: 日志配置
"""

from src.config.settings import settings
from src.config.models import model_manager, ModelConfig, ModelManager
from src.config.connection import Neo4jClient, AsyncNeo4jClient, MySQLClient, RedisClient

__all__ = [
    # 环境配置（只导出实例）
    "settings",
    # 模型配置
    "model_manager",
    "ModelConfig",
    "ModelManager",
    # 数据库客户端
    "Neo4jClient",
    "AsyncNeo4jClient",
    "MySQLClient",
    "RedisClient",
]
