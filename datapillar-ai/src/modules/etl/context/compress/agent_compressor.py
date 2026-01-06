"""
Agent 上下文压缩器

职责：
- 检查指定 Agent 的上下文是否需要压缩
- 调用 LLM 生成对话摘要
- 更新 SessionMemory

触发条件：
- 用户手动 /compress
- 该 Agent 上下文 token 数 >= 阈值

Fallback 策略：
- LLM 压缩失败时，保留 recent_turns，不丢弃原始数据
- 返回 CompressionResult 让上层感知压缩状态
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, Literal

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage

from src.infrastructure.resilience import (
    ErrorClassifier,
    get_resilience_config,
)
from src.modules.etl.context.compress.budget import (
    ContextBudget,
    HeuristicTokenEstimator,
    get_default_budget,
)

if TYPE_CHECKING:
    from langchain_core.language_models import BaseChatModel

    from src.modules.etl.memory.session_memory import SessionMemory

logger = logging.getLogger(__name__)


_COMPRESS_SYSTEM_PROMPT = """你是对话压缩助手。

你的任务是将对话历史压缩成简洁的摘要，保留关键信息。

要求：
1. 保留用户的核心需求和意图
2. 保留重要的决策和确认
3. 保留关键的技术细节（表名、字段、条件等）
4. 删除冗余的寒暄和重复内容
5. 输出纯文本摘要，不超过 500 字

只输出摘要文本，不要解释。
"""


@dataclass
class CompressionResult:
    """
    压缩结果

    设计原则：
    - 明确表示压缩成功/失败
    - 失败时提供降级策略
    - 让上层感知压缩状态
    """

    status: Literal["success", "failed", "skipped"]
    summary: str | None = None
    error: str | None = None
    keep_recent_turns: bool = False  # 失败时是否保留原始对话

    @classmethod
    def success(cls, summary: str) -> CompressionResult:
        return cls(status="success", summary=summary)

    @classmethod
    def failed(cls, error: str, keep_recent: bool = True) -> CompressionResult:
        return cls(status="failed", error=error, keep_recent_turns=keep_recent)

    @classmethod
    def skipped(cls) -> CompressionResult:
        return cls(status="skipped")


def estimate_context_tokens(
    *,
    system_instructions: str,
    context_payload: dict[str, Any] | None,
    memory_context: dict[str, Any] | None,
    user_query: str,
) -> int:
    """
    估算指定 Agent 的上下文 token 数

    参数：
    - system_instructions: 系统指令
    - context_payload: 知识上下文（etl_pointers 等）
    - memory_context: 记忆上下文（对话历史、需求 TODO）
    - user_query: 用户当前输入
    """
    estimator = HeuristicTokenEstimator()

    messages: list[BaseMessage] = [SystemMessage(content=system_instructions)]

    if context_payload:
        messages.append(
            SystemMessage(content=json.dumps(context_payload, ensure_ascii=False, default=str))
        )

    if memory_context:
        messages.append(
            SystemMessage(content=json.dumps(memory_context, ensure_ascii=False, default=str))
        )

    messages.append(HumanMessage(content=user_query))

    return estimator.estimate_messages_tokens(messages)


def should_compress(
    *,
    estimated_tokens: int,
    budget: ContextBudget | None = None,
    manual_trigger: bool = False,
) -> bool:
    """
    判断是否需要压缩

    参数：
    - estimated_tokens: 估算的 token 数
    - budget: 上下文预算
    - manual_trigger: 是否用户手动触发
    """
    if manual_trigger:
        return True

    b = budget or get_default_budget()
    soft_limit = int(b.model_context_tokens * b.soft_limit_ratio)

    return estimated_tokens >= soft_limit


async def compress_agent_conversation(
    *,
    llm: BaseChatModel,
    memory: SessionMemory,
    agent_id: str,
    include_todos: bool = True,
) -> CompressionResult:
    """
    压缩指定 Agent 的对话历史

    参数：
    - llm: LLM 实例
    - memory: SessionMemory 实例
    - agent_id: Agent ID
    - include_todos: 是否包含需求 TODO

    返回：
    - CompressionResult: 压缩结果（成功/失败/跳过）

    Fallback 策略：
    - 使用 resilience 配置的重试次数
    - 失败时保留 recent_turns，不丢弃原始数据
    """
    conv = memory.get_agent_conversation(agent_id)

    # 构建待压缩的内容
    content_parts = []

    # 已有的压缩摘要
    if conv.compressed_summary:
        content_parts.append(f"【历史摘要】\n{conv.compressed_summary}")

    # 最近的对话
    if conv.recent_turns:
        turns_text = []
        for turn in conv.recent_turns:
            role = "用户" if turn.get("role") == "user" else "助手"
            turns_text.append(f"{role}: {turn.get('content', '')}")
        content_parts.append("【最近对话】\n" + "\n".join(turns_text))

    # 需求 TODO
    if include_todos and memory.requirement_todos:
        todos_text = []
        for todo in memory.requirement_todos:
            status = "✅" if todo.get("status") == "done" else "⏳"
            todos_text.append(f"{status} {todo.get('title', '')}")
        content_parts.append("【需求 TODO】\n" + "\n".join(todos_text))

    if not content_parts:
        return CompressionResult.skipped()

    full_content = "\n\n".join(content_parts)

    # 调用 LLM 压缩（带重试）
    messages: list[BaseMessage] = [
        SystemMessage(content=_COMPRESS_SYSTEM_PROMPT),
        HumanMessage(content=f"请压缩以下内容：\n\n{full_content}"),
    ]

    config = get_resilience_config()
    last_error: Exception | None = None

    for attempt in range(config.max_retries + 1):
        try:
            response = await llm.ainvoke(messages)
            summary = (getattr(response, "content", "") or "").strip()

            if not summary:
                raise ValueError("LLM 返回空摘要")

            logger.info(f"Agent {agent_id} 对话压缩完成，摘要长度: {len(summary)}")
            return CompressionResult.success(summary)

        except Exception as e:
            last_error = e

            # 不可重试错误，直接失败
            if not ErrorClassifier.is_retryable(e):
                logger.error(f"Agent {agent_id} 压缩失败（不可重试）: {e}")
                break

            # 已达最大重试次数
            if attempt >= config.max_retries:
                logger.error(f"Agent {agent_id} 压缩失败（重试耗尽）: {e}")
                break

            # 计算延迟并等待
            import asyncio

            delay = config.calculate_delay(attempt)
            logger.warning(
                f"Agent {agent_id} 压缩失败，{delay:.2f}s 后重试 "
                f"(第 {attempt + 1}/{config.max_retries} 次): {e}"
            )
            await asyncio.sleep(delay)

    # 压缩失败，返回失败结果（保留 recent_turns）
    error_msg = str(last_error) if last_error else "未知错误"
    return CompressionResult.failed(
        error=error_msg,
        keep_recent=True,  # 关键：不丢弃原始数据
    )


async def maybe_compress(
    *,
    llm: BaseChatModel,
    memory: SessionMemory,
    agent_id: str,
    system_instructions: str,
    context_payload: dict[str, Any] | None,
    user_query: str,
    budget: ContextBudget | None = None,
    manual_trigger: bool = False,
) -> CompressionResult:
    """
    检查并执行压缩（如果需要）

    参数：
    - llm: LLM 实例
    - memory: SessionMemory 实例
    - agent_id: Agent ID
    - system_instructions: 系统指令
    - context_payload: 知识上下文
    - user_query: 用户当前输入
    - budget: 上下文预算
    - manual_trigger: 是否用户手动触发

    返回：
    - CompressionResult: 压缩结果
    """
    memory_context = memory.get_agent_context(agent_id)

    estimated = estimate_context_tokens(
        system_instructions=system_instructions,
        context_payload=context_payload,
        memory_context=memory_context,
        user_query=user_query,
    )

    if not should_compress(
        estimated_tokens=estimated,
        budget=budget,
        manual_trigger=manual_trigger,
    ):
        return CompressionResult.skipped()

    logger.info(f"Agent {agent_id} 触发压缩: estimated={estimated}, manual={manual_trigger}")

    result = await compress_agent_conversation(
        llm=llm,
        memory=memory,
        agent_id=agent_id,
    )

    # 根据结果更新 memory
    if result.status == "success" and result.summary:
        memory.apply_agent_compression(agent_id, result.summary)
    elif result.status == "failed" and not result.keep_recent_turns:
        # 只有明确不保留时才清空（当前设计总是保留）
        memory.apply_agent_compression(agent_id, f"[压缩失败] {result.error}")

    # 失败但保留 recent_turns 时，不调用 apply_compression，保持原样

    return result
