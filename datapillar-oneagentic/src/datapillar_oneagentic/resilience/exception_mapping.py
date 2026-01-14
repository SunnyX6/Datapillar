"""
LLM 异常映射模块

将各 LLM Provider 的异常统一映射为框架内部异常。
按 Provider 组织错误模式，便于维护。
"""

from typing import Final


class ContextLengthExceededError(Exception):
    """上下文长度超限异常"""

    def __init__(self, message: str, provider: str | None = None, model: str | None = None):
        self.provider = provider
        self.model = model
        self.original_message = message
        super().__init__(self._format_message(message))

    def _format_message(self, message: str) -> str:
        prefix = "上下文长度超限"
        if self.provider:
            prefix = f"{self.provider} {prefix}"
        return f"{prefix}: {message}"


# ==================== 通用错误模式 ====================

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


# ==================== Provider 特定错误模式 ====================

# OpenAI / OpenAI 兼容 (DeepSeek, OpenRouter 等)
OPENAI_CONTEXT_PATTERNS: Final[list[str]] = [
    # 通用模式已覆盖
]

# Anthropic (Claude)
ANTHROPIC_CONTEXT_PATTERNS: Final[list[str]] = [
    "prompt is too long",
    "prompt: length",
]

# 智谱 GLM
GLM_CONTEXT_PATTERNS: Final[list[str]] = [
    "context window limit",
    "the model has reached its context window limit",
    # 错误码 1210/1214 的错误信息
    "api 调用参数有误",
    "参数非法",
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
    异常映射器

    检查错误信息是否匹配特定异常类型。
    """

    # 合并所有 Provider 的上下文超限模式
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
    def is_context_length_exceeded(cls, error: Exception | str) -> bool:
        """
        检查错误是否为上下文长度超限

        Args:
            error: 异常对象或错误信息字符串

        Returns:
            True 如果是上下文超限错误
        """
        error_str = str(error).lower()

        # 检查所有已知模式
        for pattern in cls.ALL_CONTEXT_PATTERNS:
            if pattern.lower() in error_str:
                return True

        # Cerebras 特殊模式: "Current length is X while limit is Y"
        return "current length is" in error_str and "while limit is" in error_str

    @classmethod
    def wrap_context_exceeded(
        cls,
        error: Exception,
        provider: str | None = None,
        model: str | None = None,
    ) -> ContextLengthExceededError:
        """
        将原始异常包装为 ContextLengthExceededError

        Args:
            error: 原始异常
            provider: LLM 提供商
            model: 模型名称

        Returns:
            ContextLengthExceededError 实例
        """
        return ContextLengthExceededError(
            message=str(error),
            provider=provider,
            model=model,
        )
