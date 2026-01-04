"""
Agent 上下文压缩器

职责：
- 检查指定 Agent 的上下文是否需要压缩
- 调用 LLM 生成对话摘要
- 更新 SessionMemory

触发条件：
- 用户手动 /compress
- 该 Agent 上下文 token 数 >= 阈值
"""

from __future__ import annotations

import json
import logging
from typing import TYPE_CHECKING, Any

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage

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
) -> str:
    """
    压缩指定 Agent 的对话历史

    参数：
    - llm: LLM 实例
    - memory: SessionMemory 实例
    - agent_id: Agent ID
    - include_todos: 是否包含需求 TODO

    返回：
    - 压缩后的摘要
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
        return ""

    full_content = "\n\n".join(content_parts)

    # 调用 LLM 压缩
    messages: list[BaseMessage] = [
        SystemMessage(content=_COMPRESS_SYSTEM_PROMPT),
        HumanMessage(content=f"请压缩以下内容：\n\n{full_content}"),
    ]

    try:
        response = await llm.ainvoke(messages)
        summary = (getattr(response, "content", "") or "").strip()
        logger.info(f"Agent {agent_id} 对话压缩完成，摘要长度: {len(summary)}")
        return summary
    except Exception as e:
        logger.error(f"Agent {agent_id} 对话压缩失败: {e}")
        # 压缩失败时，返回简单拼接
        return f"[压缩失败] {full_content[:500]}"


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
) -> bool:
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
    - 是否执行了压缩
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
        return False

    logger.info(f"Agent {agent_id} 触发压缩: estimated={estimated}, manual={manual_trigger}")

    summary = await compress_agent_conversation(
        llm=llm,
        memory=memory,
        agent_id=agent_id,
    )

    if summary:
        memory.apply_agent_compression(agent_id, summary)

    return True
