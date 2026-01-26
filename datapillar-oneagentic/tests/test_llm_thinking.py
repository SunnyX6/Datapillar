from __future__ import annotations

from datapillar_oneagentic.messages import Message
from datapillar_oneagentic.providers.llm.llm import extract_thinking


def test_extract_thinking() -> None:
    msg = Message.assistant(
        content="answer",
        metadata={"reasoning_content": "thinking content"},
    )
    assert extract_thinking(msg) == "thinking content"


def test_extract_thinking2() -> None:
    msg = Message.assistant(content="answer")
    assert extract_thinking(msg) is None
