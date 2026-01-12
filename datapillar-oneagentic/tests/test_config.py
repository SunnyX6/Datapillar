"""
配置系统单元测试

测试模块：datapillar_oneagentic.config
"""

import pytest

from datapillar_oneagentic.config import (
    ConfigurationError,
    DatapillarConfig,
    datapillar,
    datapillar_configure,
    get_config,
    reset_config,
)


class TestDatapillarConfig:
    """DatapillarConfig 配置类测试"""

    @pytest.fixture(autouse=True)
    def reset_global_config(self):
        """每个测试前重置全局配置"""
        reset_config()
        yield
        reset_config()

    def test_default_config(self):
        """测试默认配置"""
        config = DatapillarConfig()

        assert config.llm is not None
        assert config.embedding is not None
        assert config.context is not None
        assert config.agent is not None
        assert config.learning is not None
        assert config.cache is not None
        assert config.telemetry is not None

    def test_llm_not_configured_by_default(self):
        """测试 LLM 默认未配置"""
        config = DatapillarConfig()

        assert config.is_llm_configured() is False

    def test_embedding_not_configured_by_default(self):
        """测试 Embedding 默认未配置"""
        config = DatapillarConfig()

        assert config.is_embedding_configured() is False

    def test_validate_llm_raises_error_when_not_configured(self):
        """测试 validate_llm 在未配置时抛出异常"""
        config = DatapillarConfig()

        with pytest.raises(ConfigurationError) as exc_info:
            config.validate_llm()

        assert "LLM 未配置" in str(exc_info.value)

    def test_validate_embedding_raises_error_when_not_configured(self):
        """测试 validate_embedding 在未配置时抛出异常"""
        config = DatapillarConfig()

        with pytest.raises(ConfigurationError) as exc_info:
            config.validate_embedding()

        assert "Embedding 未配置" in str(exc_info.value)


class TestDatapillarConfigure:
    """datapillar_configure() 函数测试"""

    @pytest.fixture(autouse=True)
    def reset_global_config(self):
        """每个测试前重置全局配置"""
        reset_config()
        yield
        reset_config()

    def test_configure_llm(self):
        """测试配置 LLM"""
        config = datapillar_configure(
            llm={
                "api_key": "test-key",
                "model": "gpt-4o",
                "base_url": "https://api.openai.com/v1",
            }
        )

        assert config.llm.api_key == "test-key"
        assert config.llm.model == "gpt-4o"
        assert config.llm.base_url == "https://api.openai.com/v1"
        assert config.is_llm_configured() is True

    def test_configure_embedding(self):
        """测试配置 Embedding"""
        config = datapillar_configure(
            embedding={
                "api_key": "embed-key",
                "model": "text-embedding-3-small",
            }
        )

        assert config.embedding.api_key == "embed-key"
        assert config.embedding.model == "text-embedding-3-small"
        assert config.is_embedding_configured() is True

    def test_configure_context(self):
        """测试配置 Context"""
        config = datapillar_configure(
            context={
                "window_size": 64000,
            }
        )

        assert config.context.window_size == 64000

    def test_configure_agent(self):
        """测试配置 Agent"""
        config = datapillar_configure(
            agent={
                "max_steps": 100,
            }
        )

        assert config.agent.max_steps == 100

    def test_configure_llm_resilience(self):
        """测试配置 LLM 弹性参数（retry、timeout 在 llm 配置中）"""
        config = datapillar_configure(
            llm={
                "timeout_seconds": 180,
                "retry": {
                    "max_retries": 5,
                },
            }
        )

        assert config.llm.retry.max_retries == 5
        assert config.llm.timeout_seconds == 180

    def test_configure_multiple_sections(self):
        """测试同时配置多个部分"""
        config = datapillar_configure(
            llm={"api_key": "llm-key", "model": "gpt-4o"},
            embedding={"api_key": "embed-key", "model": "text-embedding-3-small"},
            agent={"max_steps": 50},
        )

        assert config.llm.api_key == "llm-key"
        assert config.embedding.api_key == "embed-key"
        assert config.agent.max_steps == 50

    def test_configure_replaces_existing(self):
        """测试重新配置会替换已有配置"""
        datapillar_configure(llm={"api_key": "key1", "model": "gpt-4"})
        config = datapillar_configure(llm={"api_key": "key2", "model": "gpt-4o"})

        # 新配置完全替换旧配置
        assert config.llm.api_key == "key2"
        assert config.llm.model == "gpt-4o"

    def test_configure_extra_fields(self):
        """测试额外配置字段"""
        config = datapillar_configure(custom_field="custom_value")

        # pydantic-settings extra="allow" 将额外字段存储在 model_extra 中
        assert config.model_extra.get("custom_field") == "custom_value"
        # 也可以直接作为属性访问
        assert config.custom_field == "custom_value"


class TestGetConfig:
    """get_config() 函数测试"""

    @pytest.fixture(autouse=True)
    def reset_global_config(self):
        """每个测试前重置全局配置"""
        reset_config()
        yield
        reset_config()

    def test_get_config_returns_default_if_not_configured(self):
        """测试未配置时返回默认配置"""
        config = get_config()

        assert isinstance(config, DatapillarConfig)
        assert config.is_llm_configured() is False

    def test_get_config_returns_same_instance(self):
        """测试返回同一个实例"""
        config1 = get_config()
        config2 = get_config()

        assert config1 is config2


class TestDatapillarProxy:
    """datapillar 代理对象测试"""

    @pytest.fixture(autouse=True)
    def reset_global_config(self):
        """每个测试前重置全局配置"""
        reset_config()
        yield
        reset_config()

    def test_proxy_access_llm(self):
        """测试代理访问 datapillar.llm 配置"""
        datapillar_configure(llm={"api_key": "test-key", "model": "gpt-4o"})

        assert datapillar.llm.api_key == "test-key"
        assert datapillar.llm.model == "gpt-4o"

    def test_proxy_access_agent(self):
        """测试代理访问 datapillar.agent 配置"""
        datapillar_configure(agent={"max_steps": 75})

        assert datapillar.agent.max_steps == 75

    def test_proxy_access_llm_retry(self):
        """测试代理访问 datapillar.llm.retry 配置"""
        datapillar_configure(llm={"retry": {"max_retries": 10}})

        assert datapillar.llm.retry.max_retries == 10

    def test_proxy_access_context(self):
        """测试代理访问 datapillar.context 配置"""
        datapillar_configure(context={"window_size": 128000})

        assert datapillar.context.window_size == 128000

    def test_proxy_access_embedding(self):
        """测试代理访问 datapillar.embedding 配置"""
        datapillar_configure(embedding={"api_key": "embed-key", "model": "text-embedding-3-small"})

        assert datapillar.embedding.api_key == "embed-key"
        assert datapillar.embedding.model == "text-embedding-3-small"

    def test_proxy_access_extra(self):
        """测试代理访问额外配置"""
        datapillar_configure(redis_url="redis://localhost:6379")

        # pydantic-settings extra="allow" 将额外字段存储在 model_extra 中
        assert datapillar.model_extra.get("redis_url") == "redis://localhost:6379"
        # 也可以直接访问
        assert datapillar.redis_url == "redis://localhost:6379"


class TestLLMConfig:
    """LLMConfig 测试"""

    def test_llm_config_defaults(self):
        """测试 LLMConfig 默认值"""
        from datapillar_oneagentic.providers.llm.config import LLMConfig

        config = LLMConfig()

        assert config.api_key is None
        assert config.model is None
        assert config.base_url is None
        assert config.temperature == 0.0
        assert config.timeout_seconds == 120.0

    def test_llm_config_is_configured(self):
        """测试 is_configured()"""
        from datapillar_oneagentic.providers.llm.config import LLMConfig

        config_empty = LLMConfig()
        config_with_key = LLMConfig(api_key="key", model="model")

        assert config_empty.is_configured() is False
        assert config_with_key.is_configured() is True


class TestEmbeddingConfig:
    """EmbeddingConfig 测试"""

    def test_embedding_config_defaults(self):
        """测试 EmbeddingConfig 默认值"""
        from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

        config = EmbeddingConfig()

        assert config.api_key is None
        assert config.model is None
        assert config.base_url is None
        assert config.dimension == 1536

    def test_embedding_config_is_configured(self):
        """测试 is_configured()"""
        from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

        config_empty = EmbeddingConfig()
        config_with_key = EmbeddingConfig(api_key="key", model="model")

        assert config_empty.is_configured() is False
        assert config_with_key.is_configured() is True


class TestAgentConfig:
    """AgentConfig 测试"""

    def test_agent_config_defaults(self):
        """测试 AgentConfig 默认值"""
        from datapillar_oneagentic.core.config import AgentConfig

        config = AgentConfig()

        assert config.max_steps == 25
        assert config.timeout_seconds == 300.0
        assert config.tool_timeout_seconds == 30.0


class TestContextConfig:
    """ContextConfig 测试"""

    def test_context_config_defaults(self):
        """测试 ContextConfig 默认值"""
        from datapillar_oneagentic.core.config import ContextConfig

        config = ContextConfig()

        assert config.window_size == 200000
        assert config.compact_trigger_threshold == 0.95
        assert config.compact_target_ratio == 0.60
        assert config.compact_min_keep_entries == 5
        assert config.compact_max_summary_tokens == 2000

    def test_get_trigger_tokens(self):
        """测试 get_trigger_tokens()"""
        from datapillar_oneagentic.core.config import ContextConfig

        config = ContextConfig(window_size=100000, compact_trigger_threshold=0.8)

        assert config.get_trigger_tokens() == 80000

    def test_get_target_tokens(self):
        """测试 get_target_tokens()"""
        from datapillar_oneagentic.core.config import ContextConfig

        config = ContextConfig(window_size=100000, compact_target_ratio=0.5)

        assert config.get_target_tokens() == 50000


class TestLLMResilienceConfig:
    """LLM 弹性配置测试（retry、circuit_breaker 在 LLMConfig 中）"""

    def test_retry_config_defaults(self):
        """测试 RetryConfig 默认值"""
        from datapillar_oneagentic.providers.llm.config import RetryConfig

        config = RetryConfig()

        assert config.max_retries == 3
        assert config.initial_delay_ms == 500
        assert config.max_delay_ms == 30000
        assert config.exponential_base == 2.0
        assert config.jitter is True

    def test_circuit_breaker_config_defaults(self):
        """测试 CircuitBreakerConfig 默认值"""
        from datapillar_oneagentic.providers.llm.config import CircuitBreakerConfig

        config = CircuitBreakerConfig()

        assert config.failure_threshold == 5
        assert config.recovery_seconds == 60

    def test_llm_config_has_resilience_settings(self):
        """测试 LLMConfig 包含弹性配置"""
        from datapillar_oneagentic.providers.llm.config import LLMConfig

        config = LLMConfig()

        assert config.timeout_seconds == 120.0
        assert config.retry.max_retries == 3
        assert config.circuit_breaker.failure_threshold == 5
        assert config.rate_limit.enabled is True

    def test_calculate_retry_delay_exponential(self):
        """测试延迟计算指数退避"""
        from datapillar_oneagentic.providers.llm.config import RetryConfig
        from datapillar_oneagentic.resilience.config import calculate_retry_delay

        config = RetryConfig(
            initial_delay_ms=1000,
            max_delay_ms=100000,
            exponential_base=2.0,
            jitter=False,
        )

        delay_0 = calculate_retry_delay(config, 0)
        delay_1 = calculate_retry_delay(config, 1)
        delay_2 = calculate_retry_delay(config, 2)

        assert delay_0 == 1.0
        assert delay_1 == 2.0
        assert delay_2 == 4.0

    def test_calculate_retry_delay_with_max_limit(self):
        """测试延迟计算最大值限制"""
        from datapillar_oneagentic.providers.llm.config import RetryConfig
        from datapillar_oneagentic.resilience.config import calculate_retry_delay

        config = RetryConfig(
            initial_delay_ms=1000,
            max_delay_ms=5000,
            exponential_base=2.0,
            jitter=False,
        )

        delay_10 = calculate_retry_delay(config, 10)

        assert delay_10 == 5.0

    def test_calculate_retry_delay_with_jitter(self):
        """测试延迟计算抖动"""
        from datapillar_oneagentic.providers.llm.config import RetryConfig
        from datapillar_oneagentic.resilience.config import calculate_retry_delay

        config = RetryConfig(
            initial_delay_ms=1000,
            max_delay_ms=100000,
            exponential_base=2.0,
            jitter=True,
        )

        delays = [calculate_retry_delay(config, 0) for _ in range(100)]

        base_delay = 1.0
        jitter_range = base_delay * 0.25
        assert min(delays) >= base_delay - jitter_range
        assert max(delays) <= base_delay + jitter_range
        assert len(set(delays)) > 1
