"""
全局异常类定义
"""

from typing import Any


class BaseError(Exception):
    """基础异常类"""

    def __init__(self, message: str, details: Any | None = None):
        self.message = message
        self.details = details
        super().__init__(self.message)


class ConfigurationError(BaseError):
    """配置错误"""

    pass


class DatabaseError(BaseError):
    """数据库操作错误"""

    pass


class Neo4jError(DatabaseError):
    """Neo4j 错误"""

    pass


class MySQLError(DatabaseError):
    """MySQL 错误"""

    pass


class RedisError(DatabaseError):
    """Redis 错误"""

    pass


class AuthenticationError(BaseError):
    """认证失败"""

    pass


class AuthorizationError(BaseError):
    """授权失败"""

    pass


class ResourceNotFoundError(BaseError):
    """资源未找到"""

    pass


class ValidationError(BaseError):
    """数据校验失败"""

    pass


class AgentExecutionError(BaseError):
    """Agent 执行失败"""

    pass


class LLMError(BaseError):
    """LLM 服务错误"""

    pass
