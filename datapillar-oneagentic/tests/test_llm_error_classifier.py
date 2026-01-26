from datapillar_oneagentic.exception import (
    LLMErrorCategory,
    LLMErrorClassifier,
    RecoveryAction,
)


def test_llm_error() -> None:
    error = RuntimeError("rate limit exceeded")
    category, action = LLMErrorClassifier.classify(error)
    assert category == LLMErrorCategory.RATE_LIMIT
    assert action == RecoveryAction.RETRY
