"""
共享配置模块

包含:
- settings: 项目环境配置
- exceptions: 全局异常
- logging: 日志配置
"""

from __future__ import annotations

from typing import TYPE_CHECKING

# 注意：必须显式导出 settings 对象，避免 `from src.shared.config import settings`
# 被 Python 解析为“导入子模块 src.shared.config.settings”而得到 module 对象。
# settings 模块本身不依赖 repository/mysql 等重模块，因此这里的导入不会引入循环依赖。
from src.shared.config.settings import settings

__all__ = [
    "settings",
    "Neo4jError",
    "MySQLError",
    "RedisError",
    "LLMError",
]

if TYPE_CHECKING:
    from src.shared.config.exceptions import (
        LLMError as LLMError,
    )
    from src.shared.config.exceptions import (
        MySQLError as MySQLError,
    )
    from src.shared.config.exceptions import (
        Neo4jError as Neo4jError,
    )
    from src.shared.config.exceptions import (
        RedisError as RedisError,
    )


def __getattr__(name: str):
    """
    延迟导入（避免导入阶段触发重依赖/循环依赖）。
    """
    if name == "settings":
        from src.shared.config.settings import settings

        return settings
    if name in {"Neo4jError", "MySQLError", "RedisError", "LLMError"}:
        from src.shared.config.exceptions import LLMError, MySQLError, Neo4jError, RedisError

        return {
            "Neo4jError": Neo4jError,
            "MySQLError": MySQLError,
            "RedisError": RedisError,
            "LLMError": LLMError,
        }[name]
    raise AttributeError(name)
