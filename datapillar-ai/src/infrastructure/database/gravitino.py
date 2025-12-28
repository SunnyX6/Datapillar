"""
Gravitino 元数据库连接管理

支持 MySQL、PostgreSQL、H2 等多种后端数据库
"""

import structlog
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.pool import QueuePool

from src.shared.config import settings

logger = structlog.get_logger()


class GravitinoDBClient:
    """Gravitino 数据库连接管理器"""

    _engine: Engine | None = None

    @classmethod
    def _build_db_url(cls) -> str:
        """根据数据库类型构建连接 URL"""
        db_type = settings.gravitino_db_type.lower()
        host = settings.gravitino_db_host
        port = settings.gravitino_db_port
        database = settings.gravitino_db_database
        username = settings.gravitino_db_username
        password = settings.gravitino_db_password

        if db_type == "mysql":
            return (
                f"mysql+pymysql://{username}:{password}"
                f"@{host}:{port}/{database}?charset=utf8mb4"
            )
        elif db_type == "postgresql":
            return (
                f"postgresql+psycopg2://{username}:{password}"
                f"@{host}:{port}/{database}"
            )
        elif db_type == "h2":
            # H2 通常用于测试，使用文件模式
            return f"h2+jaydebeapi:///{database}"
        else:
            raise ValueError(f"不支持的数据库类型: {db_type}")

    @classmethod
    def get_engine(cls) -> Engine:
        """获取 SQLAlchemy Engine"""
        if cls._engine is None:
            db_url = cls._build_db_url()
            try:
                cls._engine = create_engine(
                    db_url,
                    poolclass=QueuePool,
                    pool_size=5,
                    max_overflow=10,
                    pool_pre_ping=True,
                    pool_recycle=3600,
                    pool_timeout=30,
                    echo=False,
                )
                logger.info(
                    "gravitino_db_connected",
                    db_type=settings.gravitino_db_type,
                    host=settings.gravitino_db_host,
                    database=settings.gravitino_db_database,
                )
            except Exception as e:
                logger.error("gravitino_db_connection_failed", error=str(e))
                raise
        return cls._engine

    @classmethod
    def close(cls) -> None:
        """关闭连接池"""
        if cls._engine:
            cls._engine.dispose()
            cls._engine = None
            logger.info("gravitino_db_closed")

    @classmethod
    def execute_query(cls, query: str, params: dict | None = None) -> list[dict]:
        """执行查询并返回结果"""
        engine = cls.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text(query), params or {})
            rows = result.fetchall()
            columns = result.keys()
            return [dict(zip(columns, row)) for row in rows]
