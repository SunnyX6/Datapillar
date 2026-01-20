"""
LLM 配置
"""

from enum import Enum

from pydantic import BaseModel, Field, field_validator


class Provider(str, Enum):
    """
    支持的 LLM 提供商

    框架内置支持以下提供商：
    - OPENAI: OpenAI 官方 API（GPT 系列）
    - ANTHROPIC: Anthropic API（Claude 系列）
    - GLM: 智谱 AI（GLM 系列）
    - DEEPSEEK: DeepSeek API
    - OPENROUTER: OpenRouter 多模型网关
    - OLLAMA: Ollama 本地模型
    """

    OPENAI = "openai"
    ANTHROPIC = "anthropic"
    GLM = "glm"
    DEEPSEEK = "deepseek"
    OPENROUTER = "openrouter"
    OLLAMA = "ollama"

    @classmethod
    def list_supported(cls) -> list[str]:
        """列出所有支持的 provider"""
        return [p.value for p in cls]


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


class CacheBackend(str, Enum):
    """缓存后端类型"""

    MEMORY = "memory"
    REDIS = "redis"


class LLMCacheConfig(BaseModel):
    """
    LLM 缓存配置

    支持两种缓存后端：
    - memory: 内存缓存（默认，适合单进程）
    - redis: Redis 缓存（适合分布式）
    """

    enabled: bool = Field(default=True, description="是否启用 LLM 响应缓存")
    backend: str = Field(
        default=CacheBackend.MEMORY.value,
        description="缓存后端: memory 或 redis"
    )
    ttl_seconds: int = Field(default=300, gt=0, description="缓存 TTL（秒）")
    max_size: int = Field(default=1000, gt=0, description="内存缓存最大条目数")

    # Redis 专用配置
    redis_url: str | None = Field(default=None, description="Redis URL（backend=redis 时必填）")
    key_prefix: str = Field(default="llm_cache:", description="Redis 缓存键前缀")

    @field_validator("backend")
    @classmethod
    def validate_backend(cls, v: str) -> str:
        """校验缓存后端"""
        supported = [b.value for b in CacheBackend]
        if v.lower() not in supported:
            raise ValueError(f"不支持的缓存后端: '{v}'。支持: {', '.join(supported)}")
        return v.lower()


class LLMConfig(BaseModel):
    """
    LLM 配置

    包含：
    - 基础配置（api_key, model, base_url, provider）
    - 调用配置（temperature, timeout_seconds）
    - 思考模式（enable_thinking）
    - 弹性配置（retry, circuit_breaker）
    - 限流配置（rate_limit）
    - 缓存配置（cache）
    """

    # 基础配置
    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="模型名称")
    base_url: str | None = Field(default=None, description="API Base URL")
    provider: str = Field(
        default=Provider.OPENAI.value,
        description=f"LLM 提供商，支持: {', '.join(Provider.list_supported())}"
    )

    # 调用配置
    temperature: float = Field(default=0.0, description="温度")
    timeout_seconds: float = Field(default=120.0, description="单次调用超时（秒）")

    # 思考模式配置
    enable_thinking: bool = Field(
        default=False,
        description="是否启用思考模式（GLM/DeepSeek/Claude 等支持）"
    )
    thinking_budget_tokens: int | None = Field(
        default=None,
        description="思考 token 预算（Claude 专用，默认不限制）"
    )

    # 弹性配置
    retry: RetryConfig = Field(default_factory=RetryConfig, description="重试配置")
    circuit_breaker: CircuitBreakerConfig = Field(
        default_factory=CircuitBreakerConfig, description="熔断配置"
    )

    # 限流配置
    rate_limit: RateLimitConfig = Field(
        default_factory=RateLimitConfig, description="限流配置"
    )

    # 缓存配置
    cache: LLMCacheConfig = Field(
        default_factory=LLMCacheConfig, description="LLM 响应缓存配置"
    )

    @field_validator("provider")
    @classmethod
    def validate_provider(cls, v: str) -> str:
        """校验 provider 是否支持"""
        supported = Provider.list_supported()
        if v.lower() not in supported:
            raise ValueError(
                f"不支持的 provider: '{v}'。"
                f"框架支持: {', '.join(supported)}"
            )
        return v.lower()

    def is_configured(self) -> bool:
        """检查是否已配置"""
        return self.api_key is not None and self.model is not None


class EmbeddingBackend(str, Enum):
    """
    支持的 Embedding 提供商

    框架内置支持以下提供商：
    - OPENAI: OpenAI Embeddings（text-embedding-3-small/large）
    - GLM: 智谱 AI Embeddings（embedding-3）
    """

    OPENAI = "openai"
    GLM = "glm"

    @classmethod
    def list_supported(cls) -> list[str]:
        """列出所有支持的 embedding provider"""
        return [p.value for p in cls]


class EmbeddingConfig(BaseModel):
    """
    Embedding 配置

    包含：
    - provider: 提供商（openai, glm）
    - api_key: API Key
    - model: 模型名称
    - base_url: 自定义端点（可选）
    - dimension: 向量维度
    """

    provider: str = Field(
        default=EmbeddingBackend.OPENAI.value,
        description=f"Embedding 提供商，支持: {', '.join(EmbeddingBackend.list_supported())}"
    )
    api_key: str | None = Field(default=None, description="API Key")
    model: str | None = Field(default=None, description="模型名称")
    base_url: str | None = Field(default=None, description="API Base URL")
    dimension: int = Field(default=1536, description="向量维度")

    @field_validator("provider")
    @classmethod
    def validate_provider(cls, v: str) -> str:
        """校验 embedding provider 是否支持"""
        supported = EmbeddingBackend.list_supported()
        if v.lower() not in supported:
            raise ValueError(
                f"不支持的 embedding provider: '{v}'。"
                f"框架支持: {', '.join(supported)}"
            )
        return v.lower()

    def is_configured(self) -> bool:
        """检查是否已配置"""
        return self.api_key is not None and self.model is not None
