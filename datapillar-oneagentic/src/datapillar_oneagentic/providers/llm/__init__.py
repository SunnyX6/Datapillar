"""
LLM provider module.

Supported backends:
- OpenAI
- Anthropic
- GLM
- DeepSeek
- OpenRouter
- Ollama

Features:
- Team-level LLMProvider / EmbeddingProvider
- Built-in resilience (timeout + retry + circuit breaker)
- Optional cache
- Token usage tracking

Example:
```python
from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.providers.llm import LLMProvider, EmbeddingProvider
from datapillar_oneagentic.messages import Messages, Message

config = DatapillarConfig(
    llm={"provider": "openai", "api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"provider": "openai", "api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

llm_provider = LLMProvider(config.llm)
llm = llm_provider()
messages = Messages([Message.system("sys"), Message.user("hi")])
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
    # Provider enums
    "Provider",
    "EmbeddingBackend",
    # Usage tracking
    "TokenUsage",
    "extract_usage",
    # Cache
    "create_llm_cache",
    "InMemoryLLMCache",
    "RedisLLMCache",
]
