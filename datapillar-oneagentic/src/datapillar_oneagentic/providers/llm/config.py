"""
LLM 配置
"""

from pydantic import BaseModel, Field


class ProviderRateLimitConfig(BaseModel):
    """单个 Provider 的限流配置"""

    rpm: int = Field(default=60, description="每分钟请求数 (Requests Per Minute)")
    max_concurrent: int = Field(default=10, description="最大并发请求数")


class RateLimitConfig(BaseModel):
    """
    限流配置

    基于 OpenAI 的 RPM 理念设计：
    - rpm: 每分钟请求数限制
    - max_concurrent: 最大并发数限制
    """

    enabled: bool = Field(default=True, description="是否启用限流")
    default: ProviderRateLimitConfig = Field(
        default_factory=ProviderRateLimitConfig,
        description="默认限流配置（所有 Provider 通用）",
    )
    providers: dict[str, ProviderRateLimitConfig] = Field(
        default_factory=dict,
        description="按 Provider 覆盖的限流配置",
    )

    def get_provider_config(self, provider: str) -> ProviderRateLimitConfig:
        """获取指定 Provider 的限流配置"""
        provider_lower = provider.lower()
        if provider_lower in self.providers:
            return self.providers[provider_lower]
        return self.default


class RetryConfig(BaseModel):
    """重试配置"""

    max_retries: int = Field(default=3, ge=0, description="最大重试次数")
    initial_delay_ms: int = Field(default=500, gt=0, description="初始重试延迟（毫秒）")
    max_delay_ms: int = Field(default=30000, gt=0, description="最大重试延迟（毫秒）")
    exponential_base: float = Field(default=2.0, gt=1.0, description="指数退避基数")
    jitter: bool = Field(default=True, description="是否启用抖动")


class CircuitBreakerConfig(BaseModel):
    """熔断配置"""

    failure_threshold: int = Field(
        default=5,
        ge=1,
        description="熔断失败阈值（连续失败多少次触发熔断）",
    )
    recovery_seconds: int = Field(
        default=60,
        gt=0,
        description="熔断恢复时间（秒）",
    )


class LLMConfig(BaseModel):
    """
    LLM 配置

    包含：
    - 基础配置（api_key, model, base_url）
    - 调用配置（temperature, timeout_seconds）
    - 弹性配置（retry, circuit_breaker）
    - 限流配置（rate_limit）
    """

    # 基础配置
    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="模型名称")
    base_url: str | None = Field(default=None, description="API Base URL")

    # 调用配置
    temperature: float = Field(default=0.0, description="温度")
    timeout_seconds: float = Field(default=120.0, description="单次调用超时（秒）")

    # 弹性配置
    retry: RetryConfig = Field(default_factory=RetryConfig, description="重试配置")
    circuit_breaker: CircuitBreakerConfig = Field(
        default_factory=CircuitBreakerConfig, description="熔断配置"
    )

    # 限流配置
    rate_limit: RateLimitConfig = Field(
        default_factory=RateLimitConfig, description="限流配置"
    )

    def is_configured(self) -> bool:
        """检查是否已配置"""
        return self.api_key is not None and self.model is not None


class EmbeddingConfig(BaseModel):
    """Embedding 配置"""

    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="模型名称")
    base_url: str | None = Field(default=None, description="API Base URL")
    dimension: int = Field(default=1536, description="向量维度")

    def is_configured(self) -> bool:
        """检查是否已配置"""
        return self.api_key is not None and self.model is not None
