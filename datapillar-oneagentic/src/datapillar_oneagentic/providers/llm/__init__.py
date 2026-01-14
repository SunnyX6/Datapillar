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
- 统一接口 call_llm / call_embedding
- 内置弹性机制（超时 + 重试 + 熔断）
- 可选缓存
- Token 使用量追踪

使用示例：
```python
from datapillar_oneagentic import datapillar_configure
from datapillar_oneagentic.providers.llm import call_llm, call_embedding

# 必须先配置
datapillar_configure(
    llm={"provider": "openai", "api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"provider": "openai", "api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

# 获取 LLM（自动带弹性保护）
llm = call_llm()
result = await llm.ainvoke(messages)

# 获取 Embedding
embeddings = call_embedding()
vector = await embeddings.aembed_query("hello")

# 带 structured output
from pydantic import BaseModel
class Output(BaseModel):
    answer: str

llm = call_llm(output_schema=Output)
```
"""

from datapillar_oneagentic.providers.llm.config import (
    EmbeddingProvider,
    Provider,
)
from datapillar_oneagentic.providers.llm.embedding import (
    EmbeddingFactory,
    call_embedding,
    clear_embedding_cache,
    embed_text,
    embed_texts,
)
from datapillar_oneagentic.providers.llm.llm import (
    LLMFactory,
    LLMProviderConfig,
    ResilientChatModel,
    call_llm,
    clear_llm_cache,
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
    "call_llm",
    "clear_llm_cache",
    "ResilientChatModel",
    "LLMFactory",
    "LLMProviderConfig",
    # Embedding
    "call_embedding",
    "embed_text",
    "embed_texts",
    "clear_embedding_cache",
    "EmbeddingFactory",
    # Provider 枚举
    "Provider",
    "EmbeddingProvider",
    # Usage 追踪
    "TokenUsage",
    "extract_usage",
    # 缓存
    "create_llm_cache",
    "InMemoryLLMCache",
    "RedisLLMCache",
]
