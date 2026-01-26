"""Compaction tests."""

from __future__ import annotations

import pytest

from datapillar_oneagentic.context.compaction.compact_policy import CompactPolicy, CompactResult
from datapillar_oneagentic.context.compaction.compactor import Compactor
from datapillar_oneagentic.messages import Message, Messages


class _MockLLMResponse:
    def __init__(self, content: str):
        self.content = content


class _MockLLM:
    """Mock LLM returning a fixed summary."""

    def __init__(self, summary: str = "test summary"):
        self._summary = summary

    async def ainvoke(self, _messages):
        return _MockLLMResponse(self._summary)


@pytest.mark.asyncio
async def test_compress_ai() -> None:
    """Ensure compactor compresses AI/tool messages and keeps human/system messages."""
    policy = CompactPolicy(min_keep_entries=2)
    compactor = Compactor(llm=_MockLLM(), policy=policy)

    messages = [
        Message.system("system prompt"),
        Message.user("user question 1"),
        Message.assistant("assistant reply 1"),
        Message.tool("tool result 1", tool_call_id="t1"),
        Message.assistant("assistant reply 2"),
        Message.user("user question 2"),
        Message.assistant("assistant reply 3"),
    ]

    compressed, result = await compactor.compact(Messages(messages))

    assert result.success is True
    assert result.removed_count > 0
    assert result.kept_count > 0
    assert result.summary == "test summary"
    # Summary should not be injected into messages.
    assert all("test summary" not in msg.content for msg in compressed if isinstance(msg, Message))


@pytest.mark.asyncio
async def test_compactor_keep() -> None:
    """Ensure compactor keeps the most recent N messages."""
    policy = CompactPolicy(min_keep_entries=3)
    compactor = Compactor(llm=_MockLLM(), policy=policy)

    messages = [
        Message.user("question 1"),
        Message.assistant("reply 1"),
        Message.assistant("reply 2"),
        Message.assistant("reply 3"),
        Message.user("question 2"),
        Message.assistant("reply 4"),
    ]

    compressed, result = await compactor.compact(Messages(messages))

    assert result.success is True
    # The last 3 messages should be kept.
    assert any(m.content == "question 2" for m in compressed)
    assert any(m.content == "reply 4" for m in compressed)


@pytest.mark.asyncio
async def test_handle_llm() -> None:
    """Return failure when LLM call fails."""

    class _FailingLLM:
        async def ainvoke(self, _messages):
            raise RuntimeError("LLM call failed")

    policy = CompactPolicy(min_keep_entries=2)
    compactor = Compactor(llm=_FailingLLM(), policy=policy)

    messages = [
        Message.user("question 1"),
        Message.assistant("reply 1"),
        Message.assistant("reply 2"),
        Message.assistant("reply 3"),
    ]

    compressed, result = await compactor.compact(Messages(messages))

    assert result.success is False
    assert result.error is not None
    assert "LLM call failed" in result.error
