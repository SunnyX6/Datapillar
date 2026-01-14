"""压缩机制测试"""

from __future__ import annotations

import pytest
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage

from datapillar_oneagentic.context.compaction.compact_policy import CompactPolicy, CompactResult
from datapillar_oneagentic.context.compaction.compactor import Compactor


class _MockLLMResponse:
    def __init__(self, content: str):
        self.content = content


class _MockLLM:
    """Mock LLM，返回固定摘要"""

    def __init__(self, summary: str = "测试摘要"):
        self._summary = summary

    async def ainvoke(self, _messages):
        return _MockLLMResponse(self._summary)


@pytest.mark.asyncio
async def test_compactor_should_compress_ai_and_tool_messages() -> None:
    """验证压缩器正确压缩 AI 和 Tool 消息，保留 Human 和 System 消息"""
    policy = CompactPolicy(min_keep_entries=2)
    compactor = Compactor(llm=_MockLLM(), policy=policy)

    messages = [
        SystemMessage(content="系统提示"),
        HumanMessage(content="用户问题1"),
        AIMessage(content="AI回复1"),
        ToolMessage(content="工具结果1", tool_call_id="t1"),
        AIMessage(content="AI回复2"),
        HumanMessage(content="用户问题2"),
        AIMessage(content="AI回复3"),
    ]

    compressed, result = await compactor.compact(messages)

    assert result.success is True
    assert result.removed_count > 0
    assert result.kept_count > 0
    # 压缩后第一条应该是历史摘要
    assert isinstance(compressed[0], SystemMessage)
    assert "[历史摘要]" in compressed[0].content


@pytest.mark.asyncio
async def test_compactor_should_keep_recent_messages() -> None:
    """验证压缩器保留最近 N 条消息"""
    policy = CompactPolicy(min_keep_entries=3)
    compactor = Compactor(llm=_MockLLM(), policy=policy)

    messages = [
        HumanMessage(content="问题1"),
        AIMessage(content="回复1"),
        AIMessage(content="回复2"),
        AIMessage(content="回复3"),
        HumanMessage(content="问题2"),
        AIMessage(content="回复4"),
    ]

    compressed, result = await compactor.compact(messages)

    assert result.success is True
    # 最后3条消息应该被保留
    assert any(m.content == "问题2" for m in compressed)
    assert any(m.content == "回复4" for m in compressed)


@pytest.mark.asyncio
async def test_compactor_should_handle_llm_failure() -> None:
    """验证 LLM 调用失败时返回失败结果"""

    class _FailingLLM:
        async def ainvoke(self, _messages):
            raise RuntimeError("LLM 调用失败")

    policy = CompactPolicy(min_keep_entries=2)
    compactor = Compactor(llm=_FailingLLM(), policy=policy)

    messages = [
        HumanMessage(content="问题1"),
        AIMessage(content="回复1"),
        AIMessage(content="回复2"),
        AIMessage(content="回复3"),
    ]

    compressed, result = await compactor.compact(messages)

    assert result.success is False
    assert result.error is not None
    assert "LLM 调用失败" in result.error
