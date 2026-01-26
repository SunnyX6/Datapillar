"""
Core module configuration.

Includes ContextConfig and AgentConfig.
"""

from pydantic import BaseModel, Field, field_validator


class ContextConfig(BaseModel):
    """Context configuration."""

    compact_min_keep_entries: int = Field(
        default=5,
        ge=1,
        description="Minimum number of messages to keep during compaction",
    )


class CheckpointerConfig(BaseModel):
    """Checkpointer configuration (state persistence)."""

    type: str = Field(
        default="memory",
        description="Type: memory | sqlite | postgres | redis | redis_shallow",
    )
    path: str | None = Field(
        default=None,
        description="SQLite database path",
    )
    url: str | None = Field(
        default=None,
        description="Database URL (postgres/redis)",
    )
    ttl_minutes: float | None = Field(
        default=None,
        description="Redis TTL in minutes",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"memory", "sqlite", "postgres", "redis", "redis_shallow"}
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported checkpointer type: '{v}'. Supported: {', '.join(sorted(supported))}"
            )
        return v.lower()


class DeliverableStoreConfig(BaseModel):
    """DeliverableStore configuration (agent deliverables storage)."""

    type: str = Field(
        default="memory",
        description="Type: memory | postgres | redis",
    )
    url: str | None = Field(
        default=None,
        description="Database URL (postgres/redis)",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"memory", "postgres", "redis"}
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported deliverable_store type: '{v}'. Supported: {', '.join(sorted(supported))}"
            )
        return v.lower()




class AgentRetryConfig(BaseModel):
    """Agent retry configuration."""

    max_retries: int = Field(default=0, ge=0, description="Maximum retry count")
    initial_delay_ms: int = Field(default=500, gt=0, description="Initial retry delay in ms")
    max_delay_ms: int = Field(default=30000, gt=0, description="Maximum retry delay in ms")
    exponential_base: float = Field(default=2.0, gt=1.0, description="Exponential backoff base")
    jitter: bool = Field(default=True, description="Enable jitter")


class AgentConfig(BaseModel):
    """Agent execution configuration."""

    max_steps: int = Field(
        default=25,
        ge=1,
        description="Max agent steps (1 LLM call + optional tool calls per step)",
    )

    timeout_seconds: float = Field(
        default=300.0,
        gt=0,
        description="Agent execution timeout in seconds",
    )

    tool_timeout_seconds: float = Field(
        default=30.0,
        gt=0,
        description="Tool call timeout in seconds",
    )

    retry: AgentRetryConfig = Field(
        default_factory=AgentRetryConfig,
        description="Agent retry configuration",
    )

    # Storage configuration
    checkpointer: CheckpointerConfig = Field(
        default_factory=CheckpointerConfig,
        description="Checkpointer configuration",
    )

    deliverable_store: DeliverableStoreConfig = Field(
        default_factory=DeliverableStoreConfig,
        description="DeliverableStore configuration",
    )
