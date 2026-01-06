"""
System 级别数据访问（系统配置/元数据）

定位：
- 存放"系统级表"的数据访问：比如 AI 模型配置、ETL 组件配置等
- 这类查询不属于具体业务模块（openlineage/etl/knowledge），属于平台系统层
"""

from src.infrastructure.repository.system.ai_model import LlmUsage, Model
from src.infrastructure.repository.system.component import Component

__all__ = [
    "Model",
    "LlmUsage",
    "Component",
]
