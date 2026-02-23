from __future__ import annotations

from datapillar_oneagentic.exception import (
    ContextLengthExceededException,
    ExceptionMapper,
    RecoveryAction,
    TooManyRequestsException,
    action_for,
)


class _FakeRateLimitError(Exception):
    def __init__(self):
        super().__init__("rate limit")
        self.status_code = 429


class _FakeContextExceededError(Exception):
    def __init__(self):
        super().__init__("context exceeded")
        self.status_code = 400
        self.code = "context_length_exceeded"


def test_map_rate_limit_to_retryable_exception() -> None:
    mapped = ExceptionMapper.map_llm_error(_FakeRateLimitError(), provider="openai", model="gpt")
    assert isinstance(mapped, TooManyRequestsException)
    assert action_for(mapped) == RecoveryAction.RETRY


def test_map_context_exceeded_to_fail_fast_exception() -> None:
    mapped = ExceptionMapper.map_llm_error(_FakeContextExceededError(), provider="openai", model="gpt")
    assert isinstance(mapped, ContextLengthExceededException)
    assert action_for(mapped) == RecoveryAction.FAIL_FAST
