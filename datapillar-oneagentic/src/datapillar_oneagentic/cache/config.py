"""
缓存配置
"""

from pydantic import BaseModel, Field


class CacheConfig(BaseModel):
    """缓存配置"""

    enabled: bool = Field(
        default=True,
        description="是否启用 LLM 响应缓存",
    )

    ttl_seconds: int = Field(
        default=300,
        gt=0,
        description="缓存 TTL（秒）",
    )

    key_prefix: str = Field(
        default="llm_cache:",
        description="缓存键前缀",
    )

    checkpoint_ttl_seconds: int = Field(
        default=60 * 60 * 24 * 7,
        gt=0,
        description="Checkpoint TTL（默认 7 天）",
    )
