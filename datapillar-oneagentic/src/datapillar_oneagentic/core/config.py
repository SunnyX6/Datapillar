"""
Core 模块配置

包含 ContextConfig 和 AgentConfig。
"""

from pydantic import BaseModel, Field, field_validator


class ContextConfig(BaseModel):
    """上下文配置"""

    compact_min_keep_entries: int = Field(
        default=5,
        ge=1,
        description="压缩时最少保留的对话条目数",
    )


class CheckpointerConfig(BaseModel):
    """Checkpointer 配置（状态持久化）"""

    type: str = Field(
        default="memory",
        description="类型: memory | sqlite | postgres | redis",
    )
    path: str | None = Field(
        default=None,
        description="SQLite 数据库路径",
    )
    url: str | None = Field(
        default=None,
        description="数据库连接 URL（postgres/redis）",
    )
    ttl_minutes: float | None = Field(
        default=None,
        description="Redis TTL（分钟）",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"memory", "sqlite", "postgres", "redis"}
        if v.lower() not in supported:
            raise ValueError(f"不支持的 checkpointer 类型: '{v}'。支持: {', '.join(sorted(supported))}")
        return v.lower()


class DeliverableStoreConfig(BaseModel):
    """DeliverableStore 配置（Agent 交付物存储）"""

    type: str = Field(
        default="memory",
        description="类型: memory | postgres | redis",
    )
    url: str | None = Field(
        default=None,
        description="数据库连接 URL（postgres/redis）",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"memory", "postgres", "redis"}
        if v.lower() not in supported:
            raise ValueError(f"不支持的 deliverable_store 类型: '{v}'。支持: {', '.join(sorted(supported))}")
        return v.lower()


class LearningStoreConfig(BaseModel):
    """LearningStore 配置（经验学习向量数据库）"""

    type: str = Field(
        default="lance",
        description="类型: lance | chroma | milvus",
    )
    path: str | None = Field(
        default=None,
        description="本地存储路径（lance/chroma）",
    )
    uri: str | None = Field(
        default=None,
        description="Milvus 连接 URI",
    )
    host: str | None = Field(
        default=None,
        description="Chroma 远程服务器地址",
    )
    port: int = Field(
        default=8000,
        description="Chroma 远程服务器端口",
    )
    token: str | None = Field(
        default=None,
        description="Milvus 认证令牌",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"lance", "chroma", "milvus"}
        if v.lower() not in supported:
            raise ValueError(f"不支持的 learning_store 类型: '{v}'。支持: {', '.join(sorted(supported))}")
        return v.lower()


class AgentConfig(BaseModel):
    """Agent 执行配置"""

    max_steps: int = Field(
        default=25,
        ge=1,
        description="Agent 最大执行步数（每步 = 1 次 LLM 调用 + 可能的工具调用）",
    )

    timeout_seconds: float = Field(
        default=300.0,
        gt=0,
        description="Agent 单次执行超时（秒）",
    )

    tool_timeout_seconds: float = Field(
        default=30.0,
        gt=0,
        description="工具单次调用超时（秒）",
    )

    checkpoint_ttl_seconds: int = Field(
        default=60 * 60 * 24 * 7,
        gt=0,
        description="Checkpoint TTL（默认 7 天）",
    )

    # 存储配置
    checkpointer: CheckpointerConfig = Field(
        default_factory=CheckpointerConfig,
        description="Checkpointer 配置",
    )

    deliverable_store: DeliverableStoreConfig = Field(
        default_factory=DeliverableStoreConfig,
        description="DeliverableStore 配置",
    )

    learning_store: LearningStoreConfig = Field(
        default_factory=LearningStoreConfig,
        description="LearningStore 配置",
    )
