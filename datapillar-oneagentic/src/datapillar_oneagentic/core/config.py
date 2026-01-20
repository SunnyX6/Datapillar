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




class AgentRetryConfig(BaseModel):
    """Agent 重试配置"""

    max_retries: int = Field(default=0, ge=0, description="最大重试次数")
    initial_delay_ms: int = Field(default=500, gt=0, description="初始重试延迟（毫秒）")
    max_delay_ms: int = Field(default=30000, gt=0, description="最大重试延迟（毫秒）")
    exponential_base: float = Field(default=2.0, gt=1.0, description="指数退避基数")
    jitter: bool = Field(default=True, description="是否启用抖动")


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

    retry: AgentRetryConfig = Field(
        default_factory=AgentRetryConfig,
        description="Agent 重试配置",
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
