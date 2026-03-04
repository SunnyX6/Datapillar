# @author Sunny
# @date 2026-01-27

"""
Gravitino Metabase connection management

support MySQL,PostgreSQL,H2 and other back-end databases
"""

import logging

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.pool import QueuePool

from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class GravitinoDBClient:
    """Gravitino Database connection manager"""

    _engine: Engine | None = None

    @classmethod
    def _build_db_url(cls) -> str:
        """Build connections based on database type URL"""
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
            return f"postgresql+psycopg2://{username}:{password}" f"@{host}:{port}/{database}"
        elif db_type == "h2":
            # H2 Usually used for testing,Use file mode
            return f"h2+jaydebeapi:///{database}"
        else:
            raise ValueError(f"Unsupported database type:{db_type}")

    @classmethod
    def get_engine(cls) -> Engine:
        """Get SQLAlchemy Engine"""
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
                    extra={
                        "data": {
                            "db_type": settings.gravitino_db_type,
                            "host": settings.gravitino_db_host,
                            "database": settings.gravitino_db_database,
                        }
                    },
                )
            except Exception as e:
                logger.error(
                    "gravitino_db_connection_failed",
                    extra={"data": {"error": str(e)}},
                )
                raise
        return cls._engine

    @classmethod
    def close(cls) -> None:
        """Close connection pool"""
        if cls._engine:
            cls._engine.dispose()
            cls._engine = None
            logger.info("gravitino_db_closed")

    @classmethod
    def execute_query(cls, query: str, params: dict | None = None) -> list[dict]:
        """Execute a query and return results"""
        engine = cls.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text(query), params or {})
            rows = result.fetchall()
            columns = result.keys()
            return [dict(zip(columns, row, strict=False)) for row in rows]
