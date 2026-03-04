# @author Sunny
# @date 2026-01-27

"""
Global exception class definition
"""

from typing import Any


class BaseError(Exception):
    """Basic exception class"""

    def __init__(self, message: str, details: Any | None = None):
        self.message = message
        self.details = details
        super().__init__(self.message)


class ConfigurationError(BaseError):
    """Configuration error"""

    pass


class DatabaseError(BaseError):
    """Database operation error"""

    pass


class Neo4jError(DatabaseError):
    """Neo4j Error"""

    pass


class MySQLError(DatabaseError):
    """MySQL Error"""

    pass


class RedisError(DatabaseError):
    """Redis Error"""

    pass


class AuthenticationError(BaseError):
    """Authentication failed"""

    pass


class AuthorizationError(BaseError):
    """Authorization failed"""

    pass


class ResourceNotFoundError(BaseError):
    """Resource not found"""

    pass


class ValidationError(BaseError):
    """Data verification failed"""

    pass


class AgentExecutionError(BaseError):
    """Agent Execution failed"""

    pass


class LLMError(BaseError):
    """LLM Service error"""

    pass
