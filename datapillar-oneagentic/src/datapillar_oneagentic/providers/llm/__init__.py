"""
LLM 提供者模块

支持多种 LLM 后端：
- OpenAI
- Anthropic
- GLM
- DeepSeek
- OpenRouter
- Ollama

特性：
- 团队级 LLMProvider / EmbeddingProvider
- 内置弹性机制（超时 + 重试 + 熔断）
- 可选缓存
- Token 使用量追踪

使用示例：
```python
from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.providers.llm import LLMProvider, EmbeddingProvider

config = DatapillarConfig(
    llm={"provider": "openai", "api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"provider": "openai", "api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

llm_provider = LLMProvider(config.llm)
llm = llm_provider()
result = await llm.ainvoke(messages)

embedding_provider = EmbeddingProvider(config.embedding)
vector = await embedding_provider.embed_text("hello")
```
"""

from datapillar_oneagentic.providers.llm.config import (
    EmbeddingBackend,
    Provider,
)
from datapillar_oneagentic.providers.llm.embedding import (
    EmbeddingFactory,
    EmbeddingProvider,
)
from datapillar_oneagentic.providers.llm.llm import (
    LLMFactory,
    LLMProviderConfig,
    ResilientChatModel,
    LLMProvider,
)
from datapillar_oneagentic.providers.llm.llm_cache import (
    InMemoryLLMCache,
    RedisLLMCache,
    create_llm_cache,
)
from datapillar_oneagentic.providers.llm.usage_tracker import (
    TokenUsage,
    extract_usage,
)

__all__ = [
    # LLM
    "LLMProvider",
    "ResilientChatModel",
    "LLMFactory",
    "LLMProviderConfig",
    # Embedding
    "EmbeddingProvider",
    "EmbeddingFactory",
    # Provider 枚举
    "Provider",
    "EmbeddingBackend",
    # Usage 追踪
    "TokenUsage",
    "extract_usage",
    # 缓存
    "create_llm_cache",
    "InMemoryLLMCache",
    "RedisLLMCache",
]
