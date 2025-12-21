"""
MySQL 数据库连接管理

提供 MySQL 连接池（基于 SQLAlchemy）
"""

import logging

from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

from src.shared.config import settings
from src.shared.config.exceptions import MySQLError

logger = logging.getLogger(__name__)


class MySQLClient:
    """MySQL 连接池管理器（使用 SQLAlchemy）"""

    _engine = None

    @classmethod
    def get_engine(cls):
        """获取 SQLAlchemy Engine（全局单例连接池）"""
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
                logger.info(f"MySQL 连接池已初始化: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")
            except Exception as e:
                logger.error(f"MySQL 连接池初始化失败: {e}")
                raise MySQLError(f"MySQL 连接池初始化失败: {e}")
        return cls._engine

    @classmethod
    def close(cls):
        """关闭连接池"""
        if cls._engine:
            cls._engine.dispose()
            cls._engine = None
            logger.info("MySQL 连接池已关闭")
