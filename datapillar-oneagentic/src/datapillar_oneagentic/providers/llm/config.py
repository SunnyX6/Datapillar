# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""LLM configuration."""

from enum import Enum

from pydantic import BaseModel, Field, field_validator


class Provider(str, Enum):
    """
    Supported LLM providers.

    Built-in providers:
    - OPENAI: OpenAI API (GPT series)
    - ANTHROPIC: Anthropic API (Claude series)
    - GLM: Zhipu AI (GLM series)
    - DEEPSEEK: DeepSeek API
    - OPENROUTER: OpenRouter multi-model gateway
    - OLLAMA: Ollama local models
    """

    OPENAI = "openai"
    ANTHROPIC = "anthropic"
    GLM = "glm"
    DEEPSEEK = "deepseek"
    OPENROUTER = "openrouter"
    OLLAMA = "ollama"

    @classmethod
    def list_supported(cls) -> list[str]:
        """List supported providers."""
        return [p.value for p in cls]


class ProviderRateLimitConfig(BaseModel):
    """Rate limit config for a provider."""

    rpm: int = Field(default=60, description="Requests per minute")
    max_concurrent: int = Field(default=10, description="Maximum concurrent requests")


class RateLimitConfig(BaseModel):
    """
    Rate limit configuration.

    Based on OpenAI's RPM concept:
    - rpm: requests per minute
    - max_concurrent: maximum concurrency
    """

    enabled: bool = Field(default=True, description="Enable rate limiting")
    default: ProviderRateLimitConfig = Field(
        default_factory=ProviderRateLimitConfig,
        description="Default rate limit config (all providers)",
    )
    providers: dict[str, ProviderRateLimitConfig] = Field(
        default_factory=dict,
        description="Provider-specific overrides",
    )

    def get_provider_config(self, provider: str) -> ProviderRateLimitConfig:
        """Get rate limit config for a provider."""
        provider_lower = provider.lower()
        if provider_lower in self.providers:
            return self.providers[provider_lower]
        return self.default


class RetryConfig(BaseModel):
    """Retry configuration."""

    max_retries: int = Field(default=3, ge=0, description="Maximum retry count")
    initial_delay_ms: int = Field(default=500, gt=0, description="Initial retry delay in ms")
    max_delay_ms: int = Field(default=30000, gt=0, description="Maximum retry delay in ms")
    exponential_base: float = Field(default=2.0, gt=1.0, description="Exponential backoff base")
    jitter: bool = Field(default=True, description="Enable jitter")


class CircuitBreakerConfig(BaseModel):
    """Circuit breaker configuration."""

    failure_threshold: int = Field(
        default=5,
        ge=1,
        description="Failure threshold to trip the breaker",
    )
    recovery_seconds: int = Field(
        default=60,
        gt=0,
        description="Recovery time in seconds",
    )


class CacheBackend(str, Enum):
    """Cache backend type."""

    MEMORY = "memory"
    REDIS = "redis"


class LLMCacheConfig(BaseModel):
    """
    LLM cache configuration.

    Supported backends:
    - memory: in-memory cache (default, single process)
    - redis: Redis cache (distributed)
    """

    enabled: bool = Field(default=True, description="Enable LLM response cache")
    backend: str = Field(
        default=CacheBackend.MEMORY.value,
        description="Cache backend: memory or redis"
    )
    ttl_seconds: int = Field(default=300, gt=0, description="Cache TTL in seconds")
    max_size: int = Field(default=1000, gt=0, description="Max in-memory cache entries")

    # Redis-specific configuration
    redis_url: str | None = Field(default=None, description="Redis URL (required for redis backend)")
    key_prefix: str = Field(default="llm_cache:", description="Redis key prefix")

    @field_validator("backend")
    @classmethod
    def validate_backend(cls, v: str) -> str:
        """Validate cache backend."""
        supported = [b.value for b in CacheBackend]
        if v.lower() not in supported:
            raise ValueError(f"Unsupported cache backend: '{v}'. Supported: {', '.join(supported)}")
        return v.lower()


class LLMConfig(BaseModel):
    """
    LLM configuration.

    Includes:
    - Base config (api_key, model, base_url, provider)
    - Call config (temperature, timeout_seconds)
    - Thinking mode (enable_thinking)
    - Resilience (retry, circuit_breaker)
    - Rate limiting (rate_limit)
    - Cache (cache)
    """

    # Base configuration
    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="Model name")
    base_url: str | None = Field(default=None, description="API Base URL")
    provider: str = Field(
        default=Provider.OPENAI.value,
        description=f"LLM provider, supported: {', '.join(Provider.list_supported())}"
    )

    # Call configuration
    temperature: float = Field(default=0.0, description="Temperature")
    timeout_seconds: float = Field(default=120.0, description="Per-call timeout in seconds")

    # Thinking mode configuration
    enable_thinking: bool = Field(
        default=False,
        description="Enable thinking mode (GLM/DeepSeek/Claude supported)"
    )
    thinking_budget_tokens: int | None = Field(
        default=None,
        description="Thinking token budget (Claude only, unlimited by default)"
    )

    # Resilience configuration
    retry: RetryConfig = Field(default_factory=RetryConfig, description="Retry configuration")
    circuit_breaker: CircuitBreakerConfig = Field(
        default_factory=CircuitBreakerConfig, description="Circuit breaker configuration"
    )

    # Rate limiting
    rate_limit: RateLimitConfig = Field(
        default_factory=RateLimitConfig, description="Rate limit configuration"
    )

    # Cache configuration
    cache: LLMCacheConfig = Field(
        default_factory=LLMCacheConfig, description="LLM response cache configuration"
    )

    @field_validator("provider")
    @classmethod
    def validate_provider(cls, v: str) -> str:
        """Validate provider support."""
        supported = Provider.list_supported()
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported provider: '{v}'. "
                f"Supported: {', '.join(supported)}"
            )
        return v.lower()

    def is_configured(self) -> bool:
        """Return True if configured."""
        return self.api_key is not None and self.model is not None


class EmbeddingBackend(str, Enum):
    """
    Supported embedding providers.

    Built-in providers:
    - OPENAI: OpenAI Embeddings (text-embedding-3-small/large)
    - GLM: Zhipu AI Embeddings (embedding-3)
    """

    OPENAI = "openai"
    GLM = "glm"

    @classmethod
    def list_supported(cls) -> list[str]:
        """List supported embedding providers."""
        return [p.value for p in cls]


class EmbeddingConfig(BaseModel):
    """
    Embedding configuration.

    Includes:
    - provider: provider (openai, glm)
    - api_key: API Key
    - model: model name
    - base_url: custom endpoint (optional)
    - dimension: vector dimension
    """

    provider: str = Field(
        default=EmbeddingBackend.OPENAI.value,
        description=f"Embedding provider, supported: {', '.join(EmbeddingBackend.list_supported())}"
    )
    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="Model name")
    base_url: str | None = Field(default=None, description="API Base URL")
    dimension: int = Field(default=1536, description="Vector dimension")

    @field_validator("provider")
    @classmethod
    def validate_provider(cls, v: str) -> str:
        """Validate embedding provider support."""
        supported = EmbeddingBackend.list_supported()
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported embedding provider: '{v}'. "
                f"Supported: {', '.join(supported)}"
            )
        return v.lower()

    def is_configured(self) -> bool:
        """Return True if configured."""
        return self.api_key is not None and self.model is not None
