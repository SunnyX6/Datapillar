from __future__ import annotations

from langchain_core.messages import AIMessage

from datapillar_oneagentic.providers.llm.llm import extract_thinking


def test_extract_thinking_from_additional_kwargs() -> None:
    msg = AIMessage(
        content="answer",
        additional_kwargs={"reasoning_content": "思考内容"},
    )
    assert extract_thinking(msg) == "思考内容"


def test_extract_thinking_from_list_content() -> None:
    class _Message:
        content = [
            {"type": "thinking", "thinking": "步骤1"},
            {"type": "thinking", "thinking": "步骤2"},
        ]
        additional_kwargs = {}

    assert extract_thinking(_Message()) == "步骤1\n步骤2"
