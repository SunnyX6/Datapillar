"""
配置管理 - 环境变量 + AI模型配置
合并了原app/config/settings.py和app/config/model_manager.py
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional, Dict, Any
from sqlalchemy import create_engine, text
import logging

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    """应用配置"""

    # FastAPI配置
    app_name: str = "Data Builder AI"
    app_version: str = "0.1.0"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    debug: bool = False

    # Neo4j配置
    neo4j_uri: str
    neo4j_username: str
    neo4j_password: str
    neo4j_database: str = "neo4j"

    # MySQL配置（业务数据库）
    mysql_host: str
    mysql_port: int
    mysql_database: str
    mysql_username: str
    mysql_password: str

    # Redis配置（Checkpoint/缓存）
    redis_host: str = "127.0.0.1"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: Optional[str] = None
    redis_checkpoint_ttl_seconds: int = 7 * 24 * 3600  # 默认7天

    # JWT认证配置（与 data-builder-auth 保持一致）
    jwt_secret: str
    jwt_issuer: str

    # 注意：AI模型配置已迁移到MySQL的ai_model表中

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
    )


# 全局配置实例
settings = Settings()


# ==================== AI模型配置管理 ====================

class ModelConfig:
    """模型配置"""

    def __init__(self, row: Dict[str, Any]):
        self.id = row["id"]
        self.name = row["name"]
        self.provider = row["provider"]
        self.model_name = row["model_name"]
        self.model_type = row["model_type"]
        self.api_key = row["api_key"]
        self.base_url = row["base_url"]
        self.is_enabled = bool(row["is_enabled"])
        self.is_default = bool(row["is_default"])
        self.config_json = row.get("config_json")
        # Embedding模型专用属性
        self.embedding_dimension = row.get("embedding_dimension")

    def __repr__(self):
        return f"<ModelConfig {self.provider}/{self.model_name} ({self.model_type})>"


class ModelManager:
    """AI模型配置管理器"""

    _instance = None
    _engine = None

    def __new__(cls):
        """单例模式"""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        if self._engine is None:
            db_url = (
                f"mysql+pymysql://{settings.mysql_username}:{settings.mysql_password}"
                f"@{settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}"
                f"?charset=utf8mb4"
            )
            try:
                self._engine = create_engine(
                    db_url,
                    pool_pre_ping=True,  # 连接池预检查
                    pool_recycle=3600,  # 1小时回收连接
                )
                logger.info(
                    f"ModelManager已连接MySQL: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}"
                )
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"无法连接MySQL，ModelManager降级为不可用: {exc}")
                self._engine = None

    def get_default_chat_model(self) -> Optional[ModelConfig]:
        """获取默认的Chat模型"""
        if not self._ensure_engine():
            return None
        query = text("""
            SELECT * FROM ai_model
            WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'chat'
            LIMIT 1
        """)

        result = self._execute(query)
        if result:
            row = result.mappings().fetchone()
            if row:
                return ModelConfig(dict(row))
        logger.warning("未找到默认的Chat模型")
        return None

    def get_default_embedding_model(self) -> Optional[ModelConfig]:
        """获取默认的Embedding模型"""
        if not self._ensure_engine():
            return None
        query = text("""
            SELECT * FROM ai_model
            WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'embedding'
            LIMIT 1
        """)

        result = self._execute(query)
        if result:
            row = result.mappings().fetchone()
            if row:
                return ModelConfig(dict(row))
        logger.warning("未找到默认的Embedding模型")
        return None

    def get_model_by_id(self, model_id: int) -> Optional[ModelConfig]:
        """根据ID获取模型配置"""
        if not self._ensure_engine():
            return None
        query = text("""
            SELECT * FROM ai_model
            WHERE id = :id AND is_enabled = 1
        """)

        result = self._execute(query, {"id": model_id})
        if result:
            row = result.mappings().fetchone()
            if row:
                return ModelConfig(dict(row))
        return None

    def list_enabled_models(self, model_type: Optional[str] = None):
        """列出所有启用的模型"""
        if not self._ensure_engine():
            return []
        if model_type:
            query = text("""
                SELECT * FROM ai_model
                WHERE is_enabled = 1 AND model_type = :model_type
                ORDER BY is_default DESC, created_at DESC
            """)
            params = {"model_type": model_type}
        else:
            query = text("""
                SELECT * FROM ai_model
                WHERE is_enabled = 1
                ORDER BY model_type, is_default DESC, created_at DESC
            """)
            params = {}

        result = self._execute(query, params)
        if not result:
            return []
        rows = result.mappings().fetchall()
        return [ModelConfig(dict(row)) for row in rows]

    def get_embedding_dimension(self) -> int:
        """获取当前默认Embedding模型的向量维度"""
        if not self._ensure_engine():
            return 2048
        model = self.get_default_embedding_model()
        if model and model.embedding_dimension:
            return model.embedding_dimension
        # 默认返回2048（智谱embedding-3）
        logger.warning("未找到Embedding模型维度配置，使用默认值2048")
        return 2048

    def _ensure_engine(self) -> bool:
        if self._engine is None:
            logger.warning("ModelManager未连接MySQL，跳过模型配置")
            return False
        return True

    def _execute(self, query, params=None):
        if self._engine is None:
            return None
        try:
            with self._engine.connect() as conn:
                return conn.execute(query, params or {})
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"执行MySQL查询失败: {exc}")
            return None


# 全局单例
model_manager = ModelManager()
