"""
System 级别 Repository（系统配置/元数据）

定位：
- 存放“系统级表”的数据访问：比如 AI 模型配置、ETL 组件配置等
- 这类 Repo 不属于具体业务模块（openlineage/etl/knowledge），属于平台系统层
"""

from src.infrastructure.repository.system.repository import (
    ComponentRepository,
    LlmUsageRepository,
    ModelRepository,
)

__all__ = [
    "ComponentRepository",
    "LlmUsageRepository",
    "ModelRepository",
]
