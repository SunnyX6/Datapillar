from datapillar_oneagentic.exception import (
    LLMErrorCategory,
    LLMErrorClassifier,
    RecoveryAction,
)


def test_llm_error_classifier_rate_limit() -> None:
    error = RuntimeError("rate limit exceeded")
    category, action = LLMErrorClassifier.classify(error)
    assert category == LLMErrorCategory.RATE_LIMIT
    assert action == RecoveryAction.RETRY
