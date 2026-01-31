# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
LLM exception mapping.

Unifies provider-specific errors into framework-level exceptions and
organizes patterns by provider for maintainability.
"""

from typing import Final


class ContextLengthExceededError(Exception):
    """Context length exceeded error."""

    def __init__(self, message: str, provider: str | None = None, model: str | None = None):
        self.provider = provider
        self.model = model
        self.original_message = message
        super().__init__(self._format_message(message))

    def _format_message(self, message: str) -> str:
        prefix = "Context length exceeded"
        if self.provider:
            prefix = f"{self.provider} {prefix}"
        return f"{prefix}: {message}"


# ==================== Common error patterns ====================

COMMON_CONTEXT_EXCEEDED_PATTERNS: Final[list[str]] = [
    "exceed context limit",
    "this model's maximum context length is",
    "string too long. expected a string with maximum length",
    "model's maximum context limit",
    "is longer than the model's context length",
    "input tokens exceed the configured limit",
    "`inputs` tokens + `max_new_tokens` must be",
    "exceeds the maximum number of tokens allowed",
]


# ==================== Provider-specific error patterns ====================

# OpenAI / OpenAI-compatible (DeepSeek, OpenRouter, etc.)
OPENAI_CONTEXT_PATTERNS: Final[list[str]] = [
    # Covered by common patterns
]

# Anthropic (Claude)
ANTHROPIC_CONTEXT_PATTERNS: Final[list[str]] = [
    "prompt is too long",
    "prompt: length",
]

# Zhipu GLM
GLM_CONTEXT_PATTERNS: Final[list[str]] = [
    "context window limit",
    "the model has reached its context window limit",
    # Error messages for GLM codes 1210/1214 (kept as unicode escapes)
    "\u0061\u0070\u0069\u0020\u8c03\u7528\u53c2\u6570\u6709\u8bef",
    "\u53c2\u6570\u975e\u6cd5",
]

# AWS Bedrock
BEDROCK_CONTEXT_PATTERNS: Final[list[str]] = [
    "too many tokens",
    "expected maxlength",
    "input is too long",
    "too many input tokens",
    "prompt: length: 1..",
]

# Google Vertex AI / Gemini
VERTEX_CONTEXT_PATTERNS: Final[list[str]] = [
    "400 request payload size exceeds",
]

# Cohere
COHERE_CONTEXT_PATTERNS: Final[list[str]] = [
    "too many tokens",
]

# Huggingface
HUGGINGFACE_CONTEXT_PATTERNS: Final[list[str]] = [
    "length limit exceeded",
]

# Replicate
REPLICATE_CONTEXT_PATTERNS: Final[list[str]] = [
    "input is too long",
]

# Together AI
TOGETHER_CONTEXT_PATTERNS: Final[list[str]] = [
    "`inputs` tokens + `max_new_tokens` must be <=",
]

# Ollama
OLLAMA_CONTEXT_PATTERNS: Final[list[str]] = [
    "context length exceeded",
]


class ExceptionMapper:
    """
    Exception mapper.

    Matches error messages against known patterns.
    """

    # Merge all provider context-limit patterns
    ALL_CONTEXT_PATTERNS: Final[list[str]] = (
        COMMON_CONTEXT_EXCEEDED_PATTERNS
        + ANTHROPIC_CONTEXT_PATTERNS
        + GLM_CONTEXT_PATTERNS
        + BEDROCK_CONTEXT_PATTERNS
        + VERTEX_CONTEXT_PATTERNS
        + COHERE_CONTEXT_PATTERNS
        + HUGGINGFACE_CONTEXT_PATTERNS
        + REPLICATE_CONTEXT_PATTERNS
        + TOGETHER_CONTEXT_PATTERNS
        + OLLAMA_CONTEXT_PATTERNS
    )

    @classmethod
    def is_context_exceeded(cls, error: Exception | str) -> bool:
        """
        Check whether an error is a context-length-exceeded error.

        Args:
            error: Exception object or error message string

        Returns:
            True if the error indicates context length exceeded
        """
        error_str = str(error).lower()

        for pattern in cls.ALL_CONTEXT_PATTERNS:
            if pattern.lower() in error_str:
                return True

        return "current length is" in error_str and "while limit is" in error_str

    @classmethod
    def wrap_context_exceeded(
        cls,
        error: Exception,
        provider: str | None = None,
        model: str | None = None,
    ) -> ContextLengthExceededError:
        """
        Wrap an error as ContextLengthExceededError.

        Args:
            error: Original exception
            provider: LLM provider
            model: Model name

        Returns:
            ContextLengthExceededError instance
        """
        return ContextLengthExceededError(
            message=str(error),
            provider=provider,
            model=model,
        )
