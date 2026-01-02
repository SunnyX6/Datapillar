"""
共享配置模块

包含:
- settings: 项目环境配置
- models: LLM 模型配置管理
- exceptions: 全局异常
- logging: 日志配置
"""

from __future__ import annotations

# 注意：必须显式导出 settings 对象，避免 `from src.shared.config import settings`
# 被 Python 解析为“导入子模块 src.shared.config.settings”而得到 module 对象。
# settings 模块本身不依赖 repository/mysql 等重模块，因此这里的导入不会引入循环依赖。
from src.shared.config.settings import settings

__all__ = [
    "settings",
    "model_manager",
    "ModelConfig",
    "ModelManager",
    "Neo4jError",
    "MySQLError",
    "RedisError",
    "LLMError",
]


def __getattr__(name: str):
    """
    延迟导入（避免 settings/models/repository 之间的循环依赖）。

    关键点：
    - 任何 import `src.shared.config.settings` 都会先执行本文件
    - 因此禁止在 import 阶段加载 `models`（它会反向依赖 repository/mysql/settings）
    """
    if name == "settings":
        from src.shared.config.settings import settings

        return settings
    if name in {"model_manager", "ModelConfig", "ModelManager"}:
        from src.shared.config.models import model_manager, ModelConfig, ModelManager

        return {
            "model_manager": model_manager,
            "ModelConfig": ModelConfig,
            "ModelManager": ModelManager,
        }[name]
    if name in {"Neo4jError", "MySQLError", "RedisError", "LLMError"}:
        from src.shared.config.exceptions import Neo4jError, MySQLError, RedisError, LLMError

        return {
            "Neo4jError": Neo4jError,
            "MySQLError": MySQLError,
            "RedisError": RedisError,
            "LLMError": LLMError,
        }[name]
    raise AttributeError(name)
