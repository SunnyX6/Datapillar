# @author Sunny
# @date 2026-01-27

"""
MySQL Database connection management

provide MySQL connection pool（Based on SQLAlchemy）
"""

import logging

from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

from src.shared.config.exceptions import MySQLError
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class MySQLClient:
    """MySQL connection pool manager（use SQLAlchemy）"""

    _engine = None

    @classmethod
    def get_engine(cls):
        """Get SQLAlchemy Engine（Global singleton connection pool）"""
        if cls._engine is None:
            db_url = (
                f"mysql+pymysql://{settings.mysql_username}:{settings.mysql_password}"
                f"@{settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}"
                f"?charset=utf8mb4"
            )
            try:
                cls._engine = create_engine(
                    db_url,
                    poolclass=QueuePool,
                    pool_size=10,
                    max_overflow=20,
                    pool_pre_ping=True,
                    pool_recycle=3600,
                    pool_timeout=30,
                    echo=False,
                )
                logger.info(
                    f"MySQL The connection pool has been initialized: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}"
                )
            except Exception as e:
                logger.error(f"MySQL Connection pool initialization failed: {e}")
                raise MySQLError(f"MySQL Connection pool initialization failed: {e}") from e
        return cls._engine

    @classmethod
    def close(cls):
        """Close connection pool"""
        if cls._engine:
            cls._engine.dispose()
            cls._engine = None
            logger.info("MySQL Connection pool is closed")
