# @author Sunny
# @date 2026-01-27

"""
Shared configuration module

contains:- settings:Project environment configuration
- exceptions:global exception
- logging:Log configuration
"""

from __future__ import annotations

from typing import TYPE_CHECKING

# Note: Explicitly export the settings object. Otherwise
# `from src.shared.config import settings` may resolve to the submodule object.
from src.shared.config.settings import settings as settings

__all__ = ["settings", "Neo4jError", "MySQLError", "RedisError", "LLMError"]

if TYPE_CHECKING:
    from src.shared.config.exceptions import LLMError as LLMError
    from src.shared.config.exceptions import MySQLError as MySQLError
    from src.shared.config.exceptions import Neo4jError as Neo4jError
    from src.shared.config.exceptions import RedisError as RedisError


def __getattr__(name: str):
    """Lazy import optional symbols to avoid heavy import chains."""
    if name in {"Neo4jError", "MySQLError", "RedisError", "LLMError"}:
        from src.shared.config.exceptions import LLMError, MySQLError, Neo4jError, RedisError

        return {
            "Neo4jError": Neo4jError,
            "MySQLError": MySQLError,
            "RedisError": RedisError,
            "LLMError": LLMError,
        }[name]

    raise AttributeError(name)
