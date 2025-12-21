"""
LLM 集成层

提供统一的 LLM 调用接口，支持 OpenAI、Claude、GLM、OpenRouter、Ollama
"""

from src.infrastructure.llm.client import LLMFactory, call_llm, get_langchain_chat_model
from src.infrastructure.llm.embeddings import UnifiedEmbedder
from src.infrastructure.llm.glm import GlmNativeChatModel

__all__ = [
    "LLMFactory",
    "call_llm",
    "get_langchain_chat_model",
    "UnifiedEmbedder",
    "GlmNativeChatModel",
]
